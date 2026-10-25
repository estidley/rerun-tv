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

  Optional: -TmdbToken refreshes the lists of what really aired on each network
  (see README, "Real network lists from TMDB"). Use the TMDB "API Read Access Token".
  It is only passed to the refresh step and is never saved; you can set TMDB_TOKEN
  in your shell instead. Without either, the last refresh is reused if there is one.

  Usage:  .\scripts\install.ps1 -TvIp 192.168.1.50
          .\scripts\install.ps1 -TvIp 192.168.1.50 -TmdbToken 'eyJhbGciOi...'
#>
param(
    [Parameter(Mandatory = $true)][string]$TvIp,
    [switch]$SkipBuild,
    [string]$TmdbToken
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

if (-not $SkipBuild) {
    & "$PSScriptRoot\keystore.ps1"
    if (-not $?) { throw 'Keystore setup failed' }
    $tmdbList = Join-Path $root 'local\tmdb\rerun\tmdb-networks.json'
    $token = if ($TmdbToken) { $TmdbToken } else { $env:TMDB_TOKEN }
    if ($token) {
        $previous = $env:TMDB_TOKEN
        $env:TMDB_TOKEN = $token
        try {
            & "$root\gradlew.bat" ':tmdb:run' --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'TMDB network refresh failed' }
        }
        finally { $env:TMDB_TOKEN = $previous }
    }
    elseif (Test-Path $tmdbList) {
        Write-Information 'Using the TMDB network lists from the last refresh.' -InformationAction Continue
    }
    elseif (Test-Path (Join-Path $root 'data\tmdb\rerun\tmdb-networks.json')) {
        Write-Information 'Using the shared TMDB network snapshot from the repository; no token needed.' -InformationAction Continue
    }
    else {
        Write-Warning 'No TMDB token, so channels use the network Plex reports. See README: Real network lists from TMDB.'
    }
    & "$root\gradlew.bat" ':app:assembleRelease' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
}

$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "APK not found at $($apk)" }

adb connect "$($TvIp):5555" | Write-Information -InformationAction Continue
if ($LASTEXITCODE -ne 0) { throw 'ADB connection failed' }
Write-Information "If the TV shows 'Allow USB debugging?', choose Always allow, then press Enter here." -InformationAction Continue
adb -s "$($TvIp):5555" wait-for-device
if ($LASTEXITCODE -ne 0) { throw 'Waiting for the TV failed' }
adb -s "$($TvIp):5555" install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
adb -s "$($TvIp):5555" shell monkey -p com.rerun.tv -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Starting Rerun failed' }
Write-Information 'Rerun is installed and starting on the TV.' -InformationAction Continue
