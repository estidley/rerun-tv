<#
  Creates this machine's release key the first time it is needed.

  rerun-release.jks and keystore.properties are gitignored. They are created
  together and never replaced while either one still exists. The password is
  whatever you type, or a random one when this script cannot prompt. Nothing
  in the repo is a password.

  Usage:  .\scripts\keystore.ps1
#>
param(
    [string]$Password
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\jdk.ps1"
Set-RerunJdk

$root = Split-Path -Parent $PSScriptRoot
$jks = Join-Path $root 'rerun-release.jks'
$propsPath = Join-Path $root 'keystore.properties'
$hasJks = Test-Path -LiteralPath $jks
$hasProps = Test-Path -LiteralPath $propsPath
if ($hasJks -and $hasProps) {
    Write-Host "Using the existing release keystore."
    return
}
if ($hasJks -xor $hasProps) {
    throw @"
The release key is incomplete: rerun-release.jks and keystore.properties must exist together.
Restore both from backup, or delete both and run this script again.
Deleting them creates a new signing key. The TV will refuse that as an update, and saved Plex tokens will not carry over.
"@
}

function New-RerunPassword {
    $alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'.ToCharArray()
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $chars = New-Object char[] 32
        $buf = New-Object byte[] 1
        $limit = 256 - (256 % $alphabet.Length)
        for ($i = 0; $i -lt $chars.Length; ) {
            $rng.GetBytes($buf)
            $b = [int]$buf[0]
            if ($b -lt $limit) {
                $chars[$i] = $alphabet[$b % $alphabet.Length]
                $i++
            }
        }
        return -join $chars
    } finally {
        $rng.Dispose()
    }
}

function Read-RerunPassword {
    if (-not [string]::IsNullOrEmpty($Password)) { return $Password }
    $interactive = $false
    try { $interactive = -not [Console]::IsInputRedirected } catch { $interactive = $false }
    if (-not $interactive) { return $null }
    try {
        Write-Host "Release keystore password. Press Enter to generate a random one."
        $secure = Read-Host -AsSecureString
        if ($null -eq $secure -or $secure.Length -eq 0) { return $null }
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally {
            if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
        }
    } catch {
        return $null
    }
}

function ConvertTo-JavaProperty([string]$Value) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($c in $Value.ToCharArray()) {
        $code = [int]$c
        if ($c -eq '\') { [void]$sb.Append('\\'); continue }
        if ($c -eq "`n") { [void]$sb.Append('\n'); continue }
        if ($c -eq "`r") { [void]$sb.Append('\r'); continue }
        if ($c -eq "`t") { [void]$sb.Append('\t'); continue }
        if ($code -lt 0x20 -or $code -gt 0x7E) {
            [void]$sb.Append(('\u{0:x4}' -f $code))
            continue
        }
        [void]$sb.Append($c)
    }
    return $sb.ToString()
}

$chosen = Read-RerunPassword
$generated = $null -eq $chosen
$plain = if ($generated) { New-RerunPassword } else { $chosen }
if ([string]::IsNullOrEmpty($plain)) { throw "No release keystore password was provided." }

$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw "keytool was not found under JAVA_HOME ($env:JAVA_HOME)." }

$wrote = $false
$env:RERUN_KEYSTORE_PASS = $plain
try {
    & $keytool @(
        '-genkeypair',
        '-keystore', $jks,
        '-storetype', 'PKCS12',
        '-alias', 'rerun',
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '10000',
        '-dname', 'CN=Rerun',
        '-storepass:env', 'RERUN_KEYSTORE_PASS',
        '-keypass:env', 'RERUN_KEYSTORE_PASS'
    )
    if ($LASTEXITCODE -ne 0) { throw "keytool could not create the release keystore." }
    $text = @(
        "storeFile=$(ConvertTo-JavaProperty 'rerun-release.jks')"
        "storePassword=$(ConvertTo-JavaProperty $plain)"
        "keyAlias=$(ConvertTo-JavaProperty 'rerun')"
        "keyPassword=$(ConvertTo-JavaProperty $plain)"
    ) -join "`n"
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($propsPath, $text + "`n", $utf8)
    $wrote = $true
} finally {
    Remove-Item Env:RERUN_KEYSTORE_PASS -ErrorAction SilentlyContinue
    if (-not $wrote) {
        Remove-Item -LiteralPath $jks, $propsPath -Force -ErrorAction SilentlyContinue
    }
}

if ($generated) {
    Write-Host "Generated a random release-keystore password."
} else {
    Write-Host "Created the release keystore."
}
Write-Host "The password is only in keystore.properties. That file and rerun-release.jks are gitignored. Back both up."
