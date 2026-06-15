# Contexto del proyecto

Este proyecto queda como prototipo de Minecraft Forge 1.20.1 para una arena por rondas conectable a la base TikTok Live.

## Objetivo

Crear una arena donde viewers entren con comentario, esperen congelados, peleen por rondas y reciban regalos/likes aplicados a su propio avatar.

## Loop esperado

1. Iniciar todo con `INICIAR_JUEGO.cmd`.
2. El script prepara servidor Forge local, sincroniza el mod y abre event bus + servidor.
3. El script no abre Minecraft, no abre Microsoft Store y no abre navegador.
4. El usuario abre Minecraft manualmente y entra al servidor `127.0.0.1`.
6. La base/pagina conecta el manifest.
7. El creador abre inscripciones.
8. Viewers se unen.
9. El creador inicia pelea.
10. Durante pelea, regalos/likes equipan o curan al avatar propio.

## Regla importante

No tocar `%APPDATA%\.minecraft\mods` global del usuario. Todo debe vivir dentro de:

```txt
%APPDATA%\.minecraft\instances\TikTokMinecraftLive
```

## Estado actual

Este paquete instala:

- una instancia aislada de Minecraft en `%APPDATA%\.minecraft\instances\TikTokMinecraftLive`;
- un servidor Forge local en `server/`;
- mundo plano en `server/world`;
- `config/arena_prototype.json`;
- `config/arena_server.json`;
- mod fuente en `game/mod-src`;
- `game-manifest.json`
- `game.config.json`
- `runtime/event_bus.py`
- `runtime/game_adapter.py`
- `INICIAR_JUEGO.cmd`
- herramientas auxiliares en `HERRAMIENTAS_DEV/`

El manifest esta en modo prototipo:

- `status`: `prototype`
- acciones core de ronda y avatar.

Pendiente para que se vea dentro de Minecraft:

- reemplazar avatar custom por NPC/player realista: idealmente fake-player/NPC con modelo, animaciones, hit feedback y skin de jugador/TikTok. No debe sentirse como mob maquillado.
- el combate debe sentirse como PvP: cooldown de arma, animacion de golpe, daño visual, knockback horizontal natural y sin empuje vertical raro.
- crear menu dentro de Minecraft tipo servidores: inventario/GUI con objetos clickeables para admin, pruebas y configuracion de ronda.
- mejorar recompensas persistentes.
- configurar GitHub en `game-manifest.json` cuando se cree el repo:
  - `updates.githubRepo`,
  - `updates.latestManifestUrl`,
  - `updates.downloadUrl` apuntando a un ZIP liviano de update, no al repo completo.

## Al crear un juego nuevo de Minecraft

Debe quedar listo con estas piezas:

- instancia aislada en `%APPDATA%\.minecraft\instances\NOMBRE_DEL_JUEGO`,
- `.cmd` instalador que cree carpetas si faltan,
- `game-manifest.json` con acciones reales,
- `game.config.json` con rutas protegidas y actualizables,
- `runtime/event_bus.py`,
- `runtime/game_adapter.py`,
- `VALIDAR_JUEGO.cmd`,
- `ACTUALIZAR_JUEGO.cmd`,
- `INICIAR_JUEGO.cmd`,
- contrato del puente del mod.

Si falta GitHub o el puente del mod, el validador debe avisarlo como pendiente.

## Contrato agregado

- Manifest: `GET http://127.0.0.1:9010/manifest`
- Event bus: `POST http://127.0.0.1:9010/event`
- Health: `GET http://127.0.0.1:9010/health`
- Acciones: `GET http://127.0.0.1:9010/actions`

El event bus valida acciones desde `game-manifest.json`, responde CORS/OPTIONS para la base web, deduplica por `eventId`, y encola las ordenes en JSONL.

`POST /event` solo acepta acciones declaradas en `game-manifest.json`.
