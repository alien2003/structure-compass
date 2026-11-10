# Downloads a private copy of mise (Windows x64) into the given folder.
# Used by dev.cmd only when mise is not on PATH. Nothing is installed globally.
param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
New-Item -ItemType Directory -Force $Destination | Out-Null
$release = Invoke-RestMethod -UseBasicParsing 'https://api.github.com/repos/jdx/mise/releases/latest'
$asset = $release.assets | Where-Object { $_.name -match '^mise-v[\d.]+-windows-x64\.zip$' } | Select-Object -First 1
if (-not $asset) { throw 'Could not find a windows-x64 mise asset in the latest release.' }
$tmp = Join-Path $Destination 'mise-download'
New-Item -ItemType Directory -Force $tmp | Out-Null
$zip = Join-Path $tmp $asset.name
Invoke-WebRequest -UseBasicParsing $asset.browser_download_url -OutFile $zip
Expand-Archive -Force $zip $tmp
$exe = Get-ChildItem -Recurse -Filter 'mise.exe' $tmp | Select-Object -First 1
Copy-Item $exe.FullName (Join-Path $Destination 'mise.exe') -Force
Remove-Item -Recurse -Force $tmp
Write-Host "[dev] mise installed to $Destination\mise.exe"
