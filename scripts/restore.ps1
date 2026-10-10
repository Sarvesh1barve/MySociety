param([Parameter(Mandatory=$true)][string]$BackupDirectory,[Parameter(Mandatory=$true)][string]$NewDatabase)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/import-env.ps1"
if ($NewDatabase -notmatch '^mysociety_restore_[a-zA-Z0-9_]+$') { throw 'Use a NEW database named mysociety_restore_<suffix>. Existing databases are never overwritten.' }
$backupPath = (Resolve-Path -LiteralPath $BackupDirectory).Path
$dumpPath = Join-Path $backupPath 'database.dump'
if (!(Test-Path -LiteralPath $dumpPath -PathType Leaf)) { throw 'database.dump is missing.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$databaseUri = [Uri]($env:SPRING_DATASOURCE_URL -replace '^jdbc:', '')
$pgBin = Join-Path $projectRoot '.tools/postgres/pgsql/bin'
$create = if (Get-Command createdb -ErrorAction SilentlyContinue) { (Get-Command createdb).Source } else { Join-Path $pgBin 'createdb.exe' }
$restore = if (Get-Command pg_restore -ErrorAction SilentlyContinue) { (Get-Command pg_restore).Source } else { Join-Path $pgBin 'pg_restore.exe' }
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
try {
 & $create -h $databaseUri.Host -p $databaseUri.Port -U $env:SPRING_DATASOURCE_USERNAME $NewDatabase
 if ($LASTEXITCODE -ne 0) { throw 'Cannot create the new restore database. Nothing was overwritten.' }
 & $restore -h $databaseUri.Host -p $databaseUri.Port -U $env:SPRING_DATASOURCE_USERNAME -d $NewDatabase --no-owner --exit-on-error $dumpPath
 if ($LASTEXITCODE -ne 0) { throw 'Restore failed. Inspect the new database; the original is unchanged.' }
 $restoreFiles = Join-Path $projectRoot ".local/$NewDatabase-uploads"
 if (Test-Path -LiteralPath $restoreFiles) { throw 'Restore file destination exists. Database restored; original files preserved.' }
 $backupFiles = Join-Path $backupPath 'uploads'
 if (Test-Path -LiteralPath $backupFiles) { Copy-Item -LiteralPath $backupFiles -Destination $restoreFiles -Recurse }
 Write-Host "Restored to $NewDatabase and $restoreFiles. Verify before changing application settings."
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
