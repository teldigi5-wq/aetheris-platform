from pathlib import Path
import asyncio
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from .market import klines, futures_metrics, futures_universe
from .strategy import analyze, multi_timeframe
from .paper import PaperBroker
from .config import settings
from .backtest import run_backtest, strategy_arena, strategy_votes

app=FastAPI(title="Aetheris Quant",version="0.4")
BASE=Path(__file__).parent
app.mount("/static",StaticFiles(directory=BASE/"static"),name="static")
broker=PaperBroker(settings.starting_balance)
TIMEFRAMES=["5m","15m","1h","4h"]
SCAN_SEMAPHORE = asyncio.Semaphore(8)

@app.get("/")
async def root():
    return FileResponse(BASE/"static"/"index.html")

async def build_mtf(symbol):
    frames = await asyncio.gather(*[klines(symbol,tf,250) for tf in TIMEFRAMES])
    analyses = {tf:analyze(df) for tf,df in zip(TIMEFRAMES,frames)}
    return analyses, multi_timeframe(analyses)

@app.get("/api/analysis/{symbol}")
async def analysis(symbol:str, interval:str="15m"):
    try:
        symbol=symbol.upper()
        df_task=klines(symbol,interval,250)
        mtf_task=build_mtf(symbol)
        metrics_task=futures_metrics(symbol)
        df,(analyses,mtf),metrics=await asyncio.gather(df_task,mtf_task,metrics_task)
        a=analyze(df)
        a["symbol"]=symbol
        a["interval"]=interval
        a["multi_timeframe"]=mtf
        a["futures_metrics"]=metrics
        a["strategy_votes"]=strategy_votes(df)
        a["candles"]=[{
            "time":int(r.open_time),"open":float(r.open),"high":float(r.high),
            "low":float(r.low),"close":float(r.close),"volume":float(r.volume)
        } for _,r in df.tail(180).iterrows()]
        from .indicators import ema
        e20=ema(df["close"],20); e50=ema(df["close"],50); e200=ema(df["close"],200)
        start=max(0,len(df)-180)
        a["ema_series"]={
            "ema20":[{"time":int(df.iloc[i].open_time),"value":float(e20.iloc[i])} for i in range(start,len(df))],
            "ema50":[{"time":int(df.iloc[i].open_time),"value":float(e50.iloc[i])} for i in range(start,len(df))],
            "ema200":[{"time":int(df.iloc[i].open_time),"value":float(e200.iloc[i])} for i in range(start,len(df))]
        }
        broker.monitor(symbol,a["price"])
        return a
    except Exception as e:
        raise HTTPException(502,str(e))

@app.get("/api/universe")
async def universe(q: str = ""):
    rows=await futures_universe()
    q=q.strip().upper()
    if q:
        rows=[r for r in rows if q in r["symbol"]]
    return {"count":len(rows),"coins":rows}

@app.get("/api/scanner")
async def scanner(q: str = "", offset: int = Query(0, ge=0), limit: int = Query(20, ge=5, le=40), sort: str = "volume"):
    universe=await futures_universe()
    q=q.strip().upper()
    if q:
        universe=[r for r in universe if q in r["symbol"]]
    if sort == "gainers":
        universe=sorted(universe,key=lambda x:x["change_pct"],reverse=True)
    elif sort == "losers":
        universe=sorted(universe,key=lambda x:x["change_pct"])
    elif sort == "symbol":
        universe=sorted(universe,key=lambda x:x["symbol"])
    total=len(universe)
    page=universe[offset:offset+limit]

    async def one(base):
        async with SCAN_SEMAPHORE:
            try:
                df=await klines(base["symbol"],"15m",220)
                a=analyze(df)
                strongest=max(a.get("patterns",[]), key=lambda p:p.get("strength",0), default=None)
                return {**base,
                    "decision":a["decision"],"score":a["score"],"rsi":a["rsi"],"adx":a["adx"],
                    "regime":a["market_regime"],"probabilities":a["probabilities"],
                    "pattern": strongest["name"] if strongest else "No strong pattern",
                    "pattern_bias": strongest["bias"] if strongest else "NEUTRAL",
                    "structure": a.get("structure_pattern") or a.get("breakout") or "—"
                }
            except Exception as e:
                return {**base,"error":str(e),"pattern":"Unavailable","pattern_bias":"NEUTRAL"}
    out=await asyncio.gather(*[one(r) for r in page])
    return {"total":total,"offset":offset,"limit":limit,"coins":out}

@app.post("/api/paper/open/{symbol}")
async def paper_open(symbol:str, leverage:int=1):
    symbol=symbol.upper()
    analyses,mtf=await build_mtf(symbol)
    a=analyses["15m"]
    return broker.open(symbol,a,leverage,mtf)

@app.get("/api/backtest/{symbol}")
async def backtest(symbol:str, interval:str="15m", strategy:str="ensemble", limit:int=1200):
    try:
        symbol=symbol.upper()
        limit=max(300,min(limit,1500))
        df=await klines(symbol,interval,limit)
        result=run_backtest(df,strategy)
        result.update({"symbol":symbol,"interval":interval})
        return result
    except Exception as e:
        raise HTTPException(502,str(e))

@app.get("/api/arena/{symbol}")
async def arena(symbol:str, interval:str="15m", limit:int=1200):
    try:
        symbol=symbol.upper()
        df=await klines(symbol,interval,max(300,min(limit,1500)))
        return {"symbol":symbol,"interval":interval,"strategies":strategy_arena(df)}
    except Exception as e:
        raise HTTPException(502,str(e))

@app.get("/api/account")
async def account():
    return {"mode":settings.mode,**broker.state,
            "risk_rules":{"risk_per_trade_pct":settings.risk_per_trade*100,
            "max_daily_loss_pct":settings.max_daily_loss*100,
            "max_open_positions":settings.max_open_positions,
            "max_leverage":settings.max_leverage,
            "min_setup_score":settings.min_setup_score}}

@app.get("/api/journal")
async def journal():
    return broker.state["journal"][-100:]

@app.get("/api/health")
def health():
    return {"ok":True,"version":"0.4","mode":settings.mode,"live_trading_enabled":False}
