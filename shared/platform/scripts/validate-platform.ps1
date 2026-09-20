$ErrorActionPreference = 'Stop'

$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$englishPath = Join-Path $repository 'shared\platform\src\androidMain\res\values\strings.xml'
$russianPath = Join-Path $repository 'shared\platform\src\androidMain\res\values-ru\strings.xml'
$iosPath = Join-Path $repository 'shared\platform\src\iosMain\resources\Localizable.xcstrings'
$manifestPath = Join-Path $repository 'androidApp\src\main\AndroidManifest.xml'
$androidBuildPath = Join-Path $repository 'androidApp\build.gradle.kts'
$xcodeProjectPath = Join-Path $repository 'iosApp\iosApp.xcodeproj\project.pbxproj'
$firebaseGuidePath = Join-Path $repository 'docs\configuration\FIREBASE-CRASHLYTICS.md'
$androidTemplatePath = Join-Path $repository 'docs\configuration\firebase\google-services.json.example'
$iosTemplatePath = Join-Path $repository 'docs\configuration\firebase\GoogleService-Info.plist.example'

function Read-AndroidKeys([string] $path) {
    [xml] $xml = Get-Content -Raw -LiteralPath $path
    return @($xml.resources.string | ForEach-Object { $_.name } | Sort-Object)
}

$englishKeys = Read-AndroidKeys $englishPath
$russianKeys = Read-AndroidKeys $russianPath
$iosCatalog = Get-Content -Raw -LiteralPath $iosPath | ConvertFrom-Json
$iosKeys = @($iosCatalog.strings.PSObject.Properties.Name | Sort-Object)

if (Compare-Object $englishKeys $russianKeys) { throw 'Android EN/RU native key sets differ.' }
if (Compare-Object $englishKeys $iosKeys) { throw 'Android/iOS native key sets differ.' }
foreach ($key in $iosKeys) {
    $localizations = $iosCatalog.strings.$key.localizations.PSObject.Properties.Name
    if (@($localizations | Sort-Object) -join ',' -ne 'en,ru') {
        throw "iOS native key '$key' is not complete for en/ru."
    }
}

$manifest = Get-Content -Raw -LiteralPath $manifestPath
foreach ($forbiddenPermission in @('READ_MEDIA_IMAGES', 'READ_EXTERNAL_STORAGE', 'SCHEDULE_EXACT_ALARM', 'USE_EXACT_ALARM')) {
    if ($manifest.Contains($forbiddenPermission)) { throw "Forbidden broad/privileged permission: $forbiddenPermission" }
}
if (!$manifest.Contains('firebase_crashlytics_collection_enabled') -or !$manifest.Contains('android:value="false"')) {
    throw 'Android automatic Crashlytics collection must stay disabled until the stored preference is applied.'
}

$platformSources = Get-ChildItem -LiteralPath (Join-Path $repository 'shared\platform') -Recurse -File |
    Where-Object { $_.Extension -in '.kt', '.kts' }
$sourceText = ($platformSources | Get-Content -Raw) -join "`n"
foreach ($forbiddenApi in @('firebase-analytics', 'setUserId(', 'setCustomKey(', 'FirebaseAnalytics')) {
    if ($sourceText.Contains($forbiddenApi)) { throw "Forbidden Crashlytics/Analytics API: $forbiddenApi" }
}

$androidBuild = Get-Content -Raw -LiteralPath $androidBuildPath
foreach ($required in @('sproutpace.firebase.enabled', 'firebase-crashlytics', 'google-services')) {
    if (!$androidBuild.Contains($required)) { throw "Missing Android Firebase opt-in configuration: $required" }
}
$xcodeProject = Get-Content -Raw -LiteralPath $xcodeProjectPath
if (!$xcodeProject.Contains('dwarf-with-dsym')) { throw 'Release dSYM generation is not configured.' }
$iosInfo = Get-Content -Raw -LiteralPath (Join-Path $repository 'iosApp\iosApp\Info.plist')
if (!$iosInfo.Contains('FirebaseCrashlyticsCollectionEnabled') -or !$iosInfo.Contains('<false/>')) {
    throw 'iOS automatic Crashlytics collection must stay disabled until the stored preference is applied.'
}
$firebaseGuide = Get-Content -Raw -LiteralPath $firebaseGuidePath
foreach ($required in @('Crashlytics/run', 'dwarfdump --uuid', 'firebase-analytics')) {
    if (!$firebaseGuide.Contains($required)) { throw "Missing symbolication/privacy instruction: $required" }
}
foreach ($template in @($androidTemplatePath, $iosTemplatePath)) {
    $templateText = Get-Content -Raw -LiteralPath $template
    if (!$templateText.Contains('REPLACE_WITH_')) { throw "Firebase template must contain placeholders: $template" }
}
if (Test-Path -LiteralPath (Join-Path $repository 'androidApp\google-services.json')) {
    throw 'A real androidApp/google-services.json must not be present in repository delivery.'
}
if (Test-Path -LiteralPath (Join-Path $repository 'iosApp\iosApp\GoogleService-Info.plist')) {
    throw 'A real iosApp/iosApp/GoogleService-Info.plist must not be present in repository delivery.'
}

Write-Output "firebase-config-check: PASS ($($englishKeys.Count) localized native keys; optional configuration guarded)"
Write-Output 'crash-symbolication-check: PASS (mapping/dSYM setup and controlled verification documented)'
