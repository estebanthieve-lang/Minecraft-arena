param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [Parameter(Mandatory = $true)]
  [string]$PlayerName
)

$ErrorActionPreference = "Stop"

if (-not $Root -or [string]::IsNullOrWhiteSpace($Root)) {
  $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

Add-Type -AssemblyName System.Security

function Get-OfflineUuid([string]$name) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$name")
  $md5 = [System.Security.Cryptography.MD5]::Create()
  $hash = $md5.ComputeHash($bytes)
  $hash[6] = ($hash[6] -band 0x0F) -bor 0x30
  $hash[8] = ($hash[8] -band 0x3F) -bor 0x80
  return [Guid]::new($hash).ToString()
}

$rootPath = [System.IO.Path]::GetFullPath($Root)
$opsPath = Join-Path $rootPath "server\ops.json"
New-Item -ItemType Directory -Force -Path (Split-Path $opsPath -Parent) | Out-Null

$ops = @()
if ((Test-Path -LiteralPath $opsPath) -and (Get-Content -Raw -LiteralPath $opsPath).Trim()) {
  $ops = @(Get-Content -Raw -LiteralPath $opsPath | ConvertFrom-Json)
}

$ops = @($ops | Where-Object { ([string]$_.name).ToLowerInvariant() -ne $PlayerName.ToLowerInvariant() })
$ops += [pscustomobject]@{
  uuid = Get-OfflineUuid $PlayerName
  name = $PlayerName
  level = 4
  bypassesPlayerLimit = $true
}

$ops | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $opsPath -Encoding UTF8
Write-Host "OP agregado: $PlayerName" -ForegroundColor Green
Write-Host "Archivo: $opsPath"
