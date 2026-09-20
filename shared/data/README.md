# SproutPace local data

This Kotlin Multiplatform module owns relational product data, typed preferences, and private observation photos.

The batch-history CSV is UTF-8 with RFC 4180-style quoting, CRLF rows, and the stable header:

`batch_id,culture_id,recipe_name,state,outcome_at,actual_mass_grams,note`

CSV intentionally excludes photo bytes and is not accepted by the restore API. Backups use `LocalBackupArchive`, validate their version, counts, relationships, and SHA-256 photo integrity before replacement, and replace rather than merge.

Platform document/photo pickers and reminder rebuilding live outside this module. Callers own external file selection and post-commit reminder scheduling.

## SQLDelight schema and migrations

`SproutPace.sq` is the version-1 initial schema. It is intentionally not duplicated as
`1.sqm`: a numbered `.sqm` file describes a forward transition from an already captured
schema version, not bootstrap SQL. Foreign-key activation is a per-connection driver concern
and therefore is not emitted as schema DDL.

The committed `src/commonMain/sqldelight/databases/1.db` is the executable baseline for this
initial schema. It is equivalent to the output of
`:shared:data:generateCommonMainSproutPaceDatabaseSchema` and is retained unchanged once v1 is
deployed. The next schema version adds `1.sqm` (v1 to v2); a later `2.sqm` is chained after it,
and both are verified from the committed v1 baseline. Keep `schemaOutputDirectory` and
`verifyMigrations` enabled and run `:shared:data:verifyCommonMainSproutPaceDatabaseMigration`
and `:shared:data:verifySqlDelightMigration` for every migration change. Never replace a
migration with destructive reset SQL.
