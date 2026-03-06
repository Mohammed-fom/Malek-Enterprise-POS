# Malek Enterprise POS - Portable Build Script
# Produces: dist\MalekPOS\MalekPOS.exe  (bundled Java, no install required)
# Usage:    .\package.ps1

$ErrorActionPreference = "Stop"

$ProjectDir = $PSScriptRoot
$TargetDir = Join-Path $ProjectDir "target"
$DistDir = Join-Path $ProjectDir "dist"
$AppName = "MalekPOS"
$AppVersion = "1.0.0"
$MainJar = Join-Path $TargetDir "enterprise-pos-1.0-SNAPSHOT.jar"
$MainClass = "com.malek.pos.Launcher"

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Malek Enterprise POS - Portable Builder   " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build fat JAR
Write-Host "[1/3] Building fat JAR with Maven..." -ForegroundColor Yellow
Set-Location $ProjectDir
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Maven build failed." -ForegroundColor Red
    exit 1
}
Write-Host "      Fat JAR created: $MainJar" -ForegroundColor Green

# Step 2: Clean output dir
Write-Host "[2/3] Preparing output directory..." -ForegroundColor Yellow
if (Test-Path $DistDir) { Remove-Item $DistDir -Recurse -Force }
New-Item -ItemType Directory -Path $DistDir | Out-Null

# Step 3: Run jpackage
Write-Host "[3/3] Running jpackage (this may take 1-2 minutes)..." -ForegroundColor Yellow

$wixAvailable = $null -ne (Get-Command "candle.exe" -ErrorAction SilentlyContinue)

if ($wixAvailable) {
    Write-Host "      WiX found - building .exe installer..." -ForegroundColor DarkGray
    $type = "exe"
}
else {
    Write-Host "      Building portable app-image (zip and send)..." -ForegroundColor DarkGray
    $type = "app-image"
}

$jpackageArgs = @(
    "--input", $TargetDir,
    "--dest", $DistDir,
    "--name", $AppName,
    "--main-jar", (Split-Path $MainJar -Leaf),
    "--main-class", $MainClass,
    "--app-version", $AppVersion,
    "--type", $type,
    "--java-options", "-Xmx512m",
    "--java-options", "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED"
)


# Resolve jpackage path (JDK bin may not be on PATH)
$jpackagePath = "jpackage"
if (-not (Get-Command "jpackage" -ErrorAction SilentlyContinue)) {
    $candidates = @(
        "C:\Program Files\Java\jdk-21\bin\jpackage.exe",
        "C:\Program Files\Java\jdk-21.0.2\bin\jpackage.exe",
        "$env:JAVA_HOME\bin\jpackage.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $jpackagePath = $c; break }
    }
    if ($jpackagePath -eq "jpackage") {
        # Last resort: search Program Files
        $found = Get-ChildItem "C:\Program Files\Java" -Recurse -Filter "jpackage.exe" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
        if ($found) { $jpackagePath = $found }
    }
}
Write-Host "      Using jpackage: $jpackagePath" -ForegroundColor DarkGray

& $jpackagePath @jpackageArgs


if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: jpackage failed. See output above." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green

if ($type -eq "app-image") {
    Write-Host ""
    Write-Host "  Output folder : dist\$AppName\" -ForegroundColor White
    Write-Host "  Executable    : dist\$AppName\$AppName.exe" -ForegroundColor White
    Write-Host ""
    Write-Host "  HOW TO DISTRIBUTE:" -ForegroundColor Cyan
    Write-Host "  1. Zip the entire 'dist\$AppName\' folder" -ForegroundColor White
    Write-Host "  2. Send the zip to your client" -ForegroundColor White
    Write-Host "  3. Client extracts and double-clicks $AppName.exe" -ForegroundColor White
    Write-Host "     (No Java install required!)" -ForegroundColor Green
}
else {
    Write-Host ""
    Write-Host "  Installer : dist\${AppName}-${AppVersion}.exe" -ForegroundColor White
    Write-Host "  Send this single .exe to your clients." -ForegroundColor Green
}
Write-Host ""
