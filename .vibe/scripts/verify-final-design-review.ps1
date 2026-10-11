$ErrorActionPreference = 'Stop'

$coverage = Get-Content -Raw -LiteralPath 'docs/design-review/ux-preview-coverage.json' | ConvertFrom-Json
$review = Get-Content -Raw -LiteralPath 'docs/design-review/ux-preview-review.json' | ConvertFrom-Json
$goldenRoot = 'shared/compose/src/androidUnitTest/snapshots/images'
$goldens = @(Get-ChildItem -LiteralPath $goldenRoot -Filter '*.png')
if ($review.status -ne 'VISUALLY_REVIEWED') { throw 'Current visual inspection is incomplete' }
if ($coverage.declarationCount -ne $goldens.Count -or $review.images.Count -ne $goldens.Count) {
    throw 'Preview declarations, generated images and reviewed images differ'
}
if (@($coverage.screens).Count -ne 16) { throw 'All 16 screen matrices are required' }
foreach ($screen in $coverage.screens) {
    if (@($screen.missingDedicatedScenarioPreviews).Count -ne 0) {
        throw "Missing scenario coverage: $($screen.screen)"
    }
}
$original = @($goldens | Where-Object { $_.Name -match '_sproutpacepreviewskt\.' })
if ($original.Count -ne 78) { throw 'The original 78 production preview cases must remain present' }
foreach ($golden in $goldens) {
    $entries = @($review.images | Where-Object { $_.file -ceq $golden.Name })
    if ($entries.Count -ne 1) { throw "Missing or duplicate review: $($golden.Name)" }
    $hash = (Get-FileHash -LiteralPath $golden.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -cne $entries[0].sha256) { throw "Image changed after review: $($golden.Name)" }
}
Write-Output "Visual review hashes match all $($goldens.Count) production images across 16 screens. Runtime and platform checks are separate."
