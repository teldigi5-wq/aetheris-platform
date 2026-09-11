from datetime import datetime, timezone
from .risk import size_trade, risk_gate

class PaperBroker:
    def __init__(self, balance=10000):
        self.state = {"starting_balance":balance, "balance":balance, "daily_pnl":0.0, "positions":[], "journal":[]}

    def open(self, symbol, analysis, leverage=1, mtf=None):
        ok, reason = risk_gate(self.state, analysis, mtf)
        if not ok:
            self.log("REJECTED",symbol,{"reason":reason,"analysis":analysis,"mtf":mtf})
            return {"ok":False,"reason":reason}
        s = size_trade(self.state["balance"],analysis["entry"],analysis["stop_loss"],leverage,analysis["score"])
        if not s["allowed"]: return {"ok":False,"reason":s["reason"]}
        p = {"symbol":symbol,"side":analysis["decision"],"entry":analysis["entry"],"stop_loss":analysis["stop_loss"],"take_profits":analysis["take_profits"],"quantity":s["quantity"],"notional":s["notional"],"leverage":s["leverage"],"opened_at":datetime.now(timezone.utc).isoformat(),"status":"OPEN","score":analysis["score"],"tp_stage":0}
        self.state["positions"].append(p); self.log("OPEN",symbol,p)
        return {"ok":True,"position":p,"risk":s}

    def monitor(self, symbol, price):
        events=[]
        for p in list(self.state["positions"]):
            if p["symbol"] != symbol: continue
            if p["side"]=="LONG":
                if price <= p["stop_loss"]: events.append(self.close(p,price,"STOP")); continue
                if p["tp_stage"] == 0 and price >= p["take_profits"][0]: p["tp_stage"] = 1; p["stop_loss"] = p["entry"]; self.log("MANAGE",symbol,{"action":"MOVE_SL_TO_BE","price":price})
                if price >= p["take_profits"][-1]: events.append(self.close(p,price,"TP3"))
            else:
                if price >= p["stop_loss"]: events.append(self.close(p,price,"STOP")); continue
                if p["tp_stage"] == 0 and price <= p["take_profits"][0]: p["tp_stage"] = 1; p["stop_loss"] = p["entry"]; self.log("MANAGE",symbol,{"action":"MOVE_SL_TO_BE","price":price})
                if price <= p["take_profits"][-1]: events.append(self.close(p,price,"TP3"))
        return events

    def close(self,p,price,reason):
        mult = 1 if p["side"]=="LONG" else -1; pnl = (price-p["entry"])*p["quantity"]*mult
        self.state["balance"] += pnl; self.state["daily_pnl"] += pnl; self.state["positions"].remove(p)
        event={"symbol":p["symbol"],"exit":price,"reason":reason,"pnl":round(pnl,2)}; self.log("CLOSE",p["symbol"],event); return event

    def log(self,event,symbol,data):
        self.state["journal"].append({"time":datetime.now(timezone.utc).isoformat(),"event":event,"symbol":symbol,"data":data}); self.state["journal"]=self.state["journal"][-500:]
