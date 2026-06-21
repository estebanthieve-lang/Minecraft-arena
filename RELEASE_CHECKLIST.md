# Release checklist - Minecraft Live Arena

Este archivo se debe leer antes de hacer commit, tag, release o subir un ZIP para el launcher.

## Fuente de verdad

La fuente editable real del juego es esta carpeta:

```txt
C:\Users\usuario\Music\Minecraft TikTok Live
```

No publicar desde:

```txt
C:\Users\usuario\AppData\Local\TikTokLiveGames\games\minecraft-tiktok-live
C:\Users\usuario\Music\Base para juegos de tiktok\nuevos proyectos-juegos\MINECRAFT_TIKTOK_LIVE
```

La carpeta de `%LOCALAPPDATA%` es instalacion generada por la app. La carpeta de `Base para juegos...` puede tener piezas antiguas y no es release source.

## Antes de commit

1. Revisar `git status --short`.
2. Revisar que los cambios importantes esten en la fuente real, no solo en la instalacion local.
3. Si cambian acciones, actualizar:
   - `game-manifest.json`;
   - `assets\acciones_png`;
   - `runtime`;
   - jars en `mods`, `dist\mods` y `server\mods` si aplica.
4. Si el mapa/arena cambia, verificar que `server\world` existe y que el release lo incluye. No borrar `server\world` del ZIP final; se excluyen datos personales/volatiles como `session.lock`, `playerdata`, `stats` y `advancements`.
5. No commitear datos vivos:
   - `downloads`;
   - `backups`;
   - `tmp`;
   - `logs`;
   - `saves`;
   - `user-data`;
   - caches de Gradle o Python.

## Antes de release

Ejecutar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\crear_release.ps1
```

El script debe confirmar:

- manifest valido;
- version detectada;
- `assets\acciones_png` con PNG para cada accion;
- jars del mod en `mods`, `dist\mods` y `server\mods`;
- `runtime\event_bus.py`;
- `tools\python-embed\python.exe`;
- ZIP versionado y ZIP generico creados.
- `server\world\level.dat` incluido en el ZIP.

## Despues de release

1. Subir el ZIP a GitHub Releases.
2. Verificar que la URL publica descarga ese mismo ZIP.
3. Instalar desde la app en una carpeta limpia.
4. Confirmar que la app no muestra:
   - PNG faltantes;
   - EventBus visible;
   - version vieja;
   - jar viejo;
   - error de manifest.

## Regla

Si el ZIP no pasa validacion, no se sube.
