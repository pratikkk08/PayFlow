# Sets JAVA_HOME for the current PowerShell session to the Temurin JDK 21 install.
# Usage: . .\scripts\set-java-home.ps1

$candidate = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
if (-not (Test-Path $candidate)) {
    Write-Error "Temurin JDK 21 not found at $candidate. Install Temurin 21 or update this script."
    return
}

$env:JAVA_HOME = $candidate
$env:Path = "$env:JAVA_HOME\bin;" + ($env:Path -split ';' | Where-Object { $_ -and $_ -notlike '*\Eclipse Adoptium\*' } | Select-Object -Unique) -join ';'
Write-Host "JAVA_HOME=$env:JAVA_HOME"
java -version
