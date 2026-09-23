"""Refresh only brand-related metadata and hashes of reviewed changed artifacts."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]

def ref(path):
    return {"path": path, "sha256": hashlib.sha256((ROOT / path).read_bytes()).hexdigest()}

manifest_path = ROOT / "docs/assets/asset-manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
platform = manifest["platformDerivatives"]
platform["provenance"] = {
    "method": "deterministic-raster-export",
    "source": ref("docs/assets/source/logo-1b-1-transparent.png"),
    "approvedReference": ref("docs/assets/source/logo-1b-1-approved.png"),
    "exportScript": ref("docs/assets/generation/render-platform-icons.py"),
    "generationRecord": ref("docs/assets/generation/logo-1b-1.json"),
    "rights": "Original generated logo selected by the user; deterministic native size/alpha exports."
}
asset = next(a for a in manifest["assets"] if a["id"] == "ASSET-001")
asset["outputs"] = [ref("shared/compose/src/commonMain/composeResources/drawable/logo_sproutpace.png")]
asset["provenance"] = {
    "method": "generate-raster", "source": "User-approved original ImageGen logo 1B-1",
    "generationRecord": ref("docs/assets/generation/logo-1b-1.json"),
    "rights": "Original generated artwork for SproutPace; no independent legal clearance asserted."
}
asset["visualEvidence"] = [e for e in asset["visualEvidence"] if e["path"].endswith(".png")]
asset["visualEvidence"].append(ref("docs/assets/brand-refresh-review.md"))

def refresh(value):
    if isinstance(value, dict):
        if "path" in value and "sha256" in value:
            value["sha256"] = ref(value["path"])["sha256"]
        for child in value.values():
            refresh(child)
    elif isinstance(value, list):
        for child in value:
            refresh(child)

refresh(manifest)
manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")
