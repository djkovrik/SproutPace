$ErrorActionPreference = "Stop"

$contract = Get-Content -Raw -LiteralPath "docs/design-review/product-ui-material-contract.md"
$report = Get-Content -Raw -LiteralPath ".lazyweb/design-improve/sproutpace-final-2026-09-20/report.html"

$requiredContractMarkers = @(
    "638f6950-0f2c-40f2-b379-af92e1700f23",
    "695cb246-c2bb-43d7-b499-c1eca8617a57"
)

foreach ($marker in $requiredContractMarkers) {
    if (-not $contract.Contains($marker)) {
        throw "Missing design-review contract marker: $marker"
    }
}

foreach ($marker in @("SproutPace", "Accepted", "Not applied")) {
    if (-not $report.Contains($marker)) {
        throw "Missing final Lazyweb review marker: $marker"
    }
}

Write-Output "Final post-golden Lazyweb review evidence is complete."
