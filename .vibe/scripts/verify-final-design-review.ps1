$ErrorActionPreference = "Stop"

$contract = Get-Content -Raw -LiteralPath "docs/design-review/production-ui-redesign-20260927.md"
$goldens = @(Get-ChildItem -LiteralPath "shared/compose/src/androidUnitTest/snapshots/images" -Filter "*.png")

$requiredContractMarkers = @(
    "0d8da7b5-4c89-4516-99bb-ce0e2b7d76ba",
    "Blocking findings: none",
    "all 78 tracked Paparazzi goldens",
    "run-android-ui-smoke.ps1",
    "macOS Simulator execution"
)

foreach ($marker in $requiredContractMarkers) {
    if (-not $contract.Contains($marker)) {
        throw "Missing design-review contract marker: $marker"
    }
}

1..16 | ForEach-Object {
    $screenId = "SCREEN-{0:D3}" -f $_
    if (-not $contract.Contains($screenId)) {
        throw "Missing screen review marker: $screenId"
    }
}

if ($goldens.Count -ne 78) {
    throw "Expected 78 reviewed Paparazzi goldens, found $($goldens.Count)"
}

Write-Output "Current production UI review evidence is complete for all 16 screens and 78 goldens."
