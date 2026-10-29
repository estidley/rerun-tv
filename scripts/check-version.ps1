[CmdletBinding()]
param(
    [string]$BaseRef,
    [string]$TagName
)

$ErrorActionPreference = 'Stop'
. "$($PSScriptRoot)\version.ps1"
$root = Split-Path $PSScriptRoot -Parent
$versionPath = 'app/build.gradle.kts'
$current = Get-RerunVersion -Text (Get-Content -LiteralPath (Join-Path $root 'app\build.gradle.kts') -Raw)

if ($BaseRef) {
    $baselineText = git -C $root show "$($BaseRef):$($versionPath)"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read base version' }
    Assert-RerunVersion -Current $current -Baseline (Get-RerunVersion -Text ($baselineText -join "`n"))
}

$tags = git -C $root tag --list 'v*'
if ($LASTEXITCODE -ne 0) { throw 'Cannot list release tags' }
foreach ($tag in $tags) {
    if ($tag -notmatch '^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') { continue }
    $tagText = git -C $root show "$($tag):$($versionPath)"
    if ($LASTEXITCODE -ne 0) { throw "Cannot read version from $($tag)" }
    $released = Get-RerunVersion -Text ($tagText -join "`n")
    if ($tag -ne "v$($released.Name)") { throw "Tag $($tag) does not match its Android version" }
    Assert-RerunVersion -Current $current -Baseline $released
}
if ($TagName -and $TagName -ne "v$($current.Name)") {
    throw "Release tag must be v$($current.Name)"
}
Write-Output "Version valid: $($current.Name) ($($current.Code))"
