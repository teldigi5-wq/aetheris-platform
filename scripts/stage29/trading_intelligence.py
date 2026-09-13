#!/usr/bin/env python3
"""Stage 29 deterministic trading intelligence planner.

This module intentionally performs no exchange/network I/O and never submits orders.
It transforms a candidate setup plus policy into an auditable proposal or rejection.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from decimal import Decimal, ROUND_DOWN, getcontext
from pathlib import Path
from typing import Any

getcontext().prec = 28


class PolicyError(ValueError):
    pass


D = Decimal


def _d(value: Any, name: str) -> Decimal:
    try:
        return D(str(value))
    except Exception as exc:  # pragma: no cover - defensive
        raise PolicyError(f"invalid decimal for {name}") from exc


def _quant(value: Decimal, places: str = "0.00000001") -> str:
    return format(value.quantize(D(places), rounding=ROUND_DOWN), "f")


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    if not isinstance(data, dict):
        raise PolicyError("top-level JSON must be an object")
    return data


def validate_policy(policy: dict[str, Any]) -> None:
    required = {
        "schemaVersion",
        "stage",
        "defaultMode",
        "allowedModes",
        "liveExecutionEnabled",
        "testnetExecutionEnabled",
        "minSetupScore",
        "maxRiskPerTrade",
        "maxDailyLoss",
        "maxOpenPositions",
        "maxLeverage",
        "minRewardRiskRatio",
        "maxStopDistancePct",
        "requireExplicitApproval",
        "networkRequiredForCertification",
        "secretsRequiredForCertification",
    }
    missing = sorted(required - set(policy))
    if missing:
        raise PolicyError(f"missing policy fields: {', '.join(missing)}")

    if policy["stage"] != 29:
        raise PolicyError("policy stage must equal 29")
    if policy["defaultMode"] != "PAPER":
        raise PolicyError("default mode must be PAPER")
    if "LIVE" in policy["allowedModes"]:
        raise PolicyError("LIVE must not be in allowedModes")
    if policy["liveExecutionEnabled"] is not False:
        raise PolicyError("liveExecutionEnabled must remain false")
    if policy["requireExplicitApproval"] is not True:
        raise PolicyError("explicit approval boundary must remain enabled")
    if policy["networkRequiredForCertification"] is not False:
        raise PolicyError("certification must not require network access")
    if policy["secretsRequiredForCertification"] is not False:
        raise PolicyError("certification must not require secrets")

    if not (D("0") < _d(policy["maxRiskPerTrade"], "maxRiskPerTrade") <= D("0.02")):
        raise PolicyError("maxRiskPerTrade outside safe Stage 29 bound")
    if not (D("0") < _d(policy["maxDailyLoss"], "maxDailyLoss") <= D("0.10")):
        raise PolicyError("maxDailyLoss outside safe Stage 29 bound")
    if not (1 <= int(policy["maxOpenPositions"]) <= 10):
        raise PolicyError("maxOpenPositions outside safe Stage 29 bound")
    if not (1 <= int(policy["maxLeverage"]) <= 5):
        raise PolicyError("maxLeverage outside safe Stage 29 bound")
    if not (0 <= int(policy["minSetupScore"]) <= 100):
        raise PolicyError("minSetupScore must be 0..100")
    if _d(policy["minRewardRiskRatio"], "minRewardRiskRatio") < D("1"):
        raise PolicyError("minRewardRiskRatio must be >= 1")
    if not (D("0") < _d(policy["maxStopDistancePct"], "maxStopDistancePct") <= D("0.10")):
        raise PolicyError("maxStopDistancePct outside safe Stage 29 bound")


def _validate_candidate(candidate: dict[str, Any]) -> None:
    required = {
        "symbol",
        "direction",
        "entryPrice",
        "stopLossPrice",
        "takeProfitPrice",
        "setupScore",
        "accountBalance",
        "riskPerTrade",
        "requestedLeverage",
        "openPositions",
        "dailyLossFraction",
        "mode",
    }
    missing = sorted(required - set(candidate))
    if missing:
        raise PolicyError(f"missing candidate fields: {', '.join(missing)}")
    if candidate["direction"] not in {"LONG", "SHORT"}:
        raise PolicyError("direction must be LONG or SHORT")


def plan(policy: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
    validate_policy(policy)
    _validate_candidate(candidate)

    reasons: list[str] = []
    mode = str(candidate["mode"]).upper()
    direction = candidate["direction"]
    entry = _d(candidate["entryPrice"], "entryPrice")
    stop = _d(candidate["stopLossPrice"], "stopLossPrice")
    target = _d(candidate["takeProfitPrice"], "takeProfitPrice")
    balance = _d(candidate["accountBalance"], "accountBalance")
    requested_risk = _d(candidate["riskPerTrade"], "riskPerTrade")
    daily_loss = _d(candidate["dailyLossFraction"], "dailyLossFraction")
    requested_leverage = int(candidate["requestedLeverage"])
    open_positions = int(candidate["openPositions"])
    score = int(candidate["setupScore"])

    if entry <= 0 or stop <= 0 or target <= 0 or balance <= 0:
        reasons.append("NON_POSITIVE_PRICE_OR_BALANCE")

    if mode == "LIVE":
        reasons.append("LIVE_EXECUTION_BLOCKED_STAGE_29")
    elif mode not in policy["allowedModes"]:
        reasons.append("MODE_NOT_ALLOWED")
    elif mode == "TESTNET" and policy["testnetExecutionEnabled"] is not True:
        reasons.append("TESTNET_EXECUTION_DISABLED")

    if score < int(policy["minSetupScore"]):
        reasons.append("SETUP_SCORE_BELOW_MINIMUM")
    if requested_risk <= 0 or requested_risk > _d(policy["maxRiskPerTrade"], "maxRiskPerTrade"):
        reasons.append("RISK_PER_TRADE_EXCEEDS_POLICY")
    if daily_loss >= _d(policy["maxDailyLoss"], "maxDailyLoss"):
        reasons.append("DAILY_LOSS_LOCKOUT")
    if open_positions >= int(policy["maxOpenPositions"]):
        reasons.append("OPEN_POSITION_LOCKOUT")
    if requested_leverage < 1:
        reasons.append("INVALID_LEVERAGE")

    geometry_valid = False
    risk_distance = D("0")
    reward_distance = D("0")
    if entry > 0:
        if direction == "LONG":
            geometry_valid = stop < entry < target
            risk_distance = entry - stop if stop < entry else D("0")
            reward_distance = target - entry if target > entry else D("0")
        else:
            geometry_valid = target < entry < stop
            risk_distance = stop - entry if stop > entry else D("0")
            reward_distance = entry - target if target < entry else D("0")

    if not geometry_valid:
        reasons.append("INVALID_TP_SL_GEOMETRY")

    stop_pct = (risk_distance / entry) if geometry_valid and entry > 0 else D("0")
    rr = (reward_distance / risk_distance) if geometry_valid and risk_distance > 0 else D("0")

    if geometry_valid and stop_pct > _d(policy["maxStopDistancePct"], "maxStopDistancePct"):
        reasons.append("STOP_DISTANCE_EXCEEDS_POLICY")
    if geometry_valid and rr < _d(policy["minRewardRiskRatio"], "minRewardRiskRatio"):
        reasons.append("REWARD_RISK_BELOW_MINIMUM")

    capped_leverage = min(max(requested_leverage, 1), int(policy["maxLeverage"]))
    risk_capital = balance * requested_risk if requested_risk > 0 else D("0")
    max_notional_by_stop = (risk_capital / stop_pct) if stop_pct > 0 else D("0")
    max_notional_by_leverage = balance * D(capped_leverage)
    proposed_notional = min(max_notional_by_stop, max_notional_by_leverage)
    if reasons:
        proposed_notional = D("0")

    status = "PROPOSAL_READY" if not reasons else "REJECTED"

    return {
        "schemaVersion": 1,
        "stage": 29,
        "symbol": str(candidate["symbol"]).upper(),
        "direction": direction,
        "mode": mode,
        "status": status,
        "executionPermitted": False,
        "requiresExplicitApproval": True,
        "orderSubmitted": False,
        "networkUsed": False,
        "secretsUsed": False,
        "setupScore": score,
        "entryPrice": _quant(entry),
        "stopLossPrice": _quant(stop),
        "takeProfitPrice": _quant(target),
        "stopDistancePct": _quant(stop_pct),
        "rewardRiskRatio": _quant(rr),
        "riskCapital": _quant(risk_capital),
        "requestedLeverage": requested_leverage,
        "cappedLeverage": capped_leverage,
        "proposedNotional": _quant(proposed_notional),
        "rejectionReasons": sorted(set(reasons)),
    }


def canonical_json(data: dict[str, Any]) -> str:
    return json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=True) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    try:
        result = plan(load_json(args.policy), load_json(args.candidate))
    except (PolicyError, ValueError, TypeError) as exc:
        raise SystemExit(f"stage29 policy error: {exc}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(canonical_json(result), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
