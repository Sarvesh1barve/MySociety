$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
New-Item -ItemType Directory -Force '.local' | Out-Null
if (Test-Path -LiteralPath '.env') { throw '.env already exists. Existing settings were preserved.' }
function New-Secret { $bytes = New-Object byte[] 36; $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose(); [Convert]::ToBase64String($bytes) }
$dbPassword = New-Secret
$ownerPassword = New-Secret
$demoPassword = New-Secret
$jwtSecret = New-Secret
$dbPort = 5432
while (Get-NetTCPConnection -LocalPort $dbPort -State Listen -ErrorAction SilentlyContinue) { $dbPort++ }
$lines = @(
 'SERVER_PORT=8081'
 "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$dbPort/mysociety"
 "TEST_DATABASE_URL=jdbc:postgresql://localhost:$dbPort/mysociety_test"
 "POSTGRES_PORT=$dbPort"
 'SPRING_DATASOURCE_USERNAME=mysociety'
 "SPRING_DATASOURCE_PASSWORD=$dbPassword"
 'POSTGRES_DB=mysociety'
 'POSTGRES_USER=mysociety'
 "POSTGRES_PASSWORD=$dbPassword"
 "JWT_SECRET=$jwtSecret"
 'INITIAL_SUPER_ADMIN_NAME=Sarvesh Barve'
 'INITIAL_SUPER_ADMIN_EMAIL=owner@mysociety.local'
 "INITIAL_SUPER_ADMIN_PASSWORD=$ownerPassword"
 "DEMO_PASSWORD=$demoPassword"
 'APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://sarvesh1barve.github.io'
 "UPLOAD_DIR=$projectRoot/.local/uploads"
)
[IO.File]::WriteAllLines((Join-Path $projectRoot '.env'), $lines)
$passFile = Join-Path $projectRoot '.local/pg-password.txt'
[IO.File]::WriteAllText($passFile, $dbPassword)
$pgBin = Join-Path $projectRoot '.tools/postgres/pgsql/bin'
if (!(Test-Path (Join-Path $pgBin 'initdb.exe'))) { throw 'Install PostgreSQL or download the portable binaries to .tools/postgres/pgsql first.' }
& "$pgBin/initdb.exe" -D "$projectRoot/.local/postgres-data" -U mysociety --auth=scram-sha-256 --pwfile="$passFile" --encoding=UTF8 --locale=C
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initialization failed.' }
Remove-Item -LiteralPath $passFile
& "$pgBin/pg_ctl.exe" -D "$projectRoot/.local/postgres-data" -l "$projectRoot/.local/postgres.log" -o "-h 127.0.0.1 -p $dbPort" -w start
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed.' }
$env:PGPASSWORD = $dbPassword
& "$pgBin/createdb.exe" -h 127.0.0.1 -p $dbPort -U mysociety mysociety
& "$pgBin/createdb.exe" -h 127.0.0.1 -p $dbPort -U mysociety mysociety_test
Remove-Item Env:PGPASSWORD
Write-Host 'Local PostgreSQL is running on loopback. Generated credentials are in the ignored .env file.'
