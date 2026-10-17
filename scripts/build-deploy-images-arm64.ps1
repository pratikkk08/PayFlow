# Build PayFlow deploy images for linux/arm64 and load into the local Docker engine.
# Uses Docker Buildx. On amd64 hosts this typically runs under QEMU emulation (slow).
# Does NOT push to any registry. Does NOT provision cloud resources.
#
# Usage (repo root):
#   .\scripts\build-deploy-images-arm64.ps1
#   .\scripts\build-deploy-images-arm64.ps1 -FrontendOnly
#   .\scripts\build-deploy-images-arm64.ps1 -BackendOnly

param(
	[switch]$FrontendOnly,
	[switch]$BackendOnly
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")

Write-Host "Docker Buildx platforms:"
docker buildx ls
Write-Host ""

function Build-Arm64Image {
	param(
		[string]$Context,
		[string]$Dockerfile,
		[string]$Tag
	)
	Write-Host "==== Building $Tag (linux/arm64) from $Context ===="
	$sw = [System.Diagnostics.Stopwatch]::StartNew()
	& docker buildx build `
		--platform linux/arm64 `
		--load `
		-t $Tag `
		-f $Dockerfile `
		$Context
	if ($LASTEXITCODE -ne 0) {
		throw "ARM64 build failed for $Tag (exit $LASTEXITCODE)."
	}
	$sw.Stop()
	Write-Host ("Built {0} in {1:N1}s" -f $Tag, $sw.Elapsed.TotalSeconds)
	docker image inspect $Tag --format '{{.Os}}/{{.Architecture}} size={{.Size}}'
}

Push-Location $root
try {
	if (-not $FrontendOnly) {
		Build-Arm64Image -Context (Join-Path $root "backend") -Dockerfile (Join-Path $root "backend\Dockerfile") -Tag "payflow-backend:deploy-arm64"
	}
	if (-not $BackendOnly) {
		Build-Arm64Image -Context (Join-Path $root "frontend") -Dockerfile (Join-Path $root "frontend\Dockerfile") -Tag "payflow-frontend:deploy-arm64"
	}
}
finally {
	Pop-Location
}

Write-Host "Done. Images are local only (no registry push)."
Write-Host "Native Ampere A1 execution remains NOT VERIFIED until run on an Oracle A1 VM."
