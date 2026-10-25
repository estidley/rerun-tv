<#
  Finds a JDK 17+ for Gradle and keytool. Dot-source this file, then call Set-RerunJdk.
#>

function Set-RerunJdk {
    function Test-Jdk17([string]$JdkHome) {
        $java = Join-Path $JdkHome 'bin\java.exe'
        if (-not (Test-Path $java)) { return $false }
        # Java writes its version to stderr. Windows PowerShell 5.1 turns that
        # into a terminating error under Stop, even when Java exits successfully.
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $banner = & $java -version 2>&1 | Out-String
            $javaExitCode = $LASTEXITCODE
        }
        finally { $ErrorActionPreference = $previousErrorAction }
        if ($javaExitCode -ne 0) { return $false }
        if ($banner -notmatch 'version "(\d+)') { return $false }
        return [int]$Matches[1] -ge 17
    }
    if ($env:JAVA_HOME -and (Test-Jdk17 $env:JAVA_HOME)) { return }
    $jdkHomes = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    )
    foreach ($dir in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path $dir) { $jdkHomes += @(Get-ChildItem $dir -Directory | ForEach-Object FullName) }
    }
    $found = $jdkHomes | Where-Object { Test-Jdk17 $_ } | Select-Object -First 1
    if (-not $found) { throw "JDK 17 or newer is required. Set JAVA_HOME. The current one is '$env:JAVA_HOME'." }
    $env:JAVA_HOME = $found
    $env:Path = "$(Join-Path $found 'bin');$env:Path"
}
