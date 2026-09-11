# Aetheris Quant v0.5 — Liquidity Intelligence + Shadow Trader

Paper-trading-first Binance USDⓈ-M Futures research and automation platform.

## v0.5 additions

- Liquidity pool detection from repeated swing highs/lows
- Buy-side and sell-side liquidity sweep detection
- BOS (Break of Structure) and CHoCH approximations
- Fair Value Gap (FVG) detection
- Order Block candidates
- Premium / discount range positioning
- Smart-money directional bias score
- Automatic **Shadow Trader** that records qualified hypothetical trades without risking the paper account
- Shadow win rate and average R tracking
- Portfolio correlation guard before opening same-direction paper positions
- Portfolio intelligence endpoint with gross notional and pairwise return correlation
- Existing full futures universe scanner, pattern engine, Strategy Arena, backtests and smart partial-exit manager retained
- Real-money execution remains disabled

## Update an existing clone

```powershell
cd "D:\aetheris-platform"
git pull origin main
cd aetheris-quant
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

Open `http://127.0.0.1:8000`.

## Important

Smart-money labels are algorithmic approximations, not proof of institutional activity. Shadow results and backtests do not guarantee future returns. Keep PAPER/testnet mode enabled while validating the system.
