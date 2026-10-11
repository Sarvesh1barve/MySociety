$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
$tracked = @(git diff --cached --name-only --diff-filter=ACM)
$blocked = @($tracked | Where-Object { $_ -match '(^|/)(\.env($|\.(?!example$))|\.local/|\.tools/|node_modules/|target/|dist/|data/uploads/)' -or $_ -match '\.(dump|backup|log)$' })
if ($blocked.Count) { throw "Private/generated paths are staged: $($blocked -join ', ')" }
$secrets = @()
if (Test-Path -LiteralPath '.env') {
 foreach ($line in Get-Content -LiteralPath '.env') {
  if ($line -match '^([A-Z_]*(PASSWORD|SECRET|TOKEN)[A-Z_]*)=(.+)$' -and $Matches[3].Length -ge 8) { $secrets += $Matches[3] }
 }
}
if (Test-Path -LiteralPath '.local/SUPER_ADMIN_ACCESS.md') {
 foreach ($line in Get-Content -LiteralPath '.local/SUPER_ADMIN_ACCESS.md') {
  if ($line -match '^\- ([A-F0-9]{8}(?:\-[A-F0-9]{8}){3})$') { $secrets += $Matches[1] }
 }
}
if (Test-Path -LiteralPath '.local/e2e-credentials.json') {
 $secrets += (Get-Content -LiteralPath '.local/e2e-credentials.json' -Raw | ConvertFrom-Json).password
}
foreach ($path in $tracked) {
 if ($path -match '\.(png|ico)$') { continue }
 $content = git show ":$path" | Out-String
 foreach ($secret in $secrets) { if ($content.Contains($secret)) { throw "A local secret was found in staged file $path. Value was not printed." } }
}
Write-Host "Checked $($tracked.Count) staged files. No local secrets or private/generated paths found."
