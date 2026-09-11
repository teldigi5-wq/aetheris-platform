# Aetheris Quant v0.3 — Market Universe + Pattern Intelligence

Paper-trading-first Binance USDⓈ-M Futures intelligence system.

## v0.3 highlights

- Browse **every currently tradable USDT perpetual contract** returned by Binance Futures
- Search the complete market universe
- Sort by volume, gainers, losers, or symbol
- Paginated scanner so the dashboard does not hammer the Binance API
- Every scanned coin shows its strongest detected candlestick pattern
- Expanded pattern engine: engulfing, doji, pin bars, inside/outside bars, morning/evening star, three soldiers/crows
- Structure recognition: double-top/bottom candidates, volatility compression/range, symmetrical-triangle candidate
- Multi-timeframe 5m / 15m / 1h / 4h analysis
- EMA 9/20/50/200, RSI, MACD, Bollinger Bands, VWAP, ATR, ADX
- Support/resistance and breakout/breakdown detection
- Funding rate, mark price, open interest
- Explainable LONG / SHORT / WAIT scoring
- Paper execution with hard risk gates
- Real-money execution remains disabled

## Run on Windows PowerShell

```powershell
cd "D:\aetheris-quant-v0.3"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
python -m uvicorn app.main:app --reload
```

Open `http://127.0.0.1:8000`.

## Safety

Aetheris Quant cannot identify guaranteed or zero-risk futures trades. Forecasts and pattern labels are analytical estimates. Keep the system in PAPER/testnet mode until it has been independently backtested and validated.
