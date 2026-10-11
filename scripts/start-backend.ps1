param([switch]$Dev)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/import-env.ps1"
$selectedPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8081 }
while (Get-NetTCPConnection -LocalPort $selectedPort -State Listen -ErrorAction SilentlyContinue) { $selectedPort++ }
$env:SERVER_PORT = "$selectedPort"
$projectRoot = Split-Path $PSScriptRoot -Parent
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.local') | Out-Null
[IO.File]::WriteAllText((Join-Path $projectRoot '.local/backend-port.txt'), "$selectedPort")
$runtimeConfig = @{ apiBaseUrl = "http://localhost:$selectedPort"; ngrokSkipBrowserWarning = $false } | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $projectRoot 'frontend/public/runtime-config.json'), $runtimeConfig)
if ($Dev) { $env:SPRING_PROFILES_ACTIVE = 'dev' } else { Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue }
Write-Host "My Society backend: http://localhost:$selectedPort"
Write-Host "Tunnel command: ngrok http $selectedPort"
Set-Location (Join-Path $PSScriptRoot '../backend')
& ./mvnw.cmd spring-boot:run
