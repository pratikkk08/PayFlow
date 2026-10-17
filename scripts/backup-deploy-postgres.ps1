# Logical pg_dump of the payflow-deploy Postgres service. Never prints passwords.
# Usage (repo root):
#   .\scripts\backup-deploy-postgres.ps1
#   .\scripts\backup-deploy-postgres.ps1 -EnvFile secrets\deploy-smoke\.env -OutputDir backups\deploy

[CmdletBinding()]
param(
	[string]$ComposeFile = "infra/docker-compose.deploy.yml",
	[string]$EnvFile = "secrets/deploy-smoke/.env",
	[string]$OutputDir = "backups/deploy",
	[string]$ProjectName = "payflow-deploy",
	[string]$Service = "postgres"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path -LiteralPath $ComposeFile)) {
	throw "Compose file not found: $ComposeFile"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
	throw "Env file not found: $EnvFile (run .\scripts\prepare-deploy-smoke-secrets.ps1 first)"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $OutputDir "payflow-$stamp.dump"
$remotePath = "/tmp/payflow-backup-$stamp.dump"

Write-Host "Backing up service '$Service' (project $ProjectName) to $outFile ..."

$common = @(
	"compose", "-f", $ComposeFile, "--env-file", $EnvFile, "-p", $ProjectName
)

& docker @common exec -T $Service sh -c "pg_dump -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -Fc -f $remotePath"
if ($LASTEXITCODE -ne 0) {
	throw "pg_dump failed (is the deploy stack running?)"
}

& docker @common cp "${Service}:${remotePath}" $outFile
if ($LASTEXITCODE -ne 0) {
	throw "docker compose cp failed"
}

& docker @common exec -T $Service rm -f $remotePath | Out-Null

$size = (Get-Item -LiteralPath $outFile).Length
if ($size -lt 100) {
	Remove-Item -LiteralPath $outFile -Force
	throw "Backup file too small ($size bytes) - dump likely empty"
}

Write-Host "Backup OK: $outFile ($size bytes)"
Write-Host "See docs/BACKUP_RECOVERY.md for verification and disposable restore."
