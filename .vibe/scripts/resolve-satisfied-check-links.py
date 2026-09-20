#!/usr/bin/env python3
"""Remove stale per-item check links while retaining unavailable platform work."""

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
    receipt = read_json(root / args.receipt)
    if (
        receipt.get("exitCode") != 0
        or receipt.get("executionStatus") != "completed"
        or not receipt_stable(receipt)
        or not fingerprint_equal(receipt.get("startWorkspaceFingerprint"), compute_workspace_fingerprint(root))
    ):
        raise ProtocolError("a current stable successful receipt is required")

    covered = {
        (row["obligationId"], surface)
        for row in receipt["coveredObligations"]
        for surface in row["surfaces"]
    }
    resolutions: list[dict[str, str]] = []

    def mutate(ledger: dict) -> None:
        for item in [*ledger["acceptanceScenarios"], *ledger["qualityGates"]]:
            retained = []
            for check in item.get("pendingChecks", []):
                local_pairs = {
                    (row["obligationId"], surface)
                    for row in check.get("coveredObligations", [])
                    if row["obligationId"] == item["id"]
                    for surface in row["surfaces"]
                }
                if not local_pairs:
                    resolutions.append({"itemId": item["id"], "checkId": check["id"], "reason": "foreign-link"})
                elif item.get("applicability") == "not-applicable":
                    resolutions.append({"itemId": item["id"], "checkId": check["id"], "reason": "not-applicable"})
                elif local_pairs <= covered:
                    resolutions.append({"itemId": item["id"], "checkId": check["id"], "reason": "covered"})
                else:
                    retained.append(check)
            item["pendingChecks"] = retained
        if not resolutions:
            raise ProtocolError("no stale or satisfied check links found")
        ledger.setdefault("workResolutions", []).append(
            {
                "at": utc_now(),
                "links": resolutions,
                "inspectionNote": (
                    "Removed only foreign, not-applicable, or receipt-covered per-item check links; "
                    "unavailable iOS/macOS pairs remain pending on their owning obligations."
                ),
                "receiptRef": args.receipt.replace("\\", "/"),
            }
        )
        ledger["updatedAt"] = utc_now()

    updated = update_ledger_atomic(ledger_path, args.expected_ledger_digest, mutate)
    print(json.dumps({"resolvedLinks": resolutions, "ledgerDigest": updated["ledgerDigest"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
