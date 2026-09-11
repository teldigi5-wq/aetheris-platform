# Aetheris Quant v0.4 — Strategy Arena + Backtesting

Paper-trading-first Binance USDⓈ-M Futures intelligence system.

## v0.4 highlights

- Everything from v0.3: full USDT perpetual universe, pattern scanner, MTF analysis, EMA/RSI/MACD/Bollinger/VWAP/ATR/ADX, support/resistance, funding/OI and explainable signals
- **Strategy Arena**: Trend, Momentum, Mean Reversion, Breakout and Ensemble strategies analyzed independently
- **Historical backtesting API** with fees and risk-based sizing
- Performance metrics: return, win rate, profit factor, expectancy, max drawdown, Sharpe-like score and equity curve
- Strategy ranking per symbol/timeframe
- Smarter paper position management: partial TP1/TP2 exits, break-even protection and trailing stop logic
- Dashboard strategy cards + arena table + backtest equity chart
- Pattern markers shown directly on the chart for the latest detected setup
- Real-money execution remains disabled

## Update on Windows

```powershell
cd "D:\aetheris-platform"
git pull origin main
cd aetheris-quant
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

Open `http://127.0.0.1:8000`.

## Safety

Backtests are simulations and can overstate future performance. They do not prove profitability. Keep PAPER/testnet mode until strategies are validated across out-of-sample periods, different regimes, fees, slippage and failure cases.
