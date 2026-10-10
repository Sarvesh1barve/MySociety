param([string]$OutputDirectory = '')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/import-env.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot '.local/backups' }
$stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$backupDirectory = Join-Path $OutputDirectory $stamp
if (Test-Path -LiteralPath $backupDirectory) { throw 'Backup destination already exists.' }
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$databaseUri = [Uri]($env:SPRING_DATASOURCE_URL -replace '^jdbc:', '')
$pgDump = if (Get-Command pg_dump -ErrorAction SilentlyContinue) { (Get-Command pg_dump).Source } else { Join-Path $projectRoot '.tools/postgres/pgsql/bin/pg_dump.exe' }
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
try {
 & $pgDump -h $databaseUri.Host -p $databaseUri.Port -U $env:SPRING_DATASOURCE_USERNAME -d $databaseUri.AbsolutePath.TrimStart('/') -Fc -f (Join-Path $backupDirectory 'database.dump')
 if ($LASTEXITCODE -ne 0) { throw 'Database backup failed.' }
 $uploadDirectory = if ($env:UPLOAD_DIR) { $env:UPLOAD_DIR } else { Join-Path $projectRoot 'backend/data/uploads' }
 if (Test-Path -LiteralPath $uploadDirectory) { Copy-Item -LiteralPath $uploadDirectory -Destination (Join-Path $backupDirectory 'uploads') -Recurse }
 Write-Host "Backup saved to $backupDirectory. Protect it as private resident data."
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
