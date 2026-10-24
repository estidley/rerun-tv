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
    [switch]$SkipBuild,
    [switch]$DebugBuild,
    [switch]$ReinstallOnKeyMismatch
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. "$PSScriptRoot\jdk.ps1"
Set-RerunJdk

if (-not $env:ANDROID_HOME -and -not (Test-Path "$root\local.properties")) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $sdk) { "sdk.dir=$($sdk -replace '\\','\\')" | Set-Content "$root\local.properties" -Encoding ASCII }
    else { throw 'Android SDK not found. Install it or set ANDROID_HOME.' }
}

$sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$adbCmd = if (Test-Path $sdkAdb) { $sdkAdb } else { 'adb' }

if (-not $SkipBuild) {
    if ($DebugBuild) {
        & "$root\gradlew.bat" ':app:assembleDebug' --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    } else {
        & "$PSScriptRoot\keystore.ps1"
        if (-not $?) { throw 'Keystore setup failed' }
        & "$root\gradlew.bat" ':app:assembleRelease' --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    }
}

$apkRel = if ($DebugBuild) { 'app\build\outputs\apk\debug\app-debug.apk' } else { 'app\build\outputs\apk\release\app-release.apk' }
$apk = Join-Path $root $apkRel
if (-not (Test-Path $apk)) { throw "APK not found at $($apk)" }

& $adbCmd connect "$($TvIp):5555" | Write-Information -InformationAction Continue
if ($LASTEXITCODE -ne 0) { throw 'ADB connection failed' }
Write-Information "If the TV shows 'Allow USB debugging?', choose Always allow, then press Enter here." -InformationAction Continue
& $adbCmd -s "$($TvIp):5555" wait-for-device
if ($LASTEXITCODE -ne 0) { throw 'Waiting for the TV failed' }
$installOut = & $adbCmd -s "$($TvIp):5555" install -r $apk 2>&1 | Out-String
Write-Information $installOut.Trim() -InformationAction Continue
if ($LASTEXITCODE -ne 0 -or $installOut -match 'INSTALL_FAILED_') {
    if ($installOut -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        if ($ReinstallOnKeyMismatch) {
            Write-Information 'Signing key changed; uninstalling existing app and reinstalling (-ReinstallOnKeyMismatch)...' -InformationAction Continue
            & $adbCmd -s "$($TvIp):5555" uninstall com.rerun.tv | Out-Null
            & $adbCmd -s "$($TvIp):5555" install $apk
            if ($LASTEXITCODE -ne 0) { throw 'Reinstall failed' }
        } else {
            throw "Install failed due to signing key mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE). The TV has a build signed with a different key (e.g. debug vs. release). Pass -DebugBuild to keep the debug signature and preserve your Plex sign-in, or pass -ReinstallOnKeyMismatch to uninstall and replace it (requires signing into Plex again)."
        }
    } else {
        throw 'Install failed'
    }
}
& $adbCmd -s "$($TvIp):5555" shell monkey -p com.rerun.tv -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Starting Rerun failed' }
Write-Information 'Rerun is installed and starting on the TV.' -InformationAction Continue
