from pydantic import BaseModel
from dotenv import load_dotenv
import os

load_dotenv()

def b(name,default="false"):
    return os.getenv(name,default).strip().lower()=="true"

class Settings(BaseModel):
    mode: str = os.getenv("AETHERIS_MODE", "PAPER").strip().upper()
    starting_balance: float = float(os.getenv("STARTING_BALANCE", "10000"))
    risk_per_trade: float = float(os.getenv("RISK_PER_TRADE", "0.005"))
    max_daily_loss: float = float(os.getenv("MAX_DAILY_LOSS", "0.02"))
    max_open_positions: int = int(os.getenv("MAX_OPEN_POSITIONS", "3"))
    max_leverage: int = int(os.getenv("MAX_LEVERAGE", "3"))
    min_setup_score: float = float(os.getenv("MIN_SETUP_SCORE", "72"))
    binance_fapi_base: str = os.getenv("BINANCE_FAPI_BASE", "https://fapi.binance.com").strip()
    binance_testnet_base: str = os.getenv("BINANCE_TESTNET_BASE", "https://testnet.binancefuture.com").strip()
    binance_testnet_ws_base: str = os.getenv("BINANCE_TESTNET_WS_BASE", "wss://fstream.binancefuture.com").strip().rstrip('/')
    binance_testnet_api_key: str = os.getenv("BINANCE_TESTNET_API_KEY", "").strip()
    binance_testnet_api_secret: str = os.getenv("BINANCE_TESTNET_API_SECRET", "").strip()
    enable_testnet_execution: bool = b("ENABLE_TESTNET_EXECUTION")
    binance_recv_window: int = int(os.getenv("BINANCE_RECV_WINDOW", "10000"))
    shadow_scan_seconds: int = int(os.getenv("SHADOW_SCAN_SECONDS", "300"))
    max_correlated_exposure: float = float(os.getenv("MAX_CORRELATED_EXPOSURE", "0.82"))
    enable_autonomous_testnet: bool = b("ENABLE_AUTONOMOUS_TESTNET")
    auto_scan_seconds: int = int(os.getenv("AUTO_SCAN_SECONDS", "120"))
    auto_scan_markets: int = int(os.getenv("AUTO_SCAN_MARKETS", "12"))
    auto_min_score: float = float(os.getenv("AUTO_MIN_SCORE", "82"))
    auto_min_mtf_confidence: float = float(os.getenv("AUTO_MIN_MTF_CONFIDENCE", "72"))
    auto_max_spread_pct: float = float(os.getenv("AUTO_MAX_SPREAD_PCT", "0.08"))
    auto_risk_pct: float = float(os.getenv("AUTO_RISK_PCT", "0.0025"))
    auto_max_notional_pct: float = float(os.getenv("AUTO_MAX_NOTIONAL_PCT", "0.05"))
    auto_leverage: int = int(os.getenv("AUTO_LEVERAGE", "1"))
    auto_max_positions: int = int(os.getenv("AUTO_MAX_POSITIONS", "1"))
    enable_execution_stream: bool = b("ENABLE_EXECUTION_STREAM", "true")
    execution_reconcile_seconds: int = int(os.getenv("EXECUTION_RECONCILE_SECONDS", "30"))
    execution_keepalive_seconds: int = int(os.getenv("EXECUTION_KEEPALIVE_SECONDS", "2700"))

settings = Settings()
