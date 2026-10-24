$ErrorActionPreference = 'Stop'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PSScriptRoot 'keystore.ps1'), [ref]$tokens, [ref]$parseErrors
)
if ($parseErrors.Count -gt 0) { throw 'Keystore script did not parse' }

# Load only the encoder; running setup here would touch the real signing key.
$encoder = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'ConvertTo-JavaProperty'
}, $true)
if ($null -eq $encoder) { throw 'Java property encoder was not found' }
. ([scriptblock]::Create($encoder.Extent.Text))

$cases = @(
    @{ Value = '  sample value\'; Expected = '\ \ sample\ value\\' }
    @{ Value = "a`t`n`rb"; Expected = 'a\t\n\rb' }
    @{ Value = [string][char]0x00e9; Expected = '\u00e9' }
    @{ Value = '#:=!'; Expected = '#:=!' }
    @{ Value = ''; Expected = '' }
)
foreach ($case in $cases) {
    if ((ConvertTo-JavaProperty $case.Value) -cne $case.Expected) {
        throw 'Java property escaping regression'
    }
}
Write-Information 'Keystore property encoding checks passed.' -InformationAction Continue
