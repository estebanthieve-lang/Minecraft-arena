param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$SkipForgeInstall
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath([string]$path) {
  return [System.IO.Path]::GetFullPath($path)
}

function Expand-GamePath([string]$path) {
  return $path.Replace("%APPDATA%", $env:APPDATA).Replace("%LOCALAPPDATA%", $env:LOCALAPPDATA)
}

function Copy-DirectoryMerge([string]$source, [string]$target, [string]$filter = "*") {
  if (-not (Test-Path -LiteralPath $source)) { return }
  New-Item -ItemType Directory -Force -Path $target | Out-Null
  & robocopy $source $target $filter /E /NFL /NDL /NJH /NJS /NP | Out-Null
  if ($LASTEXITCODE -gt 7) {
    throw "No pude copiar $source hacia $target. Robocopy=$LASTEXITCODE"
  }
}

function Sync-JarDirectory([string]$source, [string]$target) {
  if (-not (Test-Path -LiteralPath $source)) {
    throw "Falta carpeta de mods del paquete: $source"
  }
  New-Item -ItemType Directory -Force -Path $target | Out-Null

  $sourceJars = @(Get-ChildItem -LiteralPath $source -File -Filter "*.jar")
  if ($sourceJars.Count -eq 0) {
    throw "La carpeta de mods del paquete no trae jars: $source"
  }

  $expected = @{}
  foreach ($jar in $sourceJars) {
    $expected[$jar.Name] = $true
  }

  Get-ChildItem -LiteralPath $target -File -Filter "*.jar" | ForEach-Object {
    if (-not $expected.ContainsKey($_.Name)) {
      Remove-Item -LiteralPath $_.FullName -Force
    }
  }

  foreach ($jar in $sourceJars) {
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $target $jar.Name) -Force
  }
}

function Copy-DirectoryMissingOnly([string]$source, [string]$target) {
  if (-not (Test-Path -LiteralPath $source)) { return }
  New-Item -ItemType Directory -Force -Path $target | Out-Null
  Get-ChildItem -LiteralPath $source -Recurse | ForEach-Object {
    $relative = $_.FullName.Substring($source.Length).TrimStart("\")
    if (-not $relative) { return }
    $destination = Join-Path $target $relative
    if ($_.PSIsContainer) {
      New-Item -ItemType Directory -Force -Path $destination | Out-Null
      return
    }
    if (-not (Test-Path -LiteralPath $destination)) {
      New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
      Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
    }
  }
}

function Get-JavaMajorVersion([string]$javaPath) {
  if (-not $javaPath) { return 0 }
  if (-not (Test-Path -LiteralPath $javaPath)) { return 0 }

  $previousErrorActionPreference = $ErrorActionPreference
  try {
    # java -version writes to stderr even when it succeeds.
    $ErrorActionPreference = "Continue"
    $versionText = (& $javaPath -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { return 0 }

    if ($versionText -match 'version\s+"1\.(\d+)') {
      return [int]$Matches[1]
    }

    if ($versionText -match 'version\s+"(\d+)') {
      return [int]$Matches[1]
    }
  } catch {
    return 0
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }

  return 0
}

function Add-JavaCandidatesFromGlob([System.Collections.Generic.List[string]]$candidates, [string]$pattern) {
  if (-not $pattern) { return }
  Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue | ForEach-Object {
    if (-not $candidates.Contains($_.FullName)) {
      $candidates.Add($_.FullName)
    }
  }
}

function Find-JavaCommand([string]$rootPath) {
  $candidates = [System.Collections.Generic.List[string]]::new()

  $portableJava = Join-Path $rootPath "tools\java\bin\java.exe"
  if (Test-Path -LiteralPath $portableJava) { $candidates.Add($portableJava) }

  if ($env:JAVA_HOME) {
    $javaHomeCandidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if ((Test-Path -LiteralPath $javaHomeCandidate) -and (-not $candidates.Contains($javaHomeCandidate))) {
      $candidates.Add($javaHomeCandidate)
    }
  }

  $pathJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
  if ($pathJava -and (-not $candidates.Contains($pathJava.Source))) { $candidates.Add($pathJava.Source) }

  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:APPDATA ".minecraft\runtime\*\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:APPDATA ".minecraft\runtime\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:APPDATA ".tlauncher\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:APPDATA ".tlauncher\*\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:LOCALAPPDATA "Programs\TLauncher\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:LOCALAPPDATA "Programs\TLauncher\*\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:ProgramFiles "Java\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path ${env:ProgramFiles(x86)} "Java\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:ProgramFiles "Eclipse Adoptium\*\bin\java.exe")
  Add-JavaCandidatesFromGlob $candidates (Join-Path $env:ProgramFiles "Microsoft\jdk-*\bin\java.exe")

  $validCandidates = foreach ($candidate in $candidates) {
    $major = Get-JavaMajorVersion $candidate
    if ($major -ge 17) {
      [pscustomobject]@{
        Path = $candidate
        Major = $major
      }
    }
  }

  $selected = $validCandidates | Sort-Object Major -Descending | Select-Object -First 1
  if ($selected) {
    Write-Host "Using Java $($selected.Major): $($selected.Path)"
    return $selected.Path
  }

  return ""
}

function Ensure-ForgeClientVersion([string]$rootPath, [string]$mcRoot, [string]$mcVersion, [string]$forgeVersion, [bool]$skipInstall) {
  $forgeFull = "$mcVersion-$forgeVersion"
  $versionId = "$mcVersion-forge-$forgeVersion"
  $versionDir = Join-Path (Join-Path $mcRoot "versions") $versionId
  $versionJson = Join-Path $versionDir "$versionId.json"

  if (Test-Path -LiteralPath $versionJson) {
    return @{
      ok = $true
      installed = $true
      versionId = $versionId
      message = "Forge client version already exists."
    }
  }

  if ($skipInstall) {
    return @{
      ok = $false
      installed = $false
      versionId = $versionId
      message = "Forge client version is missing."
    }
  }

  $javaCommand = Find-JavaCommand $rootPath
  if (-not $javaCommand) {
    return @{
      ok = $false
      installed = $false
      versionId = $versionId
      message = "Java 17 or newer is required to install Forge client."
    }
  }

  $installerName = "forge-$forgeFull-installer.jar"
  $installerDir = Join-Path $rootPath "tools\forge"
  $installerPath = Join-Path $installerDir $installerName
  $installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$forgeFull/$installerName"

  New-Item -ItemType Directory -Force -Path $installerDir | Out-Null
  if (-not (Test-Path -LiteralPath $installerPath)) {
    Write-Host "Downloading Forge installer: $installerUrl"
    Invoke-WebRequest -UseBasicParsing -Uri $installerUrl -OutFile $installerPath
  }

  Write-Host "Installing Forge client version: $versionId"
  Push-Location $mcRoot
  try {
    & $javaCommand -jar $installerPath --installClient
    if ($LASTEXITCODE -ne 0) {
      throw "Forge installer returned $LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }

  if (-not (Test-Path -LiteralPath $versionJson)) {
    return @{
      ok = $false
      installed = $false
      versionId = $versionId
      message = "Forge installer finished but the version was not created."
    }
  }

  return @{
    ok = $true
    installed = $true
    versionId = $versionId
    message = "Forge client version installed."
  }
}

function Upsert-OfficialLauncherProfile([string]$mcRoot, [string]$profileId, [string]$profileName, [string]$versionId, [string]$gameDir) {
  $profilesPath = Join-Path $mcRoot "launcher_profiles.json"
  $now = (Get-Date).ToUniversalTime().ToString("o")
  $profileFileExists = Test-Path -LiteralPath $profilesPath

  if (-not $profileFileExists) {
    '{"profiles":{}}' | Set-Content -LiteralPath $profilesPath -Encoding UTF8
  }

  $raw = Get-Content -Raw -LiteralPath $profilesPath
  if ($profileFileExists -and [string]::IsNullOrWhiteSpace($raw)) {
    $backupPath = "$profilesPath.empty-before-tiktok-live-$(Get-Date -Format yyyyMMddHHmmss)"
    Copy-Item -LiteralPath $profilesPath -Destination $backupPath -Force
    throw "launcher_profiles.json esta vacio. No lo sobrescribo para proteger otros perfiles; cierra Minecraft Launcher/TLauncher y vuelve a preparar."
  }

  try {
    $json = $raw | ConvertFrom-Json
  } catch {
    $backupPath = "$profilesPath.invalid-before-tiktok-live-$(Get-Date -Format yyyyMMddHHmmss)"
    Copy-Item -LiteralPath $profilesPath -Destination $backupPath -Force
    throw "launcher_profiles.json no es JSON valido. No lo sobrescribo para proteger otros perfiles; cierra Minecraft Launcher/TLauncher y vuelve a preparar."
  }

  if (-not $json.profiles) {
    if ($profileFileExists) {
      $backupPath = "$profilesPath.missing-profiles-before-tiktok-live-$(Get-Date -Format yyyyMMddHHmmss)"
      Copy-Item -LiteralPath $profilesPath -Destination $backupPath -Force
      throw "launcher_profiles.json no trae profiles. No lo sobrescribo para proteger otros perfiles; cierra Minecraft Launcher/TLauncher y vuelve a preparar."
    }
    $json | Add-Member -MemberType NoteProperty -Name profiles -Value ([pscustomobject]@{})
  }

  $existing = $json.profiles.PSObject.Properties[$profileId]
  $needsBackup = -not $existing
  if ($existing) {
    $value = $existing.Value
    $needsBackup = ($value.lastVersionId -ne $versionId) -or ($value.gameDir -ne $gameDir) -or ($value.name -ne $profileName)
  }

  if ($needsBackup) {
    $backupPath = "$profilesPath.before-tiktok-live-$(Get-Date -Format yyyyMMddHHmmss)"
    Copy-Item -LiteralPath $profilesPath -Destination $backupPath -Force
  }

  $profile = [pscustomobject]@{
    name = $profileName
    type = "custom"
    created = if ($existing -and $existing.Value.created) { $existing.Value.created } else { $now }
    lastUsed = $now
    lastVersionId = $versionId
    gameDir = $gameDir
    icon = "Furnace"
    javaArgs = "-Xms2G -Xmx4G"
  }

  $json.profiles | Add-Member -MemberType NoteProperty -Name $profileId -Value $profile -Force
  $json | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $profilesPath -Encoding UTF8
}

$rootPath = Resolve-FullPath $Root
$configPath = Join-Path $rootPath "game.config.json"
if (-not (Test-Path -LiteralPath $configPath)) {
  throw "Missing game.config.json"
}

$gameConfig = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$serverConfigPath = Join-Path $rootPath "config\arena_server.json"
$serverConfig = if (Test-Path -LiteralPath $serverConfigPath) { Get-Content -Raw -LiteralPath $serverConfigPath | ConvertFrom-Json } else { $null }

$mcRoot = Resolve-FullPath (Join-Path $env:APPDATA ".minecraft")
$instancePath = Resolve-FullPath (Expand-GamePath ([string]$gameConfig.minecraftInstancePath))
$tlauncherPackName = Split-Path $instancePath -Leaf
$profileId = if ($gameConfig.minecraftProfileId) { [string]$gameConfig.minecraftProfileId } else { "tiktok-minecraft-live" }
$profileName = if ($gameConfig.minecraftProfileName) { [string]$gameConfig.minecraftProfileName } else { "TikTok Live Arena" }

$mcVersion = if ($serverConfig -and $serverConfig.server.minecraftVersion) { [string]$serverConfig.server.minecraftVersion } else { "1.20.1" }
$forgeLoaderVersion = if ($serverConfig -and $serverConfig.server.forgeVersion) { [string]$serverConfig.server.forgeVersion } else { "47.4.10" }
$versionId = "$mcVersion-forge-$forgeLoaderVersion"

New-Item -ItemType Directory -Force -Path `
  $mcRoot, `
  (Join-Path $mcRoot "versions"), `
  $instancePath, `
  (Join-Path $instancePath "mods"), `
  (Join-Path $instancePath "config"), `
  (Join-Path $instancePath "saves"), `
  (Join-Path $instancePath "logs"), `
  (Join-Path $instancePath "resourcepacks"), `
  (Join-Path $instancePath "shaderpacks") | Out-Null

Sync-JarDirectory (Join-Path $rootPath "mods") (Join-Path $instancePath "mods")
Copy-DirectoryMissingOnly (Join-Path $rootPath "config") (Join-Path $instancePath "config")

$forgeResult = Ensure-ForgeClientVersion $rootPath $mcRoot $mcVersion $forgeLoaderVersion ([bool]$SkipForgeInstall)
Upsert-OfficialLauncherProfile $mcRoot $profileId $profileName $versionId $instancePath

$info = [ordered]@{
  preparedAt = (Get-Date).ToUniversalTime().ToString("o")
  minecraftRoot = $mcRoot
  instancePath = $instancePath
  profileId = $profileId
  profileName = $profileName
  versionId = $versionId
  forgeReady = [bool]$forgeResult.ok
  forgeMessage = [string]$forgeResult.message
  tlauncherPackName = $tlauncherPackName
  alternateLauncherHint = "TLauncher: choose or create modpack $tlauncherPackName, base version $versionId. Official Launcher: use profile $profileName."
}

$infoPath = Join-Path $instancePath "tiktok-live-launcher-info.json"
$info | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $infoPath -Encoding UTF8

Write-Host ""
Write-Host "Minecraft Live client prepared." -ForegroundColor Green
Write-Host "Profile: $profileName"
Write-Host "TLauncher/modpack folder: $tlauncherPackName"
Write-Host "Version: $versionId"
Write-Host "Game directory: $instancePath"
Write-Host "Info: $infoPath"
Write-Host ""

if (-not $forgeResult.ok) {
  Write-Host $forgeResult.message -ForegroundColor Yellow
  Write-Host "Open your launcher, install/select Forge $versionId once, then run Prepare again." -ForegroundColor Yellow
  exit 2
}

exit 0
