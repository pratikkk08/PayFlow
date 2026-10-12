# Generates RSA JWT signing keys under backend/local-secrets (gitignored).
# Usage (from repo root): .\scripts\generate-jwt-keys.ps1

$ErrorActionPreference = "Stop"
$outDir = Join-Path $PSScriptRoot "..\backend\local-secrets" | Resolve-Path -ErrorAction SilentlyContinue
if (-not $outDir) {
    $outDir = Join-Path $PSScriptRoot "..\backend\local-secrets"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $outDir = Resolve-Path $outDir
}

$privatePath = Join-Path $outDir "jwt-private.pem"
$publicPath = Join-Path $outDir "jwt-public.pem"

if ((Test-Path $privatePath) -and (Test-Path $publicPath)) {
    Write-Host "Keys already exist at $outDir"
    exit 0
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
$code = @'
import java.nio.file.*;
import java.security.*;
import java.util.Base64;
public class Gen {
  public static void main(String[] a) throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair kp = g.generateKeyPair();
    Files.createDirectories(Path.of(a[0]).getParent());
    write(a[0], "PRIVATE KEY", kp.getPrivate().getEncoded());
    write(a[1], "PUBLIC KEY", kp.getPublic().getEncoded());
  }
  static void write(String path, String type, byte[] der) throws Exception {
    String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
    Files.writeString(Path.of(path), "-----BEGIN "+type+"-----\n"+b64+"\n-----END "+type+"-----\n");
  }
}
'@
$tmp = Join-Path $env:TEMP "payflow-gen-jwt"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Set-Content -Path (Join-Path $tmp "Gen.java") -Value $code -Encoding UTF8
Push-Location $tmp
& $java Gen.java $privatePath $publicPath
Pop-Location
Write-Host "Wrote $privatePath"
Write-Host "Wrote $publicPath"
