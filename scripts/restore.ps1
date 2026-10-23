<#
  Remove Rerun from the Fire TV.
  Usage:  .\scripts\restore.ps1 -TvIp 192.168.1.50
#>
param([Parameter(Mandatory = $true)][string]$TvIp)
adb connect "${TvIp}:5555" | Write-Host
adb -s "${TvIp}:5555" uninstall com.rerun.tv
