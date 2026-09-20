$ErrorActionPreference = 'Stop'

# ad-config-check

$moduleRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Resolve-Path (Join-Path $moduleRoot '..\..')
$manifest = Join-Path $repositoryRoot 'androidApp\src\main\AndroidManifest.xml'
$androidBuild = Join-Path $repositoryRoot 'androidApp\build.gradle.kts'
$sources = Get-ChildItem -LiteralPath $moduleRoot -Recurse -File |
    Where-Object { $_.Extension -in '.kt', '.kts' }

$manifestText = Get-Content -Raw -LiteralPath $manifest
$buildText = Get-Content -Raw -LiteralPath $androidBuild
$sourceText = ($sources | ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"

if ($manifestText -notmatch 'AUTOMATIC_SDK_INITIALIZATION' -or $manifestText -notmatch 'android:value="false"') {
    throw 'Yandex automatic initialization is not explicitly disabled.'
}
if ($manifestText -notmatch 'com\.google\.android\.gms\.permission\.AD_ID' -or $manifestText -notmatch 'tools:node="remove"') {
    throw 'AD_ID removal is missing.'
}
if ($buildText -notmatch 'demo-banner-yandex' -or $buildText -notmatch 'validateYandexReleaseAdUnit') {
    throw 'Debug test ID or release guard is missing.'
}
if ($sourceText -match 'Interstitial|Rewarded|AppOpen|Mediation|Sticky') {
    throw 'A prohibited advertising format or mediation surface was found.'
}
if ($sourceText -match 'Locale|TimeZone|TelephonyManager|LocationManager') {
    throw 'A prohibited local region inference surface was found.'
}

Write-Output 'Advertising privacy static contract: OK'
