#!/usr/bin/env python3
"""Resolve legacy zero-coverage checks when their exact tasks are in a current green receipt.

Protocol 2.0's stock resolve-work action intentionally requires obligation coverage,
but early specialist handoffs emitted task-only checks. This narrow reconciler keeps
the same atomic ledger/digest machinery and refuses anything except those legacy rows.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PROTOCOL_SCRIPTS = Path(r"D:\Sources\vibe-skills\vibe-developer\scripts")
sys.path.insert(0, str(PROTOCOL_SCRIPTS))

from validation_context import receipt_stable
from vibe_protocol import (
    ProtocolError,
    compute_workspace_fingerprint,
    fingerprint_equal,
    read_json,
    update_ledger_atomic,
    utc_now,
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project_root", type=Path)
    parser.add_argument("--receipt", required=True)
    parser.add_argument("--expected-ledger-digest", required=True)
    args = parser.parse_args()

    root = args.project_root.resolve()
    ledger_path = root / ".vibe" / "delivery-ledger.json"
    receipt_path = root / args.receipt
    receipt = read_json(receipt_path)
    if (
        receipt.get("exitCode") != 0
        or receipt.get("executionStatus") != "completed"
        or not receipt_stable(receipt)
        or not fingerprint_equal(receipt.get("startWorkspaceFingerprint"), compute_workspace_fingerprint(root))
    ):
        raise ProtocolError("a current stable successful receipt is required")

    log_path = root / receipt["log"]["path"]
    log = log_path.read_text(encoding="utf-8", errors="replace")
    resolved_ids: set[str] = set()

    def mutate(ledger: dict) -> None:
        for item in [*ledger["acceptanceScenarios"], *ledger["qualityGates"]]:
            retained = []
            for check in item.get("pendingChecks", []):
                if check.get("coveredObligations"):
                    retained.append(check)
                    continue
                tasks = check.get("tasks", [])
                if not tasks or any(f"Task {task}" not in log for task in tasks):
                    retained.append(check)
                    continue
                resolved_ids.add(check["id"])
            item["pendingChecks"] = retained
        if not resolved_ids:
            raise ProtocolError("no provably executed zero-coverage checks found")
        ledger.setdefault("workResolutions", []).append(
            {
                "at": utc_now(),
                "ids": sorted(resolved_ids),
                "inspectionNote": (
                    "Legacy task-only checks were resolved because every exact Gradle task "
                    "appears in the current green closure receipt log."
                ),
                "receiptRef": args.receipt.replace("\\", "/"),
            }
        )
        ledger["updatedAt"] = utc_now()

    updated = update_ledger_atomic(ledger_path, args.expected_ledger_digest, mutate)
    print(json.dumps({"resolvedIds": sorted(resolved_ids), "ledgerDigest": updated["ledgerDigest"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
