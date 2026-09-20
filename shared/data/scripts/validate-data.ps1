$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$required = @(
    'build.gradle.kts',
    'src/commonMain/sqldelight/com/sedsoftware/sproutpace/data/db/SproutPace.sq',
    'src/commonMain/kotlin/com/sedsoftware/sproutpace/data/LocalDatabase.kt',
    'src/commonMain/kotlin/com/sedsoftware/sproutpace/data/BackupAndExport.kt',
    'src/androidMain/kotlin/com/sedsoftware/sproutpace/data/AndroidStorage.kt',
    'src/iosMain/kotlin/com/sedsoftware/sproutpace/data/IosStorage.kt',
    'src/androidHostTest/kotlin/com/sedsoftware/sproutpace/data/LocalDatabaseIntegrationTest.kt'
)
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $relative))) { throw "Missing required file: $relative" }
}
$kotlin = Get-ChildItem -LiteralPath $root -Recurse -Filter '*.kt'
foreach ($file in $kotlin) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if (([regex]::Matches($text, '\{').Count) -ne ([regex]::Matches($text, '\}').Count)) {
        throw "Brace mismatch: $($file.FullName)"
    }
}
$schema = Get-Content -LiteralPath (Join-Path $root 'src/commonMain/sqldelight/com/sedsoftware/sproutpace/data/db/SproutPace.sq') -Raw
$tables = @('grow_zone','tray_preset','user_recipe','grow_batch','harvest_plan','observation')
foreach ($table in $tables) {
    if ($schema -notmatch "CREATE TABLE $table") { throw "Missing schema table: $table" }
}
if ($schema -match 'INSERT OR REPLACE') { throw 'Destructive SQLite REPLACE semantics are forbidden' }
if ($schema -match 'INSERT OR IGNORE') { throw 'Constraint-swallowing SQLite INSERT OR IGNORE is forbidden' }
if ($schema -match '(?im)^\s*PRAGMA\s+') { throw 'Connection PRAGMAs do not belong in SQLDelight schema DDL' }
if ($schema -match '(?i)ON\s+CONFLICT\s*\([^)]*\)\s+DO\s+UPDATE') {
    throw 'SQLite syntax newer than the configured 3.18 dialect is forbidden'
}
$migrationRoot = Join-Path $root 'src/commonMain/sqldelight'
$migrations = @(Get-ChildItem -LiteralPath $migrationRoot -Recurse -Filter '*.sqm')
$snapshots = @(Get-ChildItem -LiteralPath (Join-Path $migrationRoot 'databases') -Filter '*.db' -ErrorAction SilentlyContinue)
$baseline = Join-Path $migrationRoot 'databases/1.db'
if (-not (Test-Path -LiteralPath $baseline)) { throw 'Missing committed SQLDelight v1 baseline databases/1.db' }
$header = [System.IO.File]::ReadAllBytes($baseline)[0..15]
$expectedHeader = [System.Text.Encoding]::ASCII.GetBytes("SQLite format 3`0")
if (-not [System.Linq.Enumerable]::SequenceEqual([byte[]]$header, [byte[]]$expectedHeader)) {
    throw 'SQLDelight v1 baseline is not a SQLite database'
}
$sqliteValidation = @'
import sqlite3
import sys

connection = sqlite3.connect(sys.argv[1])
tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
indexes = {
    row[0]
    for row in connection.execute(
        "SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_autoindex_%'"
    )
}
expected_tables = {'grow_zone', 'tray_preset', 'user_recipe', 'grow_batch', 'harvest_plan', 'observation'}
expected_indexes = {
    'grow_zone_order', 'tray_preset_order', 'user_recipe_culture',
    'grow_batch_state_order', 'observation_batch_time'
}
assert tables == expected_tables, (tables, expected_tables)
assert indexes == expected_indexes, (indexes, expected_indexes)
assert connection.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
assert connection.execute('PRAGMA user_version').fetchone()[0] == 1
foreign_keys = connection.execute('PRAGMA foreign_key_list(observation)').fetchall()
assert any(
    row[2] == 'grow_batch' and row[3] == 'batch_id' and row[4] == 'id' and row[6] == 'CASCADE'
    for row in foreign_keys
), foreign_keys
assert all(connection.execute('SELECT COUNT(*) FROM ' + table).fetchone()[0] == 0 for table in tables)
connection.close()
'@
$sqliteValidation | python - $baseline
if ($LASTEXITCODE -ne 0) { throw 'SQLite baseline integrity/structure validation failed' }
if ($migrations.Count -gt 0 -and $snapshots.Count -eq 0) { throw 'Forward migrations require a committed prior .db schema snapshot' }
$build = Get-Content -LiteralPath (Join-Path $root 'build.gradle.kts') -Raw
if (-not $build.Contains('schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))')) {
    throw 'schemaOutputDirectory no longer points at the committed baseline directory'
}
$upsertGroups = ([regex]::Matches($schema, '(?m)^upsert[A-Za-z]+\s*\{\s*$')).Count
if ($upsertGroups -ne 6) { throw "Expected 6 SQLite-3.18-safe upsert statement groups, found $upsertGroups" }
$guardedInserts = ([regex]::Matches($schema, '(?i)WHERE\s+changes\(\)\s*=\s*0')).Count
if ($guardedInserts -ne 6) { throw "Expected 6 constraint-preserving guarded inserts, found $guardedInserts" }
$source = ($kotlin | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
foreach ($forbidden in @('Firebase','YandexAds','languagePreference','localePreference')) {
    if ($source.Contains($forbidden)) { throw "Forbidden persistence concern: $forbidden" }
}
if (-not $source.Contains('batch_id,culture_id,recipe_name,state,outcome_at,actual_mass_grams,note')) {
    throw 'Stable CSV header is missing'
}
Write-Output "Validated $($kotlin.Count) Kotlin files, $($tables.Count) SQL tables, platform drivers, tests, and scope exclusions."
