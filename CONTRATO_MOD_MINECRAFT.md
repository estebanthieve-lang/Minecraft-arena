# Contrato del prototipo Minecraft Live Arena

Este prototipo define una arena por rondas para viewers de TikTok Live.

## Loop de ronda

1. `reset_arena`
   - limpia ronda;
   - reconstruye arena;
   - deja estado `idle`.

2. `open_join_phase`
   - deja estado `joining`;
   - los viewers pueden entrar con `join_arena`;
   - los avatares quedan congelados hasta iniciar pelea.

3. `join_arena`
   - requiere `source.username`;
   - crea o reutiliza el avatar del viewer;
   - cada username controla solo su propio avatar.

4. `start_fight`
   - deja estado `fighting`;
   - cierra inscripciones;
   - activa pelea y cierre progresivo de arena.

5. Acciones durante ronda
   - `give_sword`;
   - `give_armor`;
   - `heal_avatar`;
   - `give_totem`.

6. `force_end_round`
   - termina la ronda manualmente.

## Estado editable

- Configuracion: `config/arena_prototype.json`
- Estado actual: `data/prototype_state.json`
- Eventos recibidos: `data/events_inbox.jsonl`
- Comandos para Minecraft: `data/minecraft_commands.jsonl`
- Cola para el mod instalado: `%APPDATA%\.minecraft\instances\TikTokMinecraftLive\config\minecraft_live_arena\live_events.jsonl`

## Reglas importantes

- No hay overlay externo.
- Las acciones de viewer siempre aplican a `source.username`.
- Nadie puede unirse despues de `start_fight`.
- Los regalos/equipamiento pueden aplicarse en `joining` y `fighting`.
- El totem debe rescatar rapido al avatar si cae fuera de la arena o recibe dano fatal.
- El cierre de arena se configura en `arena_prototype.json`; el mod debe ejecutar el achique real en Minecraft.

## Pendiente del mod Forge

El event bus ya produce comandos JSONL. El mod `minecraft_live_arena` debe leer esos comandos y ejecutar:

- crear avatar;
- congelar/liberar avatar;
- equipar espada/armadura;
- curar;
- registrar totems;
- rescatar a punto seguro;
- reconstruir/achicar arena.

## Arquitectura obligatoria

```txt
Base web -> event bus local -> server/config/minecraft_live_arena/live_events.jsonl -> mod Forge en servidor -> mundo plano
```

El cliente tambien recibe el mismo mod para evitar diferencias raras entre cliente y servidor.

La arena no es una idea invisible: esta definida en `config/arena_server.json`:

- mundo: `minecraft:overworld`;
- centro: `0 64 0`;
- piso: `Y=63`;
- radio inicial: `40`;
- radio minimo: `6`;
- cierre cada `30` segundos.
