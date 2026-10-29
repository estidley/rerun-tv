function Get-RerunVersion {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Text)

    $codes = [regex]::Matches($Text, '(?m)^\s*versionCode = (\d+)\s*$')
    $names = [regex]::Matches($Text, '(?m)^\s*versionName = "(\d+\.\d+\.\d+)"\s*$')
    if ($codes.Count -ne 1 -or $names.Count -ne 1) {
        throw 'Expected exactly one literal versionCode and major.minor.patch versionName'
    }
    $code = [long]::Parse($codes[0].Groups[1].Value)
    if ($code -lt 1 -or $code -gt 2100000000) { throw 'versionCode is outside the Android range' }
    $name = $names[0].Groups[1].Value
    if ($name -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
        throw 'Version components must not have leading zeros'
    }
    [pscustomobject]@{ Code = $code; Name = $name; Value = [version]$name }
}

function Assert-RerunVersion {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Current,
        [Parameter(Mandatory)]$Baseline
    )

    if ($Current.Code -eq $Baseline.Code -and $Current.Name -eq $Baseline.Name) { return }
    if ($Current.Code -gt $Baseline.Code -and $Current.Value -gt $Baseline.Value) {
        return
    }
    throw "Both versionCode and versionName must increase together from $($Baseline.Name) ($($Baseline.Code))"
}

function Get-RerunBumpedVersion {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][ValidateSet('patch', 'minor', 'major')][string]$Bump
    )

    $current = Get-RerunVersion -Text $Text
    $major = $current.Value.Major
    $minor = $current.Value.Minor
    $patch = $current.Value.Build
    switch ($Bump) {
        'major' { $major++; $minor = 0; $patch = 0 }
        'minor' { $minor++; $patch = 0 }
        'patch' { $patch++ }
    }
    $name = "$($major).$($minor).$($patch)"
    $result = [regex]::Replace($Text, '(?m)^(\s*versionCode = )\d+(\s*)$', "`${1}$($current.Code + 1)`${2}")
    $result = [regex]::Replace($result, '(?m)^(\s*versionName = )"[\d.]+"(\s*)$', "`${1}`"$($name)`"`${2}")
    $next = Get-RerunVersion -Text $result
    Assert-RerunVersion -Current $next -Baseline $current
    $result
}
