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

settings = Settings()
