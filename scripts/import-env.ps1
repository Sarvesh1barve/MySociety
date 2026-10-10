$projectRoot = Split-Path $PSScriptRoot -Parent
$envPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envPath) {
 foreach ($line in Get-Content -LiteralPath $envPath) {
  if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
   [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
  }
 }
}
