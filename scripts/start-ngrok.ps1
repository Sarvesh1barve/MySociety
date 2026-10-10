$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/import-env.ps1"
$portFile = Join-Path $PSScriptRoot '../.local/backend-port.txt'
$backendPort = if (Test-Path -LiteralPath $portFile) { [int](Get-Content -LiteralPath $portFile) } elseif ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8081 }
$health = Invoke-RestMethod "http://localhost:$backendPort/api/v1/system/health"
if ($health.status -ne 'UP') { throw 'Backend health check failed.' }
Write-Host "Publishing the Spring Boot API on port $backendPort. PostgreSQL stays private."
& ngrok http $backendPort --inspect=false
