[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$($PSScriptRoot)\version.ps1"

function Assert-Rejected {
    param([scriptblock]$Action)
    try { & $Action } catch { return }
    throw 'Expected version validation to reject input'
}

$text = "versionCode = 3`nversionName = `"1.0.1`"`n"
$current = Get-RerunVersion -Text $text
Assert-RerunVersion -Current $current -Baseline $current
foreach ($case in @(
    @{ Bump = 'patch'; Name = '1.0.2' },
    @{ Bump = 'minor'; Name = '1.1.0' },
    @{ Bump = 'major'; Name = '2.0.0' }
)) {
    $next = Get-RerunVersion -Text (Get-RerunBumpedVersion -Text $text -Bump $case.Bump)
    if ($next.Code -ne 4 -or $next.Name -ne $case.Name) { throw 'Incorrect bump' }
    Assert-RerunVersion -Current $next -Baseline $current
    Assert-Rejected { Assert-RerunVersion -Current $current -Baseline $next }
}
foreach ($invalid in @(
    'versionCode = 0', 'versionCode = 2100000001', 'versionName = "01.0.1"',
    'versionName = "1.0"', 'versionName = "1.0.1-beta"'
)) {
    if ($invalid.StartsWith('versionCode')) {
        $bad = $text -replace 'versionCode = 3', $invalid
    } else {
        $bad = $text -replace 'versionName = "1.0.1"', $invalid
    }
    Assert-Rejected { Get-RerunVersion -Text $bad }
}
Assert-Rejected { Get-RerunVersion -Text ($text + $text) }
Assert-Rejected { Get-RerunVersion -Text '' }
foreach ($bad in @(
    ($text -replace 'versionCode = 3', 'versionCode = 4'),
    ($text -replace '1.0.1', '1.0.2')
)) {
    $candidate = Get-RerunVersion -Text $bad
    Assert-Rejected { Assert-RerunVersion -Current $candidate -Baseline $current }
}
Assert-Rejected {
    Get-RerunBumpedVersion -Text ($text -replace 'versionCode = 3', 'versionCode = 2100000000') -Bump patch
}

$fixture = Join-Path ([IO.Path]::GetTempPath()) "rerun-version-$([guid]::NewGuid())"
try {
    $app = New-Item -ItemType Directory -Path (Join-Path $fixture 'app') -Force
    $scripts = New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts')
    Copy-Item -LiteralPath "$($PSScriptRoot)\version.ps1", "$($PSScriptRoot)\check-version.ps1" -Destination $scripts.FullName
    $build = Join-Path $app.FullName 'build.gradle.kts'
    [IO.File]::WriteAllText($build, $text)
    git -C $fixture init --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Fixture init failed' }
    git -C $fixture add .
    if ($LASTEXITCODE -ne 0) { throw 'Fixture stage failed' }
    git -C $fixture -c user.name=VersionTest -c user.email=version@example.invalid -c commit.gpgsign=false commit --quiet -m 'Version fixture'
    if ($LASTEXITCODE -ne 0) { throw 'Fixture commit failed' }
    git -C $fixture -c tag.gpgsign=false tag v1.0.1
    if ($LASTEXITCODE -ne 0) { throw 'Fixture tag failed' }
    $check = Join-Path $scripts.FullName 'check-version.ps1'
    & $check -BaseRef HEAD -TagName v1.0.1
    Assert-Rejected { & $check -TagName v1.0.2 }
    [IO.File]::WriteAllText($build, (Get-RerunBumpedVersion -Text $text -Bump patch))
    & $check -BaseRef HEAD -TagName v1.0.2
    [IO.File]::WriteAllText($build, ($text -replace '1.0.1', '1.0.0'))
    Assert-Rejected { & $check }
    [IO.File]::WriteAllText($build, $text)
    git -C $fixture -c tag.gpgsign=false tag v1.0.2
    if ($LASTEXITCODE -ne 0) { throw 'Mismatched fixture tag failed' }
    Assert-Rejected { & $check }
} finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}
Write-Output 'Version parsing, bumps, bounds, git baselines and release tags passed'
