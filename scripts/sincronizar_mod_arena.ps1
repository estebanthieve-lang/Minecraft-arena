param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

if (-not $Root -or [string]::IsNullOrWhiteSpace($Root)) {
  $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-GamePath([string]$root, [string]$path) {
  $expanded = $path.Replace("%APPDATA%", $env:APPDATA).Replace("/", "\")
  if ([System.IO.Path]::IsPathRooted($expanded)) {
    return [System.IO.Path]::GetFullPath($expanded)
  }
  return [System.IO.Path]::GetFullPath((Join-Path $root $expanded))
}

$rootPath = [System.IO.Path]::GetFullPath($Root)
$configPath = Join-Path $rootPath "config\arena_server.json"
if (-not (Test-Path -LiteralPath $configPath)) {
  throw "Falta config/arena_server.json"
}

$config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$latestFolder = Resolve-GamePath $rootPath ([string]$config.mod.latestJarFolder)
$packagedModsFolder = Join-Path $rootPath "mods"
$pattern = [string]$config.mod.jarPattern
$isDevTree = Test-Path -LiteralPath (Join-Path $rootPath "game\mod-src")
$candidateFolders = if ($isDevTree) { @($latestFolder, $packagedModsFolder) } else { @($packagedModsFolder, $latestFolder) }
$latestJar = $null
foreach ($folder in $candidateFolders) {
  if (-not $folder -or -not (Test-Path -LiteralPath $folder)) { continue }
  $latestJar = Get-ChildItem -LiteralPath $folder -Filter $pattern -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if ($latestJar) { break }
}

if (-not $latestJar) {
  throw "No hay mod disponible en $latestFolder ni en mods con patron $pattern"
}

Write-Host "Mod nuevo:" -ForegroundColor Cyan
Write-Host "  $($latestJar.FullName)"

foreach ($target in @($config.mod.installTargets)) {
  $targetPath = Resolve-GamePath $rootPath ([string]$target)
  New-Item -ItemType Directory -Force -Path $targetPath | Out-Null

  if ([System.IO.Path]::GetFullPath($targetPath) -eq [System.IO.Path]::GetFullPath($latestJar.DirectoryName)) {
    Write-Host "Ya presente en: $targetPath" -ForegroundColor Green
    continue
  }

  Get-ChildItem -LiteralPath $targetPath -Filter $pattern -File -ErrorAction SilentlyContinue |
    Remove-Item -Force

  Copy-Item -LiteralPath $latestJar.FullName -Destination (Join-Path $targetPath $latestJar.Name) -Force
  Write-Host "Instalado en: $targetPath" -ForegroundColor Green
}
