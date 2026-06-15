# TikTok Minecraft Live

Prototipo inicial de arena por rondas para viewers de TikTok Live.

El manifest declara solo acciones core para probar el loop sin llenar la base de basura.

## Uso

1. Ejecuta `PREPARAR_CLIENTE.cmd` una vez en cada PC que vaya a entrar a Minecraft.
2. Ejecuta `INICIAR_JUEGO.cmd` en el PC que hostea la arena.
3. `INICIAR_JUEGO.cmd` abre solo el event bus y el servidor Forge local.
4. Abre Minecraft Launcher manualmente y elige el perfil `TikTok Minecraft Live`.
5. En Minecraft entra al servidor local `127.0.0.1`.
6. En la base usa el manifest `http://127.0.0.1:9010/manifest`.

`INICIAR_JUEGO.cmd` no abre Minecraft, no abre Microsoft Store y no abre navegador.
`PREPARAR_CLIENTE.cmd` tampoco abre Minecraft, no abre Microsoft Store y no abre navegador.

Los comandos auxiliares quedaron en `HERRAMIENTAS_DEV/`.

## Portabilidad

La carpeta esta pensada para moverse/copiarse como paquete completo.

En otro PC requiere:

- Windows;
- Java 17 instalado y disponible como `java`;
- Python 3 instalado y disponible como `python`;
- Minecraft Launcher con Forge `1.20.1-forge-47.4.10` instalado una vez para el cliente.

El servidor Forge se descarga/prepara solo desde `INICIAR_JUEGO.cmd` si falta.
El cliente aislado se crea con `PREPARAR_CLIENTE.cmd` en:

```txt
%APPDATA%\.minecraft\instances\TikTokMinecraftLive
```

No se usa `%APPDATA%\.minecraft\mods` global.

## Que hace INICIAR_JUEGO

- prepara el servidor Forge local si falta;
- sincroniza el mod de arena mas reciente;
- abre el event bus local;
- abre el servidor local.

No abre Minecraft ni toca `%APPDATA%\.minecraft\mods` global.

## Conexion con la base TikTok

- Manifest: `GET http://127.0.0.1:9010/manifest`
- Acciones: `GET http://127.0.0.1:9010/actions`
- Event bus: `POST http://127.0.0.1:9010/event`
- Health: `GET http://127.0.0.1:9010/health`

El event bus responde rapido, valida que la accion exista en `game-manifest.json` y deduplica `eventId`.

El estado del prototipo se puede revisar en:

- `GET http://127.0.0.1:9010/state`
- `data/prototype_state.json`
- `data/minecraft_commands.jsonl`

## Acciones iniciales

- `reset_arena`
- `open_join_phase`
- `join_arena`
- `start_fight`
- `force_end_round`
- `clear_live_rewards`
- `give_sword_wood`
- `give_sword_iron`
- `give_sword_diamond`
- `give_sword_netherite`
- `give_armor_leather`
- `give_armor_chainmail`
- `give_armor_iron`
- `give_armor_diamond`
- `heal_avatar`
- `give_totem`

Cada accion de viewer usa `source.username` y afecta solo al avatar de ese usuario.

## Servidor y mundo

- Servidor local Forge: `server/`
- Mundo: `server/world`
- Tipo: plano
- Mobs naturales: desactivados
- Animales/NPCs naturales: desactivados
- PvP vanilla: desactivado
- Arena: definida en `config/arena_server.json`

El mundo del servidor esta protegido y no se pisa al actualizar.

## Mod

El mod propio vive en `game/mod-src`.

Flujo:

```txt
HERRAMIENTAS_DEV/COMPILAR_MOD_ARENA.cmd -> dist/mods/minecraft_live_arena-*.jar
HERRAMIENTAS_DEV/SINCRONIZAR_MOD_ARENA.cmd -> mods + server/mods + instancia cliente
```

El sincronizador elimina versiones antiguas `minecraft_live_arena-*.jar` antes de copiar la nueva.

## Actualizaciones seguras

- `HERRAMIENTAS_DEV/VALIDAR_JUEGO.cmd` revisa manifest, config, acciones y Python.
- `HERRAMIENTAS_DEV/ACTUALIZAR_JUEGO.cmd` aplica una carpeta nueva del mismo `gameId`.
- El actualizador no pisa `config`, `saves`, `data`, `logs` ni `user-data`.

## Requisito

Java 17 debe estar instalado. El servidor Forge se prepara con `INICIAR_JUEGO.cmd`.
