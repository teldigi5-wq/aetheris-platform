import hashlib, hmac, time, uuid
from urllib.parse import urlencode
import httpx

class BinanceTestnetClient:
    def __init__(self, api_key:str, api_secret:str, base_url:str, enabled:bool=False, recv_window:int=5000):
        self.api_key=api_key.strip(); self.api_secret=api_secret.strip(); self.base_url=base_url.rstrip('/')
        self.enabled=bool(enabled); self.recv_window=int(recv_window); self.time_offset_ms=0

    @property
    def configured(self):
        return bool(self.api_key and self.api_secret)

    async def sync_time(self):
        async with httpx.AsyncClient(timeout=10) as c:
            r=await c.get(f"{self.base_url}/fapi/v1/time"); r.raise_for_status()
            server=int(r.json()["serverTime"])
        local=int(time.time()*1000); self.time_offset_ms=server-local
        return {"server_time":server,"local_time":local,"offset_ms":self.time_offset_ms}

    def _signed_params(self, params=None):
        if not self.configured: raise RuntimeError("Binance testnet API credentials are not configured.")
        p=dict(params or {})
        p.setdefault("recvWindow", self.recv_window)
        p["timestamp"]=int(time.time()*1000)+self.time_offset_ms
        query=urlencode(p, doseq=True)
        p["signature"]=hmac.new(self.api_secret.encode(), query.encode(), hashlib.sha256).hexdigest()
        return p

    async def _signed(self, method, path, params=None):
        p=self._signed_params(params)
        headers={"X-MBX-APIKEY":self.api_key}
        async with httpx.AsyncClient(timeout=15) as c:
            r=await c.request(method, f"{self.base_url}{path}", params=p, headers=headers)
            if r.status_code>=400:
                try: detail=r.json()
                except Exception: detail={"msg":r.text}
                raise RuntimeError(f"Binance {r.status_code}: {detail}")
            return r.json()

    async def status(self):
        out={"configured":self.configured,"execution_enabled":self.enabled,"base_url":self.base_url,"time_offset_ms":self.time_offset_ms}
        try:
            out["time"]=await self.sync_time(); out["reachable"]=True
        except Exception as e:
            out["reachable"]=False; out["error"]=str(e); return out
        if self.configured:
            try:
                acct=await self._signed("GET","/fapi/v2/account")
                out["authenticated"]=True
                out["account"]={"totalWalletBalance":acct.get("totalWalletBalance"),"availableBalance":acct.get("availableBalance"),"totalUnrealizedProfit":acct.get("totalUnrealizedProfit")}
            except Exception as e:
                out["authenticated"]=False; out["auth_error"]=str(e)
        else: out["authenticated"]=False
        return out

    async def account(self): return await self._signed("GET","/fapi/v2/account")
    async def open_orders(self, symbol=None): return await self._signed("GET","/fapi/v1/openOrders", {"symbol":symbol} if symbol else {})
    async def positions(self):
        acct=await self.account()
        return [p for p in acct.get("positions",[]) if abs(float(p.get("positionAmt",0)))>0]

    def _require_enabled(self):
        if not self.enabled: raise RuntimeError("Testnet execution is locked. Set ENABLE_TESTNET_EXECUTION=true locally to allow order placement.")

    async def set_leverage(self, symbol, leverage):
        self._require_enabled(); leverage=max(1,min(int(leverage),20))
        return await self._signed("POST","/fapi/v1/leverage",{"symbol":symbol.upper(),"leverage":leverage})

    async def market_order(self, symbol, side, quantity, reduce_only=False, client_id=None):
        self._require_enabled(); side=side.upper()
        if side not in ("BUY","SELL"): raise ValueError("side must be BUY or SELL")
        params={"symbol":symbol.upper(),"side":side,"type":"MARKET","quantity":str(quantity),"newClientOrderId":client_id or f"aetheris_{uuid.uuid4().hex[:20]}"}
        if reduce_only: params["reduceOnly"]="true"
        return await self._signed("POST","/fapi/v1/order",params)

    async def conditional_close(self, symbol, side, order_type, trigger_price, client_id=None):
        self._require_enabled(); order_type=order_type.upper()
        if order_type not in ("STOP_MARKET","TAKE_PROFIT_MARKET"): raise ValueError("unsupported conditional close type")
        params={"algoType":"CONDITIONAL","symbol":symbol.upper(),"side":side.upper(),"type":order_type,"triggerPrice":str(trigger_price),"closePosition":"true","workingType":"MARK_PRICE","clientAlgoId":client_id or f"aeth_{uuid.uuid4().hex[:24]}"}
        return await self._signed("POST","/fapi/v1/algoOrder",params)

    async def cancel_all(self, symbol):
        self._require_enabled()
        regular=await self._signed("DELETE","/fapi/v1/allOpenOrders",{"symbol":symbol.upper()})
        algo=None
        try: algo=await self._signed("DELETE","/fapi/v1/algoOpenOrders",{"symbol":symbol.upper()})
        except Exception as e: algo={"warning":str(e)}
        return {"regular":regular,"algo":algo}
