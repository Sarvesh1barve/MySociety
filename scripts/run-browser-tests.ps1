param([string]$Specs = 'app.spec.ts account-settings.spec.ts signup.spec.ts session-persistence.spec.ts')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
. "$PSScriptRoot/import-env.ps1"
$databaseUri = [Uri]($env:SPRING_DATASOURCE_URL -replace '^jdbc:', '')
if ($databaseUri.Host -notin @('localhost','127.0.0.1')) { throw 'Browser tests require local PostgreSQL.' }
if (Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8091 is occupied; no existing service was stopped.' }
$pg = Join-Path $projectRoot '.tools/postgres/pgsql/bin'
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
$existing = & "$pg/psql.exe" -h $databaseUri.Host -p $databaseUri.Port -U $env:SPRING_DATASOURCE_USERNAME -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='mysociety_e2e'"
$credentialFile=Join-Path $projectRoot '.local/e2e-credentials.json'
if (!$existing) {
  & "$pg/createdb.exe" -h $databaseUri.Host -p $databaseUri.Port -U $env:SPRING_DATASOURCE_USERNAME mysociety_e2e
  if($LASTEXITCODE -ne 0){throw 'Could not create isolated database.'}
  $secret='Browser-'+[Guid]::NewGuid().ToString()
  @{password=$secret} | ConvertTo-Json | Set-Content -LiteralPath $credentialFile
} elseif (!(Test-Path -LiteralPath $credentialFile)) { throw 'Existing test DB has no matching private credentials. It was left untouched.' }
Remove-Item Env:PGPASSWORD
$secret=(Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json).password
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$($databaseUri.Port)/mysociety_e2e"
$env:SERVER_PORT='8091'
$env:SERVER_ADDRESS='127.0.0.1'
$env:INITIAL_SUPER_ADMIN_EMAIL='owner@e2e.test'
$env:INITIAL_SUPER_ADMIN_NAME='Browser Test Owner'
$env:INITIAL_SUPER_ADMIN_PASSWORD=$secret
$env:DEMO_PASSWORD=$secret
$env:JWT_SECRET=[Guid]::NewGuid().ToString()+[Guid]::NewGuid().ToString()
$env:UPLOAD_DIR=Join-Path $projectRoot '.local/e2e-uploads'
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
$classpath=(Get-Content -LiteralPath 'backend/target/test-classpath.txt' -Raw).Trim()
$classpath="$projectRoot/backend/target/test-classes;$projectRoot/backend/target/classes;$classpath"
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{(Get-Command java).Source}
$server=Start-Process -FilePath $java -ArgumentList @('-cp', ('"'+$classpath+'"'), 'com.mysociety.BrowserTestServer') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$projectRoot/.local/e2e-backend.log" -RedirectStandardError "$projectRoot/.local/e2e-backend-error.log"
try {
  $ready=$false
  for($attempt=0;$attempt -lt 120;$attempt++) {
    if($server.HasExited){throw 'Isolated server failed; inspect .local/e2e-backend logs.'}
    try { $health=Invoke-RestMethod 'http://localhost:8091/api/v1/system/health'; if($health.status -eq 'UP'){$ready=$true;break} } catch {}
    Start-Sleep -Seconds 1
  }
  if(!$ready){throw 'Test server did not become ready.'}
  $env:E2E_ISOLATED='true'; $env:E2E_API_URL='http://localhost:8091'
  $env:E2E_PASSWORD=$secret; $env:E2E_OWNER_PASSWORD=$secret; $env:E2E_OWNER_EMAIL='owner@e2e.test'
  Set-Location (Join-Path $projectRoot 'frontend')
  & npx.cmd playwright test @($Specs.Split(' ',[StringSplitOptions]::RemoveEmptyEntries))
  $testExit=$LASTEXITCODE
} finally {
  # Stop only the exact fixture process created by this script, never a shared/live API.
  if(!$server.HasExited){Stop-Process -Id $server.Id}
  Set-Location $projectRoot
}
exit $testExit
