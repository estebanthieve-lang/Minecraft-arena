import json
import os
import threading
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "config" / "arena_prototype.json"
STATE_PATH = ROOT / "data" / "prototype_state.json"
COMMANDS_PATH = ROOT / "data" / "minecraft_commands.jsonl"
STATE_LOCK = threading.Lock()

ROUND_STATES = {"idle", "joining", "fighting", "ended"}
ADMIN_ACTIONS = {"reset_arena", "open_join_phase", "start_fight", "force_end_round", "move_arena_random", "clear_live_rewards"}
SWORD_ACTIONS = {
    "give_sword_wood": "wood",
    "give_sword_iron": "iron",
    "give_sword_diamond": "diamond",
    "give_sword_netherite": "netherite",
}
ARMOR_ACTIONS = {
    "give_armor_leather": "leather",
    "give_armor_chainmail": "chainmail",
    "give_armor_iron": "iron",
    "give_armor_diamond": "diamond",
}
VIEWER_ACTIONS = {"join_arena", "heal_avatar", "give_totem", *SWORD_ACTIONS.keys(), *ARMOR_ACTIONS.keys()}

ACTION_TO_COMMAND = {
    "reset_arena": {"kind": "round", "command": "reset_arena"},
    "open_join_phase": {"kind": "round", "command": "open_join_phase"},
    "join_arena": {"kind": "avatar", "command": "join_arena"},
    "start_fight": {"kind": "round", "command": "start_fight"},
    "force_end_round": {"kind": "round", "command": "force_end_round"},
    "move_arena_random": {"kind": "arena", "command": "move_arena_random"},
    "clear_live_rewards": {"kind": "live_rewards", "command": "clear_live_rewards"},
    "give_sword_wood": {"kind": "equipment", "command": "give_sword", "swordMaterial": "wood"},
    "give_sword_iron": {"kind": "equipment", "command": "give_sword", "swordMaterial": "iron"},
    "give_sword_diamond": {"kind": "equipment", "command": "give_sword", "swordMaterial": "diamond"},
    "give_sword_netherite": {"kind": "equipment", "command": "give_sword", "swordMaterial": "netherite"},
    "give_armor_leather": {"kind": "equipment", "command": "give_armor", "armorMaterial": "leather"},
    "give_armor_chainmail": {"kind": "equipment", "command": "give_armor", "armorMaterial": "chainmail"},
    "give_armor_iron": {"kind": "equipment", "command": "give_armor", "armorMaterial": "iron"},
    "give_armor_diamond": {"kind": "equipment", "command": "give_armor", "armorMaterial": "diamond"},
    "heal_avatar": {"kind": "support", "command": "heal_avatar", "supportsQuantity": True},
    "give_totem": {"kind": "support", "command": "give_totem", "supportsQuantity": True},
}


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def read_json(path, fallback):
    if not path.exists():
        return deepcopy(fallback)
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    with temp_path.open("w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
    temp_path.replace(path)


def append_jsonl(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as file:
        file.write(json.dumps(data, ensure_ascii=False) + "\n")


def default_state():
    return {
        "version": 1,
        "roundState": "idle",
        "roundNumber": 0,
        "arena": {
            "currentRadius": None,
            "nextShrinkAt": None
        },
        "participants": {},
        "liveRewards": {},
        "lastWinner": None,
        "updatedAt": now_iso()
    }


def load_config():
    return read_json(CONFIG_PATH, {})


def load_state():
    state = read_json(STATE_PATH, default_state())
    if state.get("roundState") not in ROUND_STATES:
        state["roundState"] = "idle"
    state.setdefault("participants", {})
    state.setdefault("liveRewards", {})
    state.setdefault("arena", {})
    return state


def save_state(state):
    state["updatedAt"] = now_iso()
    write_json(STATE_PATH, state)


def appdata_instance_root():
    appdata = os.environ.get("APPDATA")
    if not appdata:
        return None
    return Path(appdata) / ".minecraft" / "instances" / "TikTokMinecraftLive"


def mirror_to_game_queues(command):
    targets = []
    server_target = ROOT / "server" / "config" / "minecraft_live_arena" / "live_events.jsonl"
    append_jsonl(server_target, command)
    targets.append(str(server_target))

    instance_root = appdata_instance_root()
    if instance_root:
        client_target = instance_root / "config" / "minecraft_live_arena" / "live_events.jsonl"
        append_jsonl(client_target, command)
        targets.append(str(client_target))
    return targets


def source_username(payload):
    source = payload.get("source") if isinstance(payload.get("source"), dict) else {}
    username = source.get("username") or payload.get("username") or payload.get("user")
    return str(username).strip() if username else ""


def test_username_for(action_id, state, config):
    participants = state.get("participants", {})
    if action_id == "join_arena":
        prefix = str(config.get("testUsernamePrefix", "viewer_test_")).strip() or "viewer_test_"
        for index in range(1, 1000):
            username = f"{prefix}{index:02d}"
            if username.lower() not in participants:
                return username
    if participants:
        last_participant = list(participants.values())[-1]
        username = str(last_participant.get("username", "")).strip()
        if username:
            return username
    return str(config.get("testUsername", "viewer_test")).strip()


def quantity(payload, default=1, maximum=999):
    try:
        value = int(payload.get("quantity", default))
    except (TypeError, ValueError):
        value = default
    return max(1, min(value, maximum))


def participant_for(state, username):
    return state["participants"].get(username.lower())


def ensure_participant(state, username):
    key = username.lower()
    if key not in state["participants"]:
        state["participants"][key] = {
            "username": username,
            "alive": True,
            "joinedAt": now_iso(),
            "swordMaterial": "wood",
            "armorMaterial": "none",
            "totems": 0,
            "healsReceived": 0,
            "kills": 0,
            "winsThisLive": 0
        }
    return state["participants"][key]


def reject(reason):
    return {"accepted": False, "reason": reason}


def accept(command, state):
    append_jsonl(COMMANDS_PATH, command)
    mirrored_to = mirror_to_game_queues(command)
    return {
        "accepted": True,
        "queued": True,
        "roundState": state["roundState"],
        "command": command,
        "localQueue": str(COMMANDS_PATH),
        "minecraftQueues": mirrored_to
    }


def base_command(action_id, payload, state, username=""):
    command = dict(ACTION_TO_COMMAND[action_id])
    command.update(
        {
            "action": action_id,
            "eventId": payload.get("eventId", ""),
            "profileId": payload.get("profileId", ""),
            "gameId": payload.get("gameId", "minecraft-tiktok-live"),
            "username": username,
            "roundState": state["roundState"],
            "roundNumber": state["roundNumber"],
            "source": payload.get("source", {}),
            "receivedAt": now_iso()
        }
    )
    return command


def reset_arena(state, config, payload):
    keep_rewards = bool(config.get("round", {}).get("keepWinnerRewardsBetweenRounds", True))
    state["roundState"] = "idle"
    state["roundNumber"] = int(state.get("roundNumber", 0)) + 1
    state["participants"] = {}
    state["lastWinner"] = None
    state["arena"] = {
        "currentRadius": config.get("arena", {}).get("initialRadius"),
        "nextShrinkAt": None
    }
    if not keep_rewards:
        state["liveRewards"] = {}
    command = base_command("reset_arena", payload, state)
    command["arena"] = config.get("arena", {})
    command["keepLiveRewards"] = keep_rewards
    return command


def open_join_phase(state, config, payload):
    state["roundState"] = "joining"
    if state["arena"].get("currentRadius") is None:
        state["arena"]["currentRadius"] = config.get("arena", {}).get("initialRadius")
    command = base_command("open_join_phase", payload, state)
    command["joinCommand"] = config.get("joinCommand", "!unirse")
    command["maxParticipants"] = config.get("round", {}).get("maxParticipants", 60)
    return command


def join_arena(state, config, payload, username):
    if state["roundState"] != "joining":
        state["roundState"] = "joining"
    existing = participant_for(state, username)
    if existing:
        command = base_command("join_arena", payload, state, username)
        command["alreadyJoined"] = True
        command["participant"] = existing
        return command
    participant = ensure_participant(state, username)
    rewards = state["liveRewards"].get(username.lower(), {})
    participant["permanentRewards"] = rewards
    command = base_command("join_arena", payload, state, username)
    command["participant"] = participant
    command["spawnMode"] = "circle_slot"
    command["freezeUntilFight"] = True
    command["minecraftDecidesAdmission"] = True
    return command


def start_fight(state, config, payload):
    state["roundState"] = "fighting"
    command = base_command("start_fight", payload, state)
    command["participants"] = list(state["participants"].values())
    command["arena"] = {
        "currentRadius": state["arena"].get("currentRadius"),
        "shrinkEverySeconds": config.get("arena", {}).get("shrinkEverySeconds"),
        "shrinkByBlocks": config.get("arena", {}).get("shrinkByBlocks"),
        "minimumRadius": config.get("arena", {}).get("minimumRadius")
    }
    return command


def force_end_round(state, payload):
    state["roundState"] = "ended"
    command = base_command("force_end_round", payload, state)
    command["participants"] = list(state["participants"].values())
    return command


def clear_live_rewards(state, payload):
    state["liveRewards"] = {}
    command = base_command("clear_live_rewards", payload, state)
    return command


def apply_viewer_action(state, config, payload, username, action_id):
    participant = participant_for(state, username) or ensure_participant(state, username)

    limits = config.get("limits", {})
    command = base_command(action_id, payload, state, username)
    command["target"] = "own_avatar"

    if action_id in SWORD_ACTIONS:
        material = SWORD_ACTIONS[action_id]
        participant["swordMaterial"] = material
        command["swordMaterial"] = material
    elif action_id in ARMOR_ACTIONS:
        material = ARMOR_ACTIONS[action_id]
        participant["armorMaterial"] = material
        command["armorMaterial"] = material
    elif action_id == "heal_avatar":
        heal_amount = min(quantity(payload), int(limits.get("maxHealPerAction", 20)))
        participant["healsReceived"] = int(participant.get("healsReceived", 0)) + heal_amount
        command["healAmount"] = heal_amount
    elif action_id == "give_totem":
        max_totems = int(limits.get("maxTotemsPerUser", 5))
        add_totems = quantity(payload, maximum=max_totems)
        participant["totems"] = min(max_totems, int(participant.get("totems", 0)) + add_totems)
        command["totems"] = participant["totems"]
        command["rescueRule"] = "if_fatal_or_outside_arena_tp_to_safe_point"

    command["participant"] = participant
    return command


def execute_action(action_id, payload, manifest=None):
    if action_id not in ACTION_TO_COMMAND:
        raise ValueError(f"accion no implementada: {action_id}")

    with STATE_LOCK:
        config = load_config()
        username = source_username(payload)
        state = load_state()
        if action_id in VIEWER_ACTIONS and not username:
            username = test_username_for(action_id, state, config)
        if action_id in VIEWER_ACTIONS and not username:
            return reject("falta source.username para accion de viewer")

        if action_id == "reset_arena":
            command = reset_arena(state, config, payload)
        elif action_id == "open_join_phase":
            command = open_join_phase(state, config, payload)
        elif action_id == "join_arena":
            command = join_arena(state, config, payload, username)
        elif action_id == "start_fight":
            command = start_fight(state, config, payload)
        elif action_id == "force_end_round":
            command = force_end_round(state, payload)
        elif action_id == "move_arena_random":
            command = base_command("move_arena_random", payload, state)
        elif action_id == "clear_live_rewards":
            command = clear_live_rewards(state, payload)
        else:
            command = apply_viewer_action(state, config, payload, username, action_id)

        if isinstance(command, dict) and command.get("accepted") is False:
            save_state(state)
            return command

        save_state(state)
        return accept(command, state)
