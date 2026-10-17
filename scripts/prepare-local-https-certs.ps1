# Generates a local self-signed TLS cert+key for PayFlow HTTPS deploy smoke (Task 9.2).
# Uses the official alpine/openssl container image (no ad-hoc binary download).
# Output: secrets/local-https/tls.crt + tls.key (gitignored via secrets/). Never prints the private key.
#
# Usage (repo root): .\scripts\prepare-local-https-certs.ps1
# Trust: browsers will warn unless you import tls.crt into the OS trust store. Playwright
# HTTPS E2E uses narrowly scoped ignoreHTTPSErrors for this local self-signed case only.

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$outDir = Join-Path $root "secrets\local-https"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$certPath = Join-Path $outDir "tls.crt"
$keyPath = Join-Path $outDir "tls.key"

if ((Test-Path $certPath) -and (Test-Path $keyPath)) {
	Write-Host "Local HTTPS cert already present under secrets/local-https/ (gitignored)."
	Write-Host "Delete that directory and re-run to regenerate."
	exit 0
}

# SAN covers loopback hostnames used by local smoke / Playwright.
# Mount as /work so OpenSSL writes into the gitignored secrets folder only.
$mount = "${outDir}:/work"
$opensslArgs = @(
	"run", "--rm",
	"-v", $mount,
	"alpine/openssl",
	"req", "-x509", "-nodes", "-newkey", "rsa:2048",
	"-keyout", "/work/tls.key",
	"-out", "/work/tls.crt",
	"-days", "825",
	"-subj", "/CN=localhost/O=PayFlow Local HTTPS/OU=Task 9.2",
	"-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1"
)

Write-Host "Generating self-signed localhost certificate via alpine/openssl..."
& docker @opensslArgs
if ($LASTEXITCODE -ne 0) {
	throw "OpenSSL certificate generation failed (exit $LASTEXITCODE)."
}

if (-not ((Test-Path $certPath) -and (Test-Path $keyPath))) {
	throw "Expected tls.crt and tls.key under secrets/local-https/ were not created."
}

# OpenSSL defaults private keys to 0600 root:root; nginx-unprivileged (uid 101) must read them.
# Local gitignored smoke only — never commit these files.
& docker run --rm -v $mount alpine:3.21 chmod 644 /work/tls.crt /work/tls.key
if ($LASTEXITCODE -ne 0) {
	throw "Failed to set world-readable mode on local TLS files for nginx-unprivileged."
}

# Do not print key material. Show subject/SAN only.
Write-Host "Wrote secrets/local-https/tls.crt and tls.key (gitignored)."
& docker run --rm -v $mount alpine/openssl x509 -in /work/tls.crt -noout -subject -dates -ext subjectAltName
Write-Host "Browser trust is NOT automatic for this self-signed cert - see docs/SETUP.md."
