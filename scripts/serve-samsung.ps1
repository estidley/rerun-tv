<#
  Zero-install local web server for Samsung Smart TVs.
  Serves the Tizen web app on your LAN so you can open it immediately in the
  Samsung TV's built-in Internet Browser (or bookmark/pin it to the Samsung Home bar)
  without needing Tizen Studio or Developer Mode.

  Usage:
    .\scripts\serve-samsung.ps1 -Port 8090
#>
param(
    [int]$Port = 8090
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tizenDir = Join-Path $root 'tizen'

$lanIp = (Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.InterfaceAlias -notmatch 'WSL|vEthernet|Tailscale' } |
    Select-Object -First 1 -ExpandProperty IPAddress)

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://+:$Port/")
try {
    $listener.Start()
} catch {
    $listener = [System.Net.HttpListener]::new()
    $listener.Prefixes.Add("http://localhost:$Port/")
    if ($lanIp) { $listener.Prefixes.Add("http://${lanIp}:$Port/") }
    $listener.Start()
}

Write-Host "========================================================" -ForegroundColor Green
Write-Host " Rerun TV (Samsung / Tizen Web Server)" -ForegroundColor Green
Write-Host " Open on Samsung TV Internet Browser: http://${lanIp}:$Port" -ForegroundColor Yellow
Write-Host " Open locally on PC:                  http://localhost:$Port" -ForegroundColor Cyan
Write-Host " Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host "========================================================" -ForegroundColor Green

$mimeTypes = @{
    '.html' = 'text/html; charset=utf-8'
    '.css'  = 'text/css; charset=utf-8'
    '.js'   = 'application/javascript; charset=utf-8'
    '.json' = 'application/json; charset=utf-8'
    '.png'  = 'image/png'
    '.ttf'  = 'font/ttf'
    '.xml'  = 'application/xml; charset=utf-8'
}

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response

        $relPath = $req.Url.LocalPath.TrimStart('/')
        if ([string]::IsNullOrWhiteSpace($relPath)) { $relPath = 'index.html' }
        $filePath = Join-Path $tizenDir ($relPath -replace '/', '\')

        if (Test-Path $filePath -PathType Leaf) {
            $ext = [System.IO.Path]::GetExtension($filePath).ToLowerInvariant()
            $res.ContentType = if ($mimeTypes.ContainsKey($ext)) { $mimeTypes[$ext] } else { 'application/octet-stream' }
            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            $res.ContentLength64 = $bytes.Length
            $res.StatusCode = 200
            $res.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $res.StatusCode = 404
        }
        $res.OutputStream.Close()
    }
} finally {
    $listener.Stop()
}
