<#
  Package Rerun as a Tizen .wgt and install it on a Samsung Smart TV over Wi-Fi.

  One-time on the Samsung TV:
    1. Open the Apps screen on the TV.
    2. Press 1-2-3-4-5 on the remote (or enter 12345 on the on-screen number pad).
    3. Toggle Developer Mode to ON and enter your PC's IP address (Host PC IP).
    4. Reboot the TV (hold the Power button on the remote for 5 seconds).

  Usage:
    .\scripts\install-samsung.ps1 -TvIp 10.95.76.xxx
#>
param(
    [Parameter(Mandatory = $false)][string]$TvIp,
    [string]$CertProfile = "",
    [switch]$PackageOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tizenDir = Join-Path $root 'tizen'
$outDir = Join-Path $root 'build\tizen'
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

# Locate Tizen Studio CLI & SDB if installed
$tizenCandidates = @(
    (Get-Command tizen.bat -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
    "$env:USERPROFILE\tizen-studio\tools\ide\bin\tizen.bat",
    "C:\tizen-studio\tools\ide\bin\tizen.bat"
) | Where-Object { $_ -and (Test-Path $_) }

$sdbCandidates = @(
    (Get-Command sdb.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
    "$env:USERPROFILE\tizen-studio\tools\sdb.exe",
    "C:\tizen-studio\tools\sdb.exe"
) | Where-Object { $_ -and (Test-Path $_) }

$wgtPath = Join-Path $outDir 'Rerun.wgt'
if (Test-Path $wgtPath) { Remove-Item $wgtPath -Force }

if ($tizenCandidates.Count -gt 0) {
    $tizenCli = $tizenCandidates[0]
    Write-Host "Packaging Rerun.wgt using Tizen CLI ($tizenCli)..."
    if ($CertProfile) {
        & $tizenCli package -t wgt -s $CertProfile -o $outDir -- $tizenDir
    } else {
        & $tizenCli package -t wgt -o $outDir -- $tizenDir
    }
} else {
    Write-Host "Packaging Rerun.wgt archive..."
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tizenDir, $wgtPath)
}

Write-Host "Created package: $wgtPath"
if ($PackageOnly -or -not $TvIp) {
    if (-not $TvIp) {
        Write-Host "Tip: Pass -TvIp <Samsung-TV-IP> to install over Wi-Fi, or run .\scripts\serve-samsung.ps1 to open immediately in the Samsung TV browser."
    }
    return
}

if ($sdbCandidates.Count -eq 0 -or $tizenCandidates.Count -eq 0) {
    throw "Tizen Studio CLI (sdb.exe / tizen.bat) was not found. Install Tizen Studio CLI to push .wgt packages, or run .\scripts\serve-samsung.ps1 to use Rerun immediately in the Samsung TV web browser."
}

$sdb = $sdbCandidates[0]
$tizen = $tizenCandidates[0]

& $sdb connect "${TvIp}:26101" | Write-Host
$devices = & $sdb devices
$targetLine = ($devices | Select-String -Pattern "${TvIp}:26101").Line
if (-not $targetLine) {
    throw "Could not connect to Samsung TV at ${TvIp}:26101. Verify Developer Mode is ON and Host PC IP is set on the TV."
}
$targetName = ($targetLine -split '\s+')[-1]

& $tizen install -n $wgtPath -t $targetName
if ($LASTEXITCODE -ne 0) { throw "Tizen install failed" }

& $tizen run -p RerunTV001.Rerun -t $targetName
Write-Host "Rerun is installed and starting on the Samsung TV."
