<#
  Build Rerun and install it on a Fire TV over Wi-Fi.

  One-time on the TV: Settings > My Fire TV > About > click the device name 7 times
  to unlock Developer Options, then Developer Options > ADB Debugging: ON.
  Find the TV's IP under Settings > My Fire TV > About > Network.

  Usage:  .\scripts\install.ps1 -TvIp 192.168.1.50
#>
param(
    [Parameter(Mandatory = $true)][string]$TvIp,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $env:ANDROID_HOME -and -not (Test-Path "$root\local.properties")) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $sdk) { "sdk.dir=$($sdk -replace '\\','\\')" | Set-Content "$root\local.properties" -Encoding ASCII }
    else { throw "Android SDK not found. Install it or set ANDROID_HOME." }
}

if (-not $SkipBuild) {
    & "$root\gradlew.bat" ':app:assembleDebug' --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
}

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK not found at $apk" }

adb connect "${TvIp}:5555" | Write-Host
Write-Host "If the TV shows 'Allow USB debugging?', choose Always allow, then press Enter here."
adb -s "${TvIp}:5555" wait-for-device
adb -s "${TvIp}:5555" install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Install failed" }
adb -s "${TvIp}:5555" shell monkey -p com.rerun.tv -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Rerun is installed and starting on the TV."
