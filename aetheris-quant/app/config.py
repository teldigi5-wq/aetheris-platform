from pydantic import BaseModel
from dotenv import load_dotenv
import os

load_dotenv()

class Settings(BaseModel):
    mode: str = os.getenv("AETHERIS_MODE", "PAPER").upper()
    starting_balance: float = float(os.getenv("STARTING_BALANCE", "10000"))
    risk_per_trade: float = float(os.getenv("RISK_PER_TRADE", "0.005"))
    max_daily_loss: float = float(os.getenv("MAX_DAILY_LOSS", "0.02"))
    max_open_positions: int = int(os.getenv("MAX_OPEN_POSITIONS", "3"))
    max_leverage: int = int(os.getenv("MAX_LEVERAGE", "3"))
    min_setup_score: float = float(os.getenv("MIN_SETUP_SCORE", "72"))
    binance_fapi_base: str = os.getenv("BINANCE_FAPI_BASE", "https://fapi.binance.com")
    binance_testnet_base: str = os.getenv("BINANCE_TESTNET_BASE", "https://testnet.binancefuture.com")
    binance_testnet_api_key: str = os.getenv("BINANCE_TESTNET_API_KEY", "")
    binance_testnet_api_secret: str = os.getenv("BINANCE_TESTNET_API_SECRET", "")
    enable_testnet_execution: bool = os.getenv("ENABLE_TESTNET_EXECUTION", "false").lower() == "true"
    binance_recv_window: int = int(os.getenv("BINANCE_RECV_WINDOW", "5000"))
    shadow_scan_seconds: int = int(os.getenv("SHADOW_SCAN_SECONDS", "300"))
    max_correlated_exposure: float = float(os.getenv("MAX_CORRELATED_EXPOSURE", "0.82"))

settings = Settings()
