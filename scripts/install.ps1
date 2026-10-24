<#
  Build Rerun and install it on a Fire TV over Wi-Fi.

  One-time on the TV: Settings > My Fire TV > About > click the device name 7 times
  to unlock Developer Options, then Developer Options > ADB Debugging: ON.
  Find the TV's IP under Settings > My Fire TV > About > Network.

  The first build creates a private release key. The script asks for a password,
  or generates a random one when it cannot prompt. rerun-release.jks and
  keystore.properties stay on this machine and out of the repo. Back them up:
  a later install signed with a different key will not update the app, and
  saved Plex tokens will not carry over.

  Usage:  .\scripts\install.ps1 -TvIp 192.168.1.50
#>
param(
    [Parameter(Mandatory = $true)][string]$TvIp,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. "$PSScriptRoot\jdk.ps1"
Set-RerunJdk

if (-not $env:ANDROID_HOME -and -not (Test-Path "$root\local.properties")) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $sdk) { "sdk.dir=$($sdk -replace '\\','\\')" | Set-Content "$root\local.properties" -Encoding ASCII }
    else { throw "Android SDK not found. Install it or set ANDROID_HOME." }
}

if (-not $SkipBuild) {
    & "$PSScriptRoot\keystore.ps1"
    if (-not $?) { throw "Keystore setup failed" }
    & "$root\gradlew.bat" ':app:assembleRelease' --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
}

$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "APK not found at $apk" }

adb connect "${TvIp}:5555" | Write-Host
Write-Host "If the TV shows 'Allow USB debugging?', choose Always allow, then press Enter here."
adb -s "${TvIp}:5555" wait-for-device
adb -s "${TvIp}:5555" install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Install failed" }
adb -s "${TvIp}:5555" shell monkey -p com.rerun.tv -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Rerun is installed and starting on the TV."
