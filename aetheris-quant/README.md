# Aetheris Quant v0.6 — Binance Futures Testnet Execution

Paper/testnet-first automated futures intelligence platform.

## v0.6 highlights
- Everything from v0.5: market universe, patterns, MTF analysis, Strategy Arena, backtesting, smart-money/liquidity intelligence, portfolio correlation guard, shadow trader
- Authenticated Binance USDⓈ-M Futures testnet REST client
- Server-clock synchronization and signed HMAC requests
- Testnet account / position / open-order visibility
- Testnet leverage setting
- MARKET entry orders on testnet
- Exchange-side STOP_MARKET and TAKE_PROFIT_MARKET close orders via Binance Algo conditional-order service
- Duplicate-safe client order IDs
- Cancel-all emergency endpoint
- Hard local execution lock: no order can be placed unless `ENABLE_TESTNET_EXECUTION=true`
- Production/live base URL is never used for authenticated execution in this stage

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

## Configure testnet
Keep credentials only in local `.env` and never commit them.

```env
BINANCE_TESTNET_BASE=https://testnet.binancefuture.com
BINANCE_TESTNET_API_KEY=YOUR_TESTNET_KEY
BINANCE_TESTNET_API_SECRET=YOUR_TESTNET_SECRET
ENABLE_TESTNET_EXECUTION=false
```

Start with `false`. Verify `/api/testnet/status`. Only after the connection is authenticated should you deliberately change it to `true` for testnet order testing.

## Safety
This release deliberately does not support live-money authenticated execution. Futures remain risky even on a technically correct system. Testnet fills, liquidity, slippage and outages do not perfectly reproduce production behavior.
