import asyncio
from datetime import datetime, timezone

class AutonomousTestnetTrader:
    def __init__(self, client, settings, market_klines, universe_fn, analyze_fn, mtf_fn, smart_fn, votes_fn):
        self.client=client; self.settings=settings; self.klines=market_klines; self.universe_fn=universe_fn
        self.analyze_fn=analyze_fn; self.mtf_fn=mtf_fn; self.smart_fn=smart_fn; self.votes_fn=votes_fn
        self.running=False; self.kill_switch=False; self.task=None; self.last_scan=None; self.events=[]; self.candidates=[]

    def log(self,event,**data):
        row={"time":datetime.now(timezone.utc).isoformat(),"event":event,**data}
        self.events.append(row); self.events=self.events[-300:]; return row

    def state(self):
        return {
            "configured":self.client.configured,
            "execution_enabled":self.client.enabled,
            "autonomous_enabled":self.settings.enable_autonomous_testnet,
            "running":self.running,"kill_switch":self.kill_switch,
            "last_scan":self.last_scan,"candidates":self.candidates[-25:],"events":self.events[-80:],
            "rules":{
                "min_score":self.settings.auto_min_score,"min_mtf_confidence":self.settings.auto_min_mtf_confidence,
                "scan_seconds":self.settings.auto_scan_seconds,"scan_markets":self.settings.auto_scan_markets,
                "max_spread_pct":self.settings.auto_max_spread_pct,"risk_pct":self.settings.auto_risk_pct,
                "max_notional_pct":self.settings.auto_max_notional_pct,"leverage":self.settings.auto_leverage,
                "max_positions":self.settings.auto_max_positions
            }
        }

    async def evaluate(self,symbol):
        frames=await asyncio.gather(*[self.klines(symbol,tf,250) for tf in ("5m","15m","1h","4h")])
        analyses={tf:self.analyze_fn(df) for tf,df in zip(("5m","15m","1h","4h"),frames)}
        a=analyses["15m"]; mtf=self.mtf_fn(analyses); smart=self.smart_fn(frames[1]); votes=self.votes_fn(frames[1])
        book=await self.client.book_ticker(symbol)
        direction=a.get("decision","WAIT")
        reasons=[]
        if direction=="WAIT": reasons.append("base strategy says WAIT")
        if float(a.get("score",0))<self.settings.auto_min_score: reasons.append("score below threshold")
        if mtf.get("bias")!=direction: reasons.append("MTF bias conflict")
        if float(mtf.get("confidence",0))<self.settings.auto_min_mtf_confidence: reasons.append("MTF confidence too low")
        if smart.get("bias") not in (direction,"NEUTRAL"): reasons.append("smart-money bias conflict")
        if book["spread_pct"]>self.settings.auto_max_spread_pct: reasons.append("spread too wide")
        ensemble=next((v for v in votes if v.get("strategy")=="ensemble"),None)
        if ensemble and ensemble.get("decision") not in (direction,"WAIT"): reasons.append("ensemble conflict")
        approved=not reasons and direction in ("LONG","SHORT")
        quality=min(100, round(float(a.get("score",0))*.45 + float(mtf.get("confidence",0))*.35 + (100 if smart.get("bias")==direction else 65)*.15 + max(0,100-book["spread_pct"]*1000)*.05,1))
        return {"symbol":symbol,"approved":approved,"direction":direction,"score":a.get("score",0),"quality":quality,"mtf":mtf,"smart_bias":smart.get("bias"),"spread_pct":round(book["spread_pct"],5),"reasons":reasons,"analysis":a}

    async def size_quantity(self,analysis,balance):
        entry=float(analysis["entry"]); sl=float(analysis["stop_loss"]); stop_pct=abs(entry-sl)/entry
        risk_cash=float(balance)*self.settings.auto_risk_pct
        notional_by_risk=risk_cash/max(stop_pct,0.001)
        cap=float(balance)*self.settings.auto_max_notional_pct*self.settings.auto_leverage
        notional=min(notional_by_risk,cap)
        qty=notional/entry
        return qty,notional,risk_cash

    async def execute_candidate(self,c):
        symbol=c["symbol"]; a=c["analysis"]
        positions=await self.client.positions()
        if any(p.get("symbol")==symbol and abs(float(p.get("positionAmt",0)))>0 for p in positions):
            self.log("SKIP",symbol=symbol,reason="position already open"); return None
        if len(positions)>=self.settings.auto_max_positions:
            self.log("SKIP",symbol=symbol,reason="max testnet positions reached"); return None
        acct=await self.client.account(); balance=float(acct.get("availableBalance") or acct.get("totalWalletBalance") or 0)
        qty,notional,risk_cash=await self.size_quantity(a,balance)
        qty=await self.client.normalize_quantity(symbol,qty,a["entry"])
        side="BUY" if c["direction"]=="LONG" else "SELL"; close_side="SELL" if side=="BUY" else "BUY"
        await self.client.set_leverage(symbol,self.settings.auto_leverage)
        entry=await self.client.market_order(symbol,side,qty)
        protection={}
        try:
            protection["stop_loss"]=await self.client.conditional_close(symbol,close_side,"STOP_MARKET",a["stop_loss"])
            tp=a["take_profits"][1] if len(a.get("take_profits",[]))>1 else a["take_profits"][0]
            protection["take_profit"]=await self.client.conditional_close(symbol,close_side,"TAKE_PROFIT_MARKET",tp)
        except Exception as e:
            self.log("PROTECTION_ERROR",symbol=symbol,error=str(e))
        self.log("TESTNET_ENTRY",symbol=symbol,direction=c["direction"],quantity=qty,notional=round(notional,2),risk_cash=round(risk_cash,2),quality=c["quality"],entry_order=entry.get("orderId"))
        return {"entry":entry,"protection":protection,"quantity":qty}

    async def scan_once(self, execute=False):
        if self.kill_switch:
            return {"ok":False,"reason":"kill switch is active","state":self.state()}
        rows=(await self.universe_fn())[:self.settings.auto_scan_markets]
        sem=asyncio.Semaphore(4)
        async def one(row):
            async with sem:
                try:return await self.evaluate(row["symbol"])
                except Exception as e:return {"symbol":row["symbol"],"approved":False,"direction":"WAIT","reasons":[str(e)],"quality":0}
        vals=await asyncio.gather(*[one(r) for r in rows])
        vals=sorted(vals,key=lambda x:x.get("quality",0),reverse=True)
        self.candidates=[{k:v for k,v in x.items() if k!="analysis"} for x in vals]
        self.last_scan=datetime.now(timezone.utc).isoformat()
        executed=[]
        if execute:
            if not (self.settings.enable_autonomous_testnet and self.client.enabled):
                self.log("SCAN_ONLY",reason="autonomous/testnet execution switch is off")
            else:
                for c in vals:
                    if c.get("approved"):
                        try:
                            x=await self.execute_candidate(c)
                            if x: executed.append({"symbol":c["symbol"],"direction":c["direction"],"quality":c["quality"]})
                        except Exception as e:self.log("EXECUTION_ERROR",symbol=c["symbol"],error=str(e))
                        if len(executed)>=1: break
        self.log("SCAN",approved=sum(1 for x in vals if x.get("approved")),executed=len(executed))
        return {"ok":True,"executed":executed,"candidates":self.candidates,"state":self.state()}

    async def loop(self):
        self.running=True; self.log("AUTONOMOUS_LOOP_START")
        try:
            while not self.kill_switch and self.settings.enable_autonomous_testnet:
                await self.scan_once(execute=True)
                await asyncio.sleep(max(30,self.settings.auto_scan_seconds))
        finally:
            self.running=False; self.log("AUTONOMOUS_LOOP_STOP")

    def start(self):
        if self.task and not self.task.done(): return False
        if not self.settings.enable_autonomous_testnet: return False
        self.kill_switch=False; self.task=asyncio.create_task(self.loop()); return True

    async def kill(self,cancel_orders=True):
        self.kill_switch=True
        if self.task and not self.task.done(): self.task.cancel()
        cancelled=[]
        if cancel_orders and self.client.enabled:
            try:
                positions=await self.client.positions()
                for p in positions:
                    sym=p.get("symbol")
                    if sym:
                        try: cancelled.append({"symbol":sym,"result":await self.client.cancel_all(sym)})
                        except Exception as e: cancelled.append({"symbol":sym,"error":str(e)})
            except Exception as e:self.log("KILL_CANCEL_ERROR",error=str(e))
        self.log("KILL_SWITCH",cancel_orders=cancel_orders)
        return {"ok":True,"cancelled":cancelled,"state":self.state()}
