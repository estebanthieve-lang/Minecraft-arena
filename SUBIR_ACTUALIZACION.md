# Subir actualizacion de Minecraft Live Arena

Fuente real:

```txt
C:\Users\usuario\Music\Minecraft TikTok Live
```

No subir desde `%LOCALAPPDATA%` ni desde una copia de plantilla.

## Reglas

1. Sube una version nueva, nunca reemplaces una ya publicada.
2. `game-manifest.json` debe estar en UTF-8 sin BOM.
3. El release de GitHub va en `estebanthieve-lang/minecraft-live-arena-release`.
4. La app publica baja el juego desde el backend:
   `https://livekit-base-backend.onrender.com/api/public/game-download/minecraft-tiktok-live`
5. Si cambias version del juego, tambien actualiza el catalogo del backend.

## Pasos

```powershell
cd "C:\Users\usuario\Music\Minecraft TikTok Live"
git status --short
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validar_juego.ps1 -Root .
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\crear_release.ps1
```

Crear ZIP liviano:

```powershell
$version = (Get-Content -Raw .\game-manifest.json | ConvertFrom-Json).version
$out = "downloads\release\minecraft-live-arena-update-$version.zip"
Compress-Archive -Force -CompressionLevel Optimal -DestinationPath $out -Path `
  .\ACTUALIZAR_JUEGO.cmd, `
  .\game-manifest.json, `
  .\INICIAR_JUEGO.cmd, `
  .\scripts, `
  .\mods, `
  .\dist\mods, `
  .\server\mods
Copy-Item $out "downloads\release\minecraft-live-arena-update.zip" -Force
```

Publicar release privado:

```powershell
git add game-manifest.json runtime/event_bus.py scripts/crear_release.ps1 SUBIR_ACTUALIZACION.md
git commit -m "Release Minecraft Live Arena 0.1.XX"
git push fuente-privada main
git tag v0.1.XX
git push fuente-privada v0.1.XX
gh release create v0.1.XX `
  .\downloads\release\minecraft-live-arena-update-0.1.XX.zip `
  .\downloads\release\minecraft-live-arena-update.zip `
  --repo estebanthieve-lang/minecraft-live-arena-release `
  --title "Minecraft Live Arena 0.1.XX" `
  --notes "Actualizacion liviana sin BOM en game-manifest.json."
```

Actualizar backend para la app:

1. Copia el full ZIP a:
   `C:\Users\usuario\Music\Base para juegos de tiktok\downloads\games\minecraft-tiktok-live.zip`
2. En `C:\Users\usuario\Music\Base para juegos de tiktok\data\games.json`, actualiza `version`, `packageSize`, `sha256`, `downloadUrl` y `releaseUrl`.
3. Commit y push del backend.
4. Verifica:

```powershell
Invoke-RestMethod https://livekit-base-backend.onrender.com/api/public/catalog
Invoke-WebRequest -Method Head https://livekit-base-backend.onrender.com/api/public/game-download/minecraft-tiktok-live
```
