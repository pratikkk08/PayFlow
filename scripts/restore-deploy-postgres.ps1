# Restore a pg_dump into an ISOLATED disposable database on deploy Postgres.
# Refuses to target the live POSTGRES_DB. Never prints passwords.
# Usage (repo root):
#   .\scripts\restore-deploy-postgres.ps1 -DumpFile backups\deploy\payflow-....dump `
#     -TargetDatabase payflow_restore_check_001 -ConfirmPhrase "RESTORE TO payflow_restore_check_001"

[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)]
	[string]$DumpFile,
	[Parameter(Mandatory = $true)]
	[string]$TargetDatabase,
	[string]$ComposeFile = "infra/docker-compose.deploy.yml",
	[string]$EnvFile = "secrets/deploy-smoke/.env",
	[string]$ProjectName = "payflow-deploy",
	[string]$Service = "postgres",
	[string]$ConfirmPhrase = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path -LiteralPath $DumpFile)) {
	throw "Dump file not found: $DumpFile"
}
if (-not (Test-Path -LiteralPath $ComposeFile)) {
	throw "Compose file not found: $ComposeFile"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
	throw "Env file not found: $EnvFile"
}

if ($TargetDatabase -notmatch '^payflow_restore_[A-Za-z0-9_]+$') {
	throw "TargetDatabase must match '^payflow_restore_[A-Za-z0-9_]+$' (disposable restore DBs only)."
}

$liveDb = $null
$pgUser = $null
Get-Content -LiteralPath $EnvFile | ForEach-Object {
	if ($_ -match '^\s*POSTGRES_DB\s*=\s*(.+)\s*$') {
		$liveDb = $Matches[1].Trim().Trim('"').Trim("'")
	}
	if ($_ -match '^\s*POSTGRES_USER\s*=\s*(.+)\s*$') {
		$pgUser = $Matches[1].Trim().Trim('"').Trim("'")
	}
}
if ([string]::IsNullOrWhiteSpace($liveDb)) {
	throw "Could not resolve POSTGRES_DB from env file."
}
if ([string]::IsNullOrWhiteSpace($pgUser)) {
	throw "Could not resolve POSTGRES_USER from env file."
}
if ($TargetDatabase -eq $liveDb) {
	throw "Refusing to restore onto live database name '$liveDb'."
}

$expectedConfirm = "RESTORE TO $TargetDatabase"
if ($ConfirmPhrase -ne $expectedConfirm) {
	Write-Host "This will CREATE (if needed) and restore into disposable database: $TargetDatabase"
	Write-Host "Live database '$liveDb' will NOT be modified."
	Write-Host "Re-run with: -ConfirmPhrase `"$expectedConfirm`""
	exit 2
}

$common = @(
	"compose", "-f", $ComposeFile, "--env-file", $EnvFile, "-p", $ProjectName
)
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$remoteDump = "/tmp/payflow-restore-$stamp.dump"

Write-Host "Copying dump into container (live DB untouched)..."
& docker @common cp $DumpFile "${Service}:${remoteDump}"
if ($LASTEXITCODE -ne 0) {
	throw "docker compose cp of dump failed"
}

Write-Host "Ensuring disposable database exists..."
$exists = (& docker @common exec -T $Service psql -U $pgUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$TargetDatabase'" | Out-String).Trim()
if ($exists -ne "1") {
	& docker @common exec -T $Service psql -U $pgUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $TargetDatabase"
	if ($LASTEXITCODE -ne 0) {
		& docker @common exec -T $Service rm -f $remoteDump | Out-Null
		throw "Failed to create disposable database $TargetDatabase"
	}
}

$probe = (& docker @common exec -T $Service psql -U $pgUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$TargetDatabase'" | Out-String).Trim()
if ($probe -ne "1") {
	& docker @common exec -T $Service rm -f $remoteDump | Out-Null
	throw "Disposable database $TargetDatabase was not created"
}

Write-Host "Restoring into $TargetDatabase ..."
& docker @common exec -T $Service pg_restore -U $pgUser -d $TargetDatabase --clean --if-exists --no-owner --no-acl $remoteDump
$restoreCode = $LASTEXITCODE
& docker @common exec -T $Service rm -f $remoteDump | Out-Null

if ($restoreCode -gt 1) {
	throw "pg_restore failed with exit code $restoreCode"
}

$flyway = (& docker @common exec -T $Service psql -U $pgUser -d $TargetDatabase -tAc "SELECT COUNT(*) FROM flyway_schema_history" | Out-String).Trim()
if (-not ($flyway -match '^\d+$') -or [int]$flyway -lt 1) {
	throw "Restore verification failed: flyway_schema_history missing or empty in $TargetDatabase"
}

Write-Host "Restore finished into disposable DB '$TargetDatabase' (flyway_schema_history rows=$flyway)."
Write-Host "Spot-check data, then DROP DATABASE $TargetDatabase;"
Write-Host "This script never drops or overwrites the live database."
