package com.tiktoklivearena;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

@Mod(MinecraftLiveArenaMod.MOD_ID)
public class MinecraftLiveArenaMod {
    public static final String MOD_ID = "minecraft_live_arena";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String AVATAR_TAG = "minecraft_live_arena_avatar";
    private static final String NAMEPLATE_TAG = "minecraft_live_arena_nameplate";
    private static final String COMBAT_TEXT_TAG = "minecraft_live_arena_combat_text";
    private static final String HIDDEN_NAME_TEAM = "arena_hidden_names";
    private static final String WINS_OBJECTIVE = "arena_wins";
    private static final String NETWORK_PROTOCOL = "2";
    private static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MOD_ID, "main"),
        () -> NETWORK_PROTOCOL,
        NETWORK_PROTOCOL::equals,
        NETWORK_PROTOCOL::equals
    );
    private static int nextPacketId = 0;
    private static final double BASE_ARENA_CENTER_X = 0.5D;
    private static final double BASE_ARENA_CENTER_Z = 0.5D;
    private static final int ARENA_Y = 28;
    private static final int LAVA_Y = 1;
    private static final int LAVA_SURFACE_GAP = 4;
    private static final int ARENA_WALL_TOP_EXTRA = 24;
    private static final int LEGACY_ARENA_Y = 72;
    private static final int LEGACY_LAVA_Y = 62;
    private static final int INITIAL_RADIUS = 30;
    private static final int LAVA_RADIUS = 39;
    private static final int GLASS_WALL_RADIUS = 40;
    private static final int ARENA_CENTER_MARK_RADIUS = 3;
    private static final double ARENA_EDGE_MARK_WIDTH = 1.15D;
    private static final int MINIMUM_RADIUS = 6;
    private static final int SHRINK_SECONDS = 30;
    private static final int SHRINK_BLOCKS = 3;
    private static final int DEFAULT_STARTING_HEALTH = 50;
    private static final int MIN_STARTING_HEALTH = 20;
    private static final int MAX_STARTING_HEALTH = 200;
    private static final int DEFAULT_FIGHT_COUNTDOWN_SECONDS = 10;
    private static final int MIN_FIGHT_COUNTDOWN_SECONDS = 3;
    private static final int MAX_FIGHT_COUNTDOWN_SECONDS = 60;
    private static final int DEFAULT_SCOREBOARD_HUD_ROWS = 8;
    private static final int MIN_SCOREBOARD_HUD_ROWS = 3;
    private static final int MAX_SCOREBOARD_HUD_ROWS = 15;
    private static final int DEFAULT_SCOREBOARD_HUD_WIDTH = 150;
    private static final int MIN_SCOREBOARD_HUD_WIDTH = 118;
    private static final int MAX_SCOREBOARD_HUD_WIDTH = 260;
    private static final int DEFAULT_SCOREBOARD_HUD_Y = 24;
    private static final int MIN_SCOREBOARD_HUD_Y = 4;
    private static final int MAX_SCOREBOARD_HUD_Y = 120;
    private static final int CLEANUP_PADDING = 12;
    private static final int DYNAMIC_CENTER_ALIVE_LIMIT = 3;
    private static final double MAX_CENTER_MOVE_PER_SHRINK = 4.0D;
    private static final String CONTROL_PAGE_TAG = "ArenaControlPage";
    private static final String CONTROL_ITEM_NAME = "Arena Control";
    private static final String ROUND_ITEM_NAME = "Arena Ronda";
    private static final String PLAYERS_ITEM_NAME = "Arena Jugadores";
    private static final String ARENA_ITEM_NAME = "Arena Ajustes";
    private static final String PODIUM_ITEM_NAME = "Arena Podio";
    private static final String WEATHER_ITEM_NAME = "Arena Clima";
    private static final String GAME_ITEM_NAME = "Arena Juego";
    private static final String PODIUM_WAND_NAME = "Podio Selector";
    private static final String PODIUM_WAND_MODE_TAG = "ArenaPodiumWandMode";
    private static final double PLAYER_WALK_BLOCKS_PER_TICK = 0.10D;
    private static final double PLAYER_STRAFE_BLOCKS_PER_TICK = 0.07D;
    private static final double VANILLA_HURT_KNOCKBACK = 0.56D;
    private static final double ATTACK_RANGE_MIN = 2.05D;
    private static final double ATTACK_RANGE_MAX = 2.55D;
    private static final double ATTACK_RANGE_SQR = ATTACK_RANGE_MAX * ATTACK_RANGE_MAX;
    private static final double ATTACK_LUNGE_BLOCKS = 0.30D;
    private static final double VOLUNTARY_EDGE_PADDING = 1.15D;
    private static final int HURT_RECOVERY_TICKS = 5;
    private static final int COMBAT_TEXT_TICKS = 24;
    private static final double FAKE_PLAYER_ATTACK_WINDUP_RATIO = 0.33D;
    private static final int COMBAT_STALL_LOG_TICKS = 100;
    private static final int NOTICE_FAST_FORWARD_TICKS = 8;
    private static final int NOTICE_QUEUE_FAST_THRESHOLD = 2;
    private static final int NOTICE_QUEUE_LIMIT = 5;
    private static final int PLAYER_MENU_PAGE_SIZE = 40;
    private static final int RANDOM_ARENA_MOVE_INTERVAL_TICKS = 20;
    private static final double RANDOM_ARENA_MOVE_STEP = 1.0D;
    private static final double RANDOM_ARENA_TARGET_EPSILON = 0.35D;
    private static final float PODIUM_FACE_YAW_OFFSET = 0.0F;
    private static final int PODIUM_EMOTE_MIN_COOLDOWN_TICKS = 80;
    private static final int PODIUM_EMOTE_RANDOM_COOLDOWN_TICKS = 50;
    private static final int WINNER_FIREWORK_TICKS = 30 * 20;
    private static final int PODIUM_FIREWORK_TICKS = 30 * 20;
    private static final boolean KEEP_WINNER_START_REWARD = false;
    private static final int MAX_PENDING_TOTEMS = 99;
    private static final int MAX_PENDING_HEAL = 100;
    private static final String[] RANDOM_SKIN_NAMES = {
        "Steve", "Alex", "Technoblade", "Dream", "Sapnap", "GeorgeNotFound", "TommyInnit", "Tubbo",
        "Ranboo", "BadBoyHalo", "Skeppy", "CaptainSparklez", "DanTDM", "PrestonPlayz", "BajanCanadian",
        "JeromeASF", "AntVenom", "Smallishbeans", "Grian", "MumboJumbo", "EthosLab", "xisumavoid",
        "LDShadowLady", "GeminiTay", "FalseSymmetry", "PearlescentMoon", "GoodTimesWithScar"
    };
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    private final Map<String, LivingEntity> avatars = new HashMap<>();
    private final Map<String, LivingEntity> arenaFighters = new HashMap<>();
    private final Map<String, Display.TextDisplay> avatarNameplates = new HashMap<>();
    private final List<FloatingCombatText> floatingCombatTexts = new ArrayList<>();
    private final Map<String, Property> skinCache = new HashMap<>();
    private final Map<String, UUID> skinUuidCache = new HashMap<>();
    private final Map<String, String> avatarSkinNames = new HashMap<>();
    private final Map<String, GameProfile> avatarProfiles = new HashMap<>();
    private final Map<String, Integer> attackCooldowns = new HashMap<>();
    private final Map<String, Integer> fighterAttackCooldowns = new HashMap<>();
    private final Map<String, PendingAttack> pendingAttacks = new HashMap<>();
    private final Map<String, Integer> strafeDirections = new HashMap<>();
    private final Map<String, Integer> strafeTicks = new HashMap<>();
    private final Map<String, Integer> hurtRecoveryTicks = new HashMap<>();
    private final Map<String, Integer> shieldBlockTicks = new HashMap<>();
    private final Map<String, Long> lastCombatActionTicks = new HashMap<>();
    private final Map<String, Long> lastCombatWarnTicks = new HashMap<>();
    private final Map<String, Integer> totemCounts = new HashMap<>();
    private final Map<String, Integer> playerWins = new HashMap<>();
    private final Set<String> sidebarWinEntries = new HashSet<>();
    private final Map<String, PendingLiveReward> pendingLiveRewards = new HashMap<>();
    private final Map<String, Double> verticalVelocities = new HashMap<>();
    private final Map<String, Boolean> victoriousStartEnabled = new HashMap<>();
    private final Queue<ArenaNotice> arenaNotices = new ArrayDeque<>();
    private final Map<Integer, PodiumConfig> podiums = new HashMap<>();
    private final Map<UUID, String> podiumWandModes = new HashMap<>();
    private final Map<UUID, Integer> playerMenuPages = new HashMap<>();
    private final Map<String, PodiumSnapshot> podiumSnapshots = new HashMap<>();
    private final Map<String, Display.TextDisplay> podiumNumberMarkers = new HashMap<>();
    private final List<Entity> podiumDisplayAvatars = new ArrayList<>();
    private final List<String> eliminationOrder = new ArrayList<>();
    private final List<Object> podiumEmoteAnimations = new ArrayList<>();
    private final Map<UUID, Integer> podiumEmoteCooldowns = new HashMap<>();
    private final Map<UUID, Integer> podiumEmotePlaces = new HashMap<>();
    private boolean podiumEmoteLoadAttempted = false;
    private long readOffset = 0L;
    private int tickCounter = 0;
    private int fightTickCounter = 0;
    private int fightCountdownTicks = 0;
    private int fightCountdownLastSecond = 0;
    private String roundState = "idle";
    private String roundWinnerKey = "";
    private int currentRadius = INITIAL_RADIUS;
    private int initialRadius = INITIAL_RADIUS;
    private int shrinkSeconds = SHRINK_SECONDS;
    private int shrinkBlocks = SHRINK_BLOCKS;
    private int minimumRadius = MINIMUM_RADIUS;
    private int startingHealth = DEFAULT_STARTING_HEALTH;
    private int fightCountdownSeconds = DEFAULT_FIGHT_COUNTDOWN_SECONDS;
    private double arenaCenterX = BASE_ARENA_CENTER_X;
    private double arenaCenterZ = BASE_ARENA_CENTER_Z;
    private int activePodium = 1;
    private boolean randomPodium = false;
    private int menuRevision = 0;
    private int testViewerCounter = 1;
    private int activeNoticeTicks = 0;
    private boolean randomArenaMovementEnabled = false;
    private double randomArenaTargetX = BASE_ARENA_CENTER_X;
    private double randomArenaTargetZ = BASE_ARENA_CENTER_Z;
    private int randomArenaMoveTicks = 0;
    private String winnerCelebrationKey = "";
    private int winnerFireworkTicks = 0;
    private int winnerFireworkCooldown = 0;
    private int podiumFireworkTicks = 0;
    private int podiumFireworkCooldown = 0;
    private boolean scoreboardSidebarVisible = true;
    private boolean scoreboardSaveWins = true;
    private int scoreboardHudRows = DEFAULT_SCOREBOARD_HUD_ROWS;
    private int scoreboardHudWidth = DEFAULT_SCOREBOARD_HUD_WIDTH;
    private int scoreboardHudY = DEFAULT_SCOREBOARD_HUD_Y;
    private int scoreboardHudSyncTicks = 0;

    public MinecraftLiveArenaMod() {
        registerNetworkPackets();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientWinsHud::registerClientEvents);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private static void registerNetworkPackets() {
        NETWORK.registerMessage(
            nextPacketId++,
            WinsHudPacket.class,
            WinsHudPacket::encode,
            WinsHudPacket::decode,
            WinsHudPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private void onServerStarted(ServerStartedEvent event) {
        ensureQueue(event.getServer());
        loadArenaSettings();
        loadPodiumSettings();
        loadScoreboardSettings();
        saveScoreboardSettings();
        syncWinsSidebar(event.getServer(), true);
        sendWinsHudToAll(event.getServer());
        preloadSkinTextures();
        ServerLevel level = arenaLevel(event.getServer());
        if (level != null) {
            buildStaticArenaShell(level);
            buildCombatPlatform(level, currentRadius);
            level.setDefaultSpawnPos(new BlockPos(Mth.floor(BASE_ARENA_CENTER_X), ARENA_Y + 2, Mth.floor(BASE_ARENA_CENTER_Z)), 0.0F);
        }
        try {
            readOffset = Files.size(queuePath(event.getServer()));
        } catch (IOException error) {
            readOffset = 0L;
        }
        LOGGER.info("Minecraft Live Arena listo. Cola: {}", queuePath(event.getServer()));
    }

    private void onServerStopping(ServerStoppingEvent event) {
        saveScoreboardSettings();
        ServerLevel level = arenaLevel(event.getServer());
        if (level != null) {
            removeAvatars(level);
        }
        avatars.clear();
        arenaFighters.clear();
        avatarProfiles.clear();
        avatarSkinNames.clear();
        podiumSnapshots.clear();
        podiumDisplayAvatars.clear();
        podiumEmoteCooldowns.clear();
        podiumEmotePlaces.clear();
        attackCooldowns.clear();
        fighterAttackCooldowns.clear();
        pendingAttacks.clear();
        strafeDirections.clear();
        strafeTicks.clear();
        hurtRecoveryTicks.clear();
        shieldBlockTicks.clear();
        lastCombatActionTicks.clear();
        lastCombatWarnTicks.clear();
        totemCounts.clear();
        pendingLiveRewards.clear();
        clearFloatingCombatTexts();
        verticalVelocities.clear();
        arenaNotices.clear();
        playerMenuPages.clear();
        fightCountdownTicks = 0;
        fightCountdownLastSecond = 0;
        roundWinnerKey = "";
        randomArenaMovementEnabled = false;
        randomArenaTargetX = BASE_ARENA_CENTER_X;
        randomArenaTargetZ = BASE_ARENA_CENTER_Z;
        randomArenaMoveTicks = 0;
        stopCelebrationFireworks();
        activeNoticeTicks = 0;
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        tickCounter++;
        if (tickCounter >= 1) {
            tickCounter = 0;
            readQueue(server);
        }

        updateFightCountdown(server);
        updateAvatarPhysics(server);
        updateArenaShrink(server);
        updateRandomArenaMovement(server);
        updateCombatTargets(server);
        updateArenaNotices(server);
        updatePodiumEmotes();
        updateCelebrationFireworks(server);
        updateScoreboardHudSync(server);
        updateFloatingCombatTexts();
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensurePlayerOp(player);
            ensureControlShortcutItems(player);
            syncWinsSidebar(player.getServer(), true);
            sendWinsHudToPlayer(player);
        }
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        String page = controlShortcutPage(stack);
        if (page.isBlank()) {
            return;
        }
        openArenaMenu(player, page, "");
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!isPodiumWand(stack)) {
            return;
        }
        String mode = podiumWandModes.remove(player.getUUID());
        if ((mode == null || mode.isBlank()) && stack.hasTag()) {
            mode = stack.getTag().getString(PODIUM_WAND_MODE_TAG);
        }
        if (mode == null || mode.isBlank()) {
            return;
        }
        BlockPos pos = event.getPos();
        PodiumConfig podium = podiums.computeIfAbsent(activePodium, ignored -> new PodiumConfig());
        SavedPos saved = new SavedPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        int place = podiumPlaceForMode(mode);
        if ("first".equals(mode)) {
            podium.first = saved;
        } else if ("second".equals(mode)) {
            podium.second = saved;
        } else if ("third".equals(mode)) {
            podium.third = saved;
        }
        savePodiumSettings();
        if (place > 0 && player.level() instanceof ServerLevel level) {
            spawnPodiumNumber(level, activePodium, place, saved, podium.view);
        }
        removePodiumWands(player.getInventory());
        message(player.getServer(), "Podio " + activePodium + ": puesto guardado");
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private Path queuePath(MinecraftServer server) {
        return Path.of(".").toAbsolutePath().normalize()
            .resolve("config")
            .resolve("minecraft_live_arena")
            .resolve("live_events.jsonl");
    }

    private void ensureQueue(MinecraftServer server) {
        try {
            Path path = queuePath(server);
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, "", StandardCharsets.UTF_8);
            }
        } catch (IOException error) {
            LOGGER.error("No se pudo crear cola de arena", error);
        }
    }

    private Path arenaSettingsPath() {
        return Path.of(".").toAbsolutePath().normalize()
            .resolve("config")
            .resolve("minecraft_live_arena")
            .resolve("arena_settings.json");
    }

    private void loadArenaSettings() {
        Path path = arenaSettingsPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject settings = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            shrinkSeconds = Mth.clamp(number(settings, "shrinkSeconds", SHRINK_SECONDS), 5, 300);
            shrinkBlocks = Mth.clamp(number(settings, "shrinkBlocks", SHRINK_BLOCKS), 1, 16);
            minimumRadius = Mth.clamp(number(settings, "minimumRadius", MINIMUM_RADIUS), 2, INITIAL_RADIUS);
            int savedInitialRadius = number(settings, "initialRadius", INITIAL_RADIUS);
            initialRadius = Mth.clamp(savedInitialRadius, minimumRadius, INITIAL_RADIUS);
            currentRadius = Mth.clamp(number(settings, "currentRadius", initialRadius), minimumRadius, initialRadius);
            startingHealth = Mth.clamp(number(settings, "startingHealth", DEFAULT_STARTING_HEALTH), MIN_STARTING_HEALTH, MAX_STARTING_HEALTH);
            fightCountdownSeconds = Mth.clamp(number(settings, "fightCountdownSeconds", DEFAULT_FIGHT_COUNTDOWN_SECONDS), MIN_FIGHT_COUNTDOWN_SECONDS, MAX_FIGHT_COUNTDOWN_SECONDS);
            normalizeArenaRadii();
        } catch (Exception error) {
            LOGGER.warn("No se pudo cargar arena_settings.json", error);
        }
    }

    private void saveArenaSettings() {
        JsonObject settings = new JsonObject();
        settings.addProperty("shrinkSeconds", shrinkSeconds);
        settings.addProperty("shrinkBlocks", shrinkBlocks);
        settings.addProperty("minimumRadius", minimumRadius);
        settings.addProperty("initialRadius", initialRadius);
        settings.addProperty("currentRadius", currentRadius);
        settings.addProperty("startingHealth", startingHealth);
        settings.addProperty("fightCountdownSeconds", fightCountdownSeconds);
        try {
            Path path = arenaSettingsPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOGGER.warn("No se pudo guardar arena_settings.json", error);
        }
    }

    private void normalizeArenaRadii() {
        minimumRadius = Mth.clamp(minimumRadius, 2, INITIAL_RADIUS);
        initialRadius = Mth.clamp(initialRadius, minimumRadius, INITIAL_RADIUS);
        currentRadius = Mth.clamp(currentRadius, minimumRadius, initialRadius);
    }

    private Path podiumSettingsPath() {
        return Path.of(".").toAbsolutePath().normalize()
            .resolve("config")
            .resolve("minecraft_live_arena")
            .resolve("podium_settings.json");
    }

    private void loadPodiumSettings() {
        Path path = podiumSettingsPath();
        if (!Files.exists(path)) {
            podiums.putIfAbsent(1, defaultPodium());
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            activePodium = Math.max(1, number(root, "activePodium", 1));
            randomPodium = root.has("randomPodium") && root.get("randomPodium").getAsBoolean();
            podiums.clear();
            JsonObject savedPodiums = root.has("podiums") && root.get("podiums").isJsonObject() ? root.getAsJsonObject("podiums") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : savedPodiums.entrySet()) {
                try {
                    podiums.put(Integer.parseInt(entry.getKey()), podiumFromJson(entry.getValue().getAsJsonObject()));
                } catch (Exception ignored) {
                }
            }
            podiums.putIfAbsent(1, defaultPodium());
            if (migrateLegacyDefaultPodiums()) {
                savePodiumSettings();
            }
        } catch (Exception error) {
            LOGGER.warn("No se pudo cargar podium_settings.json", error);
            podiums.putIfAbsent(1, defaultPodium());
        }
    }

    private void savePodiumSettings() {
        JsonObject root = new JsonObject();
        root.addProperty("activePodium", activePodium);
        root.addProperty("randomPodium", randomPodium);
        JsonObject savedPodiums = new JsonObject();
        for (Map.Entry<Integer, PodiumConfig> entry : podiums.entrySet()) {
            savedPodiums.add(String.valueOf(entry.getKey()), podiumToJson(entry.getValue()));
        }
        root.add("podiums", savedPodiums);
        try {
            Path path = podiumSettingsPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOGGER.warn("No se pudo guardar podium_settings.json", error);
        }
    }

    private Path scoreboardSettingsPath() {
        return Path.of(".").toAbsolutePath().normalize()
            .resolve("config")
            .resolve("minecraft_live_arena")
            .resolve("scoreboard_settings.json");
    }

    private void loadScoreboardSettings() {
        Path path = scoreboardSettingsPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            scoreboardSidebarVisible = !root.has("sidebarVisible") || root.get("sidebarVisible").getAsBoolean();
            scoreboardSaveWins = !root.has("saveWins") || root.get("saveWins").getAsBoolean();
            scoreboardHudRows = Mth.clamp(number(root, "hudRows", DEFAULT_SCOREBOARD_HUD_ROWS), MIN_SCOREBOARD_HUD_ROWS, MAX_SCOREBOARD_HUD_ROWS);
            scoreboardHudWidth = Mth.clamp(number(root, "hudWidth", DEFAULT_SCOREBOARD_HUD_WIDTH), MIN_SCOREBOARD_HUD_WIDTH, MAX_SCOREBOARD_HUD_WIDTH);
            scoreboardHudY = Mth.clamp(number(root, "hudY", DEFAULT_SCOREBOARD_HUD_Y), MIN_SCOREBOARD_HUD_Y, MAX_SCOREBOARD_HUD_Y);
            playerWins.clear();
            if (scoreboardSaveWins && root.has("wins") && root.get("wins").isJsonObject()) {
                JsonObject wins = root.getAsJsonObject("wins");
                for (Map.Entry<String, JsonElement> entry : wins.entrySet()) {
                    int amount = Math.max(0, entry.getValue().getAsInt());
                    if (amount > 0) {
                        playerWins.put(entry.getKey().toLowerCase(), amount);
                    }
                }
            }
        } catch (Exception error) {
            LOGGER.warn("No se pudo cargar scoreboard_settings.json", error);
        }
    }

    private void saveScoreboardSettings() {
        JsonObject root = new JsonObject();
        root.addProperty("sidebarVisible", scoreboardSidebarVisible);
        root.addProperty("saveWins", scoreboardSaveWins);
        root.addProperty("hudRows", scoreboardHudRows);
        root.addProperty("hudWidth", scoreboardHudWidth);
        root.addProperty("hudY", scoreboardHudY);
        JsonObject wins = new JsonObject();
        for (Map.Entry<String, Integer> entry : playerWins.entrySet()) {
            int amount = Math.max(0, entry.getValue());
            if (amount > 0) {
                wins.addProperty(entry.getKey(), amount);
            }
        }
        root.add("wins", wins);
        try {
            Path path = scoreboardSettingsPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOGGER.warn("No se pudo guardar scoreboard_settings.json", error);
        }
    }

    private int addWinnerWin(MinecraftServer server, String winnerKey) {
        if (winnerKey == null || winnerKey.isBlank()) {
            return 0;
        }
        String key = winnerKey.toLowerCase();
        int wins = Math.max(0, playerWins.getOrDefault(key, 0)) + 1;
        playerWins.put(key, wins);
        if (scoreboardSaveWins) {
            saveScoreboardSettings();
        }
        LivingEntity avatar = avatars.get(key);
        if (avatar != null) {
            updateAvatarName(avatar, key);
        }
        syncWinsDisplays(server);
        touchMenus();
        return wins;
    }

    private void resetScoreboardWins(MinecraftServer server) {
        playerWins.clear();
        saveScoreboardSettings();
        syncWinsDisplays(server);
        refreshAllAvatarNameplates(server);
        touchMenus();
        message(server, "Victorias reiniciadas");
    }

    private void syncWinsDisplays(MinecraftServer server) {
        syncWinsSidebar(server);
        sendWinsHudToAll(server);
        scoreboardHudSyncTicks = 0;
    }

    private void updateScoreboardHudSync(MinecraftServer server) {
        if (!scoreboardSidebarVisible || server.getPlayerList().getPlayerCount() <= 0) {
            scoreboardHudSyncTicks = 0;
            return;
        }
        scoreboardHudSyncTicks++;
        if (scoreboardHudSyncTicks >= 40) {
            scoreboardHudSyncTicks = 0;
            sendWinsHudToAll(server);
        }
    }

    private void refreshAllAvatarNameplates(MinecraftServer server) {
        for (Map.Entry<String, LivingEntity> entry : avatars.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isAlive()) {
                updateAvatarName(entry.getValue(), entry.getKey());
            }
        }
    }

    private List<Map.Entry<String, Integer>> topWinnerEntries(int limit) {
        return playerWins.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
            .sorted((left, right) -> {
                int byWins = Integer.compare(right.getValue(), left.getValue());
                if (byWins != 0) {
                    return byWins;
                }
                return left.getKey().compareTo(right.getKey());
            })
            .limit(limit)
            .toList();
    }

    private WinsHudPacket buildWinsHudPacket() {
        List<WinsHudEntry> entries = topWinnerEntries(scoreboardHudRows).stream()
            .map(entry -> new WinsHudEntry(entry.getKey(), Math.max(0, entry.getValue())))
            .toList();
        return new WinsHudPacket(scoreboardSidebarVisible, scoreboardHudRows, scoreboardHudWidth, scoreboardHudY, entries);
    }

    private void sendWinsHudToAll(MinecraftServer server) {
        if (server == null || server.getPlayerList().getPlayerCount() <= 0) {
            return;
        }
        NETWORK.send(PacketDistributor.ALL.noArg(), buildWinsHudPacket());
    }

    private void sendWinsHudToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        NETWORK.send(PacketDistributor.PLAYER.with(() -> player), buildWinsHudPacket());
    }

    private void syncWinsSidebar(MinecraftServer server) {
        syncWinsSidebar(server, false);
    }

    private void syncWinsSidebar(MinecraftServer server, boolean forceDisplay) {
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(WINS_OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(
                WINS_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("VICTORIAS").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER
            );
        }

        if (!scoreboardSidebarVisible) {
            if (scoreboard.getDisplayObjective(1) == objective) {
                scoreboard.setDisplayObjective(1, null);
            }
            clearSidebarWinEntries(scoreboard, objective, Set.of());
            return;
        }

        if (forceDisplay && scoreboard.getDisplayObjective(1) == objective) {
            scoreboard.setDisplayObjective(1, null);
        }
        if (scoreboard.getDisplayObjective(1) != objective) {
            scoreboard.setDisplayObjective(1, objective);
        }
        List<Map.Entry<String, Integer>> top = topWinnerEntries(15);
        Set<String> currentEntries = new HashSet<>();
        for (int index = 0; index < top.size(); index++) {
            Map.Entry<String, Integer> entry = top.get(index);
            String label = sidebarWinLabel(index + 1, entry.getKey());
            currentEntries.add(label);
            scoreboard.getOrCreatePlayerScore(label, objective).setScore(Math.max(0, entry.getValue()));
        }
        clearSidebarWinEntries(scoreboard, objective, currentEntries);
    }

    private void clearSidebarWinEntries(Scoreboard scoreboard, Objective objective, Set<String> currentEntries) {
        for (String oldEntry : new ArrayList<>(sidebarWinEntries)) {
            if (!currentEntries.contains(oldEntry)) {
                scoreboard.resetPlayerScore(oldEntry, objective);
                sidebarWinEntries.remove(oldEntry);
            }
        }
        sidebarWinEntries.addAll(currentEntries);
    }

    private String sidebarWinLabel(int rank, String key) {
        String label = "#" + rank + " " + key;
        return label.length() <= 32 ? label : label.substring(0, 32);
    }

    private PodiumConfig defaultPodium() {
        PodiumConfig podium = new PodiumConfig();
        podium.first = new SavedPos(100.5D, ARENA_Y + 3.0D, 0.5D, -0.450531F, 0.0F);
        podium.second = new SavedPos(99.5D, ARENA_Y + 2.0D, 0.5D, 15.600952F, 0.0F);
        podium.third = new SavedPos(101.5D, ARENA_Y + 1.0D, 0.5D, -9.748016F, 0.0F);
        podium.view = new SavedPos(100.55111659033027D, ARENA_Y + 2.02600202733202D, -4.997263390771822D, 0.6000081F, 12.600048F);
        return podium;
    }

    private boolean migrateLegacyDefaultPodiums() {
        boolean changed = false;
        for (Map.Entry<Integer, PodiumConfig> entry : new ArrayList<>(podiums.entrySet())) {
            if (isLegacyDefaultPodium(entry.getValue())) {
                podiums.put(entry.getKey(), defaultPodium());
                changed = true;
            }
        }
        return changed;
    }

    private boolean isLegacyDefaultPodium(PodiumConfig podium) {
        return podium != null
            && sameBlockishPos(podium.first, 100.5D, ARENA_Y + 3.0D, 0.5D)
            && sameBlockishPos(podium.second, 97.5D, ARENA_Y + 2.0D, 0.5D)
            && sameBlockishPos(podium.third, 103.5D, ARENA_Y + 1.0D, 0.5D)
            && sameBlockishPos(podium.view, 100.5D, ARENA_Y + 5.0D, -8.5D);
    }

    private boolean sameBlockishPos(SavedPos pos, double x, double y, double z) {
        return pos != null
            && Math.abs(pos.x - x) < 0.01D
            && Math.abs(pos.y - y) < 0.01D
            && Math.abs(pos.z - z) < 0.01D;
    }

    private JsonObject podiumToJson(PodiumConfig podium) {
        JsonObject json = new JsonObject();
        if (podium.first != null) json.add("first", posToJson(podium.first));
        if (podium.second != null) json.add("second", posToJson(podium.second));
        if (podium.third != null) json.add("third", posToJson(podium.third));
        if (podium.view != null) json.add("view", posToJson(podium.view));
        return json;
    }

    private PodiumConfig podiumFromJson(JsonObject json) {
        PodiumConfig podium = new PodiumConfig();
        if (json.has("first")) podium.first = posFromJson(json.getAsJsonObject("first"));
        if (json.has("second")) podium.second = posFromJson(json.getAsJsonObject("second"));
        if (json.has("third")) podium.third = posFromJson(json.getAsJsonObject("third"));
        if (json.has("view")) podium.view = posFromJson(json.getAsJsonObject("view"));
        return podium;
    }

    private JsonObject posToJson(SavedPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.x);
        json.addProperty("y", pos.y);
        json.addProperty("z", pos.z);
        json.addProperty("yaw", pos.yaw);
        json.addProperty("pitch", pos.pitch);
        return json;
    }

    private SavedPos posFromJson(JsonObject json) {
        return new SavedPos(
            json.has("x") ? json.get("x").getAsDouble() : 0.5D,
            json.has("y") ? json.get("y").getAsDouble() : ARENA_Y + 1.0D,
            json.has("z") ? json.get("z").getAsDouble() : 0.5D,
            json.has("yaw") ? json.get("yaw").getAsFloat() : 0.0F,
            json.has("pitch") ? json.get("pitch").getAsFloat() : 0.0F
        );
    }

    private void readQueue(MinecraftServer server) {
        Path path = queuePath(server);
        if (!Files.exists(path)) {
            ensureQueue(server);
            return;
        }

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (readOffset > file.length()) {
                readOffset = 0L;
            }
            file.seek(readOffset);
            String line;
            while ((line = file.readLine()) != null) {
                String utf8Line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8).trim();
                if (!utf8Line.isEmpty()) {
                    handleCommand(server, GSON.fromJson(utf8Line, JsonObject.class));
                }
            }
            readOffset = file.getFilePointer();
        } catch (Exception error) {
            LOGGER.error("Error leyendo cola de arena", error);
        }
    }

    private void handleCommand(MinecraftServer server, JsonObject command) {
        String action = text(command, "action");
        LOGGER.info("Ejecutando accion arena: {}", action);
        ServerLevel level = arenaLevel(server);
        if (level == null) {
            LOGGER.warn("No existe overworld para ejecutar accion {}", action);
            return;
        }
        switch (action) {
            case "reset_arena" -> resetArena(level);
            case "open_join_phase" -> openJoin(server);
            case "join_arena" -> joinArena(level, commandUsername(command));
            case "start_fight" -> startFight(server);
            case "force_end_round" -> endRound(server);
            case "move_arena_random" -> moveArenaRandom(level);
            case "give_sword", "give_sword_wood", "give_sword_iron", "give_sword_diamond", "give_sword_netherite" ->
                giveSword(server, commandUsername(command), swordMaterial(action, text(command, "swordMaterial")));
            case "give_armor", "give_armor_leather", "give_armor_chainmail", "give_armor_iron", "give_armor_diamond" ->
                giveArmor(server, commandUsername(command), armorMaterial(action, text(command, "armorMaterial")));
            case "heal_avatar" -> healAvatar(server, commandUsername(command), number(command, "healAmount", 4));
            case "give_totem" -> applyTotemCommand(server, command);
            case "give_shield" -> giveShield(server, commandUsername(command));
            case "clear_live_rewards" -> clearLiveRewards(server);
            default -> LOGGER.warn("Accion desconocida: {}", action);
        }
    }

    private void resetArena(ServerLevel level) {
        roundState = "idle";
        fightTickCounter = 0;
        fightCountdownTicks = 0;
        fightCountdownLastSecond = 0;
        roundWinnerKey = "";
        normalizeArenaRadii();
        currentRadius = initialRadius;
        arenaCenterX = BASE_ARENA_CENTER_X;
        arenaCenterZ = BASE_ARENA_CENTER_Z;
        eliminationOrder.clear();
        removeAvatars(level);
        avatars.clear();
        arenaFighters.clear();
        avatarNameplates.clear();
        avatarSkinNames.clear();
        avatarProfiles.clear();
        podiumSnapshots.clear();
        podiumDisplayAvatars.clear();
        attackCooldowns.clear();
        fighterAttackCooldowns.clear();
        pendingAttacks.clear();
        strafeDirections.clear();
        strafeTicks.clear();
        hurtRecoveryTicks.clear();
        shieldBlockTicks.clear();
        lastCombatActionTicks.clear();
        lastCombatWarnTicks.clear();
        totemCounts.clear();
        pendingLiveRewards.clear();
        clearFloatingCombatTexts();
        verticalVelocities.clear();
        arenaNotices.clear();
        randomArenaMovementEnabled = false;
        randomArenaTargetX = BASE_ARENA_CENTER_X;
        randomArenaTargetZ = BASE_ARENA_CENTER_Z;
        randomArenaMoveTicks = 0;
        stopCelebrationFireworks();
        activeNoticeTicks = 0;
        buildCombatPlatform(level, currentRadius);
        touchMenus();
        message(level.getServer(), "Arena reiniciada");
    }

    private GameProfile avatarProfile(String username, int index, String skinName) {
        String cleanName = profileName(username, index);
        GameProfile profile = new GameProfile(offlineUuid(username), cleanName);
        Property skin = skinTexture(skinName);
        if (skin != null) {
            profile.getProperties().put("textures", skin);
        }
        return profile;
    }

    private String randomCachedSkinName(String username, int index) {
        List<String> available = new ArrayList<>();
        for (String name : RANDOM_SKIN_NAMES) {
            if (skinCache.get(name) != null) {
                available.add(name);
            }
        }
        if (available.isEmpty()) {
            return RANDOM_SKIN_NAMES[Math.floorMod((username + index).hashCode(), RANDOM_SKIN_NAMES.length)];
        }
        return available.get(Math.floorMod((username + index + System.nanoTime()).hashCode(), available.size()));
    }

    private void preloadSkinTextures() {
        for (String name : RANDOM_SKIN_NAMES) {
            CompletableFuture.runAsync(() -> skinTexture(name));
        }
    }

    private Property skinTexture(String playerName) {
        if (skinCache.containsKey(playerName)) {
            return skinCache.get(playerName);
        }
        Property property = null;
        try {
            HttpRequest uuidRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HttpResponse<String> uuidResponse = HTTP_CLIENT.send(uuidRequest, HttpResponse.BodyHandlers.ofString());
            if (uuidResponse.statusCode() >= 200 && uuidResponse.statusCode() < 300 && !uuidResponse.body().isBlank()) {
                String id = GSON.fromJson(uuidResponse.body(), JsonObject.class).get("id").getAsString();
                skinUuidCache.put(playerName, dashedUuid(id));
                HttpRequest textureRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
                HttpResponse<String> textureResponse = HTTP_CLIENT.send(textureRequest, HttpResponse.BodyHandlers.ofString());
                if (textureResponse.statusCode() >= 200 && textureResponse.statusCode() < 300) {
                    JsonObject profile = GSON.fromJson(textureResponse.body(), JsonObject.class);
                    for (JsonElement element : profile.getAsJsonArray("properties")) {
                        JsonObject item = element.getAsJsonObject();
                        if ("textures".equals(item.get("name").getAsString())) {
                            property = new Property("textures", item.get("value").getAsString(), item.get("signature").getAsString());
                            break;
                        }
                    }
                }
            }
        } catch (Exception error) {
            LOGGER.debug("No se pudo cargar skin {}", playerName, error);
        }
        skinCache.put(playerName, property);
        return property;
    }

    private static UUID dashedUuid(String mojangId) {
        if (mojangId == null || mojangId.length() != 32) {
            return UUID.randomUUID();
        }
        return UUID.fromString(
            mojangId.substring(0, 8) + "-"
                + mojangId.substring(8, 12) + "-"
                + mojangId.substring(12, 16) + "-"
                + mojangId.substring(16, 20) + "-"
                + mojangId.substring(20)
        );
    }

    private ServerLevel arenaLevel(MinecraftServer server) {
        ServerLevel fallback = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (fallback == null) {
                fallback = level;
            }
            if ("minecraft:overworld".equals(level.dimension().location().toString())) {
                return level;
            }
        }
        return fallback;
    }

    private void openJoin(MinecraftServer server) {
        if ("fighting".equals(roundState) || "countdown".equals(roundState)) {
            message(server, "No se pueden abrir inscripciones con pelea activa");
            return;
        }
        roundWinnerKey = "";
        roundState = "joining";
        message(server, "Inscripciones abiertas: usa !unirse");
    }

    private void setArenaWeather(MinecraftServer server, String weather) {
        ServerLevel level = arenaLevel(server);
        if (level == null) {
            return;
        }
        switch (weather) {
            case "rain" -> {
                level.setWeatherParameters(0, 6000, true, false);
                message(server, "Clima: lluvia");
            }
            case "thunder" -> {
                level.setWeatherParameters(0, 6000, true, true);
                message(server, "Clima: tormenta");
            }
            default -> {
                level.setWeatherParameters(6000, 0, false, false);
                message(server, "Clima: despejado");
            }
        }
    }

    private void joinTestAvatar(ServerLevel level) {
        String previousState = roundState;
        roundState = "joining";
        joinArena(level, nextTestViewerName());
        roundState = previousState;
        touchMenus();
    }

    private String nextTestViewerName() {
        String username;
        do {
            username = "viewer_test_" + String.format("%02d", testViewerCounter++);
        } while (avatars.containsKey(username.toLowerCase()));
        return username;
    }

    private void joinArena(ServerLevel level, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (!"joining".equals(roundState)) {
            message(level.getServer(), username + " intento entrar fuera de inscripciones");
            return;
        }
        String key = username.toLowerCase();
        if (avatars.containsKey(key)) {
            return;
        }

        int index = avatars.size();
        String skinName = randomCachedSkinName(username, index);
        GameProfile profile = avatarProfile(username, index, skinName);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
        double radius = ThreadLocalRandom.current().nextDouble(3.0D, Math.max(4.0D, currentRadius - 8.0D));
        double x = arenaCenterX + Math.cos(angle) * radius;
        double z = arenaCenterZ + Math.sin(angle) * radius;
        double[] safe = findSafeRescuePoint(level, x, z, Math.max(2.0D, currentRadius - 3.0D));

        LivingEntity avatar = spawnStablePlayerAvatar(level, username, profile, safe[0], ARENA_Y + 1.0D, safe[1]);
        if (avatar == null) {
            message(level.getServer(), "No se pudo crear avatar para " + username);
            return;
        }
        equipBaseJoinLoadout(avatar, key);
        updateAvatarName(avatar, username);
        avatars.put(key, avatar);
        arenaFighters.put(key, avatar);
        avatarProfiles.put(key, profile);
        avatarSkinNames.put(key, skinName);
        attackCooldowns.put(key, 0);
        fighterAttackCooldowns.put(key, 0);
        pendingAttacks.remove(key);
        strafeDirections.put(key, Math.floorMod(key.hashCode(), 2) == 0 ? 1 : -1);
        strafeTicks.put(key, 20 + Math.floorMod(key.hashCode(), 30));
        hurtRecoveryTicks.put(key, 0);
        lastCombatActionTicks.put(key, level.getGameTime());
        lastCombatWarnTicks.put(key, 0L);
        totemCounts.put(key, 0);
        verticalVelocities.put(key, 0.0D);
        applyPendingLiveRewards(level.getServer(), key);
        ensureAvatarNameplate(level, key, avatar);
        updateAvatarName(avatar, key);
        touchMenus();
        LOGGER.info("Arena NPC registrado {} entityId={} uuid={} type={} pos=({}, {}, {}) map={} worldTagged={}",
            key,
            avatar.getId(),
            avatar.getUUID(),
            ForgeRegistries.ENTITY_TYPES.getKey(avatar.getType()),
            String.format("%.2f", avatar.getX()),
            String.format("%.2f", avatar.getY()),
            String.format("%.2f", avatar.getZ()),
            avatars.size(),
            countTaggedAvatars(level)
        );
        message(level.getServer(), username + " entro a la arena");
    }

    private void ensurePlayerOp(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ensureGameProfileOp(player.getServer(), player.getGameProfile(), player.getGameProfile().getName());
    }

    private void ensureGameProfileOp(MinecraftServer server, GameProfile profile, String username) {
        if (server == null || profile == null) {
            return;
        }
        try {
            if (!server.getPlayerList().isOp(profile)) {
                server.getPlayerList().op(profile);
                LOGGER.info("Jugador de arena {} marcado como OP ({})", username, profile.getName());
            }
        } catch (Exception error) {
            LOGGER.warn("No se pudo marcar como OP al jugador {} ({})", username, profile.getName(), error);
        }
    }

    private LivingEntity spawnStablePlayerAvatar(ServerLevel level, String username, GameProfile profile, double x, double y, double z) {
        ArenaServerPlayer avatar = new ArenaServerPlayer(level, profile);
        avatar.moveTo(x, y, z, 0.0F, 0.0F);
        avatar.setDeltaMovement(0.0D, -0.45D, 0.0D);
        avatar.fallDistance = 0.0F;
        avatar.addTag(AVATAR_TAG);
        avatar.setCustomName(Component.literal(username).withStyle(ChatFormatting.WHITE));
        avatar.setCustomNameVisible(true);
        if (avatar.getAttribute(Attributes.MAX_HEALTH) != null) {
            avatar.getAttribute(Attributes.MAX_HEALTH).setBaseValue(startingHealth);
            avatar.setHealth((float) startingHealth);
        }
        if (avatar.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            avatar.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28D);
        }
        if (avatar.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            avatar.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        }
        if (avatar.getAttribute(Attributes.ATTACK_SPEED) != null) {
            avatar.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.0D);
        }
        if (!level.addFreshEntity(avatar)) {
            LOGGER.warn("Arena avatar {} no pudo agregarse al mundo en ({}, {}, {})", username, x, y, z);
            return null;
        }
        spawnAvatarForClients(level.getServer(), avatar);
        return avatar;
    }

    private void startFight(MinecraftServer server) {
        if ("countdown".equals(roundState)) {
            message(server, "La pelea ya esta en cuenta regresiva");
            return;
        }
        if (!"joining".equals(roundState)) {
            message(server, "Primero abre inscripciones");
            return;
        }
        if (avatars.isEmpty()) {
            message(server, "No hay jugadores para iniciar pelea");
            return;
        }
        roundState = "countdown";
        fightCountdownTicks = fightCountdownSeconds * 20;
        fightCountdownLastSecond = fightCountdownSeconds;
        announceFightCountdown(server, fightCountdownSeconds);
        touchMenus();
    }

    private void updateFightCountdown(MinecraftServer server) {
        if (!"countdown".equals(roundState)) {
            return;
        }
        if (avatars.isEmpty()) {
            roundState = "joining";
            fightCountdownTicks = 0;
            fightCountdownLastSecond = 0;
            message(server, "Cuenta regresiva cancelada: no hay jugadores");
            touchMenus();
            return;
        }
        int secondsLeft = Math.max(1, (fightCountdownTicks + 19) / 20);
        if (secondsLeft != fightCountdownLastSecond) {
            announceFightCountdown(server, secondsLeft);
            fightCountdownLastSecond = secondsLeft;
        }
        fightCountdownTicks--;
        if (fightCountdownTicks <= 0) {
            fightCountdownTicks = 0;
            fightCountdownLastSecond = 0;
            beginFight(server);
        }
    }

    private void announceFightCountdown(MinecraftServer server, int secondsLeft) {
        enqueueArenaNotice(
            Component.literal(String.valueOf(secondsLeft)).withStyle(ChatFormatting.GOLD),
            Component.literal("La pelea comienza").withStyle(ChatFormatting.YELLOW),
            18
        );
        message(server, "La pelea comienza en " + secondsLeft);
        playCountdownSound(server, secondsLeft);
    }

    private void playCountdownSound(MinecraftServer server, int secondsLeft) {
        float pitch = Mth.clamp(0.75F + (fightCountdownSeconds - secondsLeft) * 0.04F, 0.75F, 1.45F);
        playArenaSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), 0.85F, pitch);
    }

    private void playFightStartSound(MinecraftServer server) {
        playArenaSound(server, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    private void playArenaSound(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
        if (server == null || sound == null) {
            return;
        }
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.getTags().contains(AVATAR_TAG)) {
                continue;
            }
            viewer.level().playSound(null, viewer.getX(), viewer.getY(), viewer.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    private void beginFight(MinecraftServer server) {
        if (avatars.isEmpty()) {
            roundState = "joining";
            message(server, "No hay jugadores para iniciar pelea");
            touchMenus();
            return;
        }
        roundState = "fighting";
        roundWinnerKey = "";
        fightTickCounter = 0;
        eliminationOrder.clear();
        podiumSnapshots.clear();
        stopCelebrationFireworks();
        clearPodiumDisplayAvatars(server);
        ServerLevel level = arenaLevel(server);
        Iterator<Map.Entry<String, LivingEntity>> iterator = avatars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LivingEntity> entry = iterator.next();
            LivingEntity player = entry.getValue();
            if (!player.isAlive()) {
                LOGGER.warn("Arena NPC {} removido al iniciar pelea: alive=false removed={} hp={} pos=({}, {}, {})",
                    entry.getKey(),
                    player.isRemoved(),
                    player.getHealth(),
                    String.format("%.2f", player.getX()),
                    String.format("%.2f", player.getY()),
                    String.format("%.2f", player.getZ())
                );
                iterator.remove();
                arenaFighters.remove(entry.getKey());
                forgetAttackState(entry.getKey());
                avatarProfiles.remove(entry.getKey());
                avatarSkinNames.remove(entry.getKey());
                totemCounts.remove(entry.getKey());
                verticalVelocities.remove(entry.getKey());
                despawnAvatar(player);
                touchMenus();
                continue;
            }
            if (player.getMainHandItem().isEmpty()) {
                setEquipment(player, EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            }
        }
        LOGGER.info("Arena pelea iniciada: avatars={} fighters={} worldTagged={}",
            avatars.size(),
            arenaFighters.size(),
            level == null ? -1 : countTaggedAvatars(level)
        );
        playFightStartSound(server);
        message(server, "Pelea iniciada");
    }

    private void endRound(MinecraftServer server) {
        roundState = "ended";
        fightTickCounter = 0;
        stopArenaMotion();
        stopWinnerCelebrationFireworks();
        showPodium(server);
        startPodiumCelebration(server);
        message(server, "Ronda terminada");
    }

    private void healAvatar(MinecraftServer server, String username, int amount) {
        LivingEntity player = avatar(username);
        if (player == null) {
            queuePendingHeal(server, username, amount);
            return;
        }
        if (player != null && player.isAlive()) {
            float beforeHealth = player.getHealth();
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
            showHealthDelta(player, player.getHealth() - beforeHealth);
            updateAvatarName(player, username);
            touchMenus();
        }
    }

    private void clearLiveRewards(MinecraftServer server) {
        victoriousStartEnabled.clear();
        pendingLiveRewards.clear();
        message(server, "Ventajas del live limpiadas");
    }

    private String swordMaterial(String action, String material) {
        String normalized = material == null ? "" : material.trim().toLowerCase();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return switch (action) {
            case "give_sword_iron" -> "iron";
            case "give_sword_diamond" -> "diamond";
            case "give_sword_netherite" -> "netherite";
            default -> "wood";
        };
    }

    private String armorMaterial(String action, String material) {
        String normalized = material == null ? "" : material.trim().toLowerCase();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return switch (action) {
            case "give_armor_chainmail" -> "chainmail";
            case "give_armor_iron" -> "iron";
            case "give_armor_diamond" -> "diamond";
            default -> "leather";
        };
    }

    private void giveSword(MinecraftServer server, String username, String material) {
        LivingEntity player = avatar(username);
        if (player == null) {
            queuePendingSword(server, username, material);
            return;
        }
        ItemStack sword = new ItemStack(switch (material) {
            case "iron" -> Items.IRON_SWORD;
            case "diamond" -> Items.DIAMOND_SWORD;
            case "netherite" -> Items.NETHERITE_SWORD;
            default -> Items.WOODEN_SWORD;
        });
        if (swordTier(player.getMainHandItem()) > swordTier(sword)) {
            message(server, username + " ya tiene una espada mejor");
            return;
        }
        setEquipment(player, EquipmentSlot.MAINHAND, sword);
        updateAvatarName(player, username);
        touchMenus();
        message(server, username + " recibio espada " + material);
    }

    private int swordTier(ItemStack stack) {
        if (stack.is(Items.NETHERITE_SWORD)) {
            return 4;
        }
        if (stack.is(Items.DIAMOND_SWORD)) {
            return 3;
        }
        if (stack.is(Items.IRON_SWORD)) {
            return 2;
        }
        if (stack.is(Items.STONE_SWORD)) {
            return 1;
        }
        if (stack.is(Items.WOODEN_SWORD)) {
            return 0;
        }
        return -1;
    }

    private void giveArmor(MinecraftServer server, String username, String material) {
        LivingEntity player = avatar(username);
        if (player == null) {
            queuePendingArmor(server, username, material);
            return;
        }
        if ("diamond".equals(material)) {
            setEquipment(player, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            setEquipment(player, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            setEquipment(player, EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            setEquipment(player, EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        } else if ("iron".equals(material)) {
            setEquipment(player, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            setEquipment(player, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            setEquipment(player, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            setEquipment(player, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        } else if ("chainmail".equals(material)) {
            setEquipment(player, EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            setEquipment(player, EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            setEquipment(player, EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            setEquipment(player, EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
        } else {
            setEquipment(player, EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            setEquipment(player, EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            setEquipment(player, EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            setEquipment(player, EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        }
        updateAvatarName(player, username);
        touchMenus();
        message(server, username + " recibio armadura " + material);
    }

    private void toggleShield(MinecraftServer server, String username) {
        LivingEntity player = avatar(username);
        if (player == null) {
            message(server, username + " no tiene avatar para recibir escudo");
            return;
        }
        if (player.getOffhandItem().is(Items.SHIELD)) {
            setEquipment(player, EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            message(server, username + " perdio escudo");
        } else {
            setEquipment(player, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            message(server, username + " recibio escudo");
        }
        updateAvatarName(player, username);
        touchMenus();
    }

    private void giveShield(MinecraftServer server, String username) {
        LivingEntity player = avatar(username);
        if (player == null) {
            queuePendingShield(server, username);
            return;
        }
        if (!player.getOffhandItem().is(Items.SHIELD)) {
            setEquipment(player, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
        updateAvatarName(player, username);
        touchMenus();
        message(server, username + " recibio escudo");
    }

    private void giveTotem(MinecraftServer server, String username, int amount) {
        LivingEntity player = avatar(username);
        if (player == null) {
            queuePendingTotem(server, username, amount);
            return;
        }
        String key = username.toLowerCase();
        int count = Math.max(0, totemCounts.getOrDefault(key, 0)) + Math.max(1, amount);
        totemCounts.put(key, count);
        updateAvatarName(player, username);
        touchMenus();
        message(server, username + " recibio totem x" + count);
    }

    private void applyTotemCommand(MinecraftServer server, JsonObject command) {
        String username = commandUsername(command);
        int quantity = number(command, "quantity", 0);
        if (quantity > 0) {
            giveTotem(server, username, quantity);
            return;
        }
        int total = number(command, "totems", 1);
        setTotemsAtLeast(server, username, total);
    }

    private void setTotemsAtLeast(MinecraftServer server, String username, int total) {
        int wanted = Math.max(1, total);
        LivingEntity player = avatar(username);
        if (player == null) {
            queuePendingTotemAtLeast(server, username, wanted);
            return;
        }
        String key = username.toLowerCase();
        int count = Math.max(Math.max(0, totemCounts.getOrDefault(key, 0)), wanted);
        totemCounts.put(key, count);
        updateAvatarName(player, username);
        touchMenus();
        message(server, username + " quedo con totem x" + count);
    }

    private void applyPendingLiveRewards(MinecraftServer server, String key) {
        PendingLiveReward pending = pendingLiveRewards.remove(key);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        if (!pending.swordMaterial().isBlank()) {
            giveSword(server, key, pending.swordMaterial());
        }
        if (!pending.armorMaterial().isBlank()) {
            giveArmor(server, key, pending.armorMaterial());
        }
        if (pending.totems() > 0) {
            giveTotem(server, key, pending.totems());
        }
        if (pending.shield()) {
            giveShield(server, key);
        }
        if (pending.healAmount() > 0) {
            healAvatar(server, key, pending.healAmount());
        }
        message(server, key + " recibio recompensas pendientes del LIVE");
    }

    private void queuePendingSword(MinecraftServer server, String username, String material) {
        String key = pendingRewardKey(username);
        if (key.isBlank()) {
            return;
        }
        PendingLiveReward current = pendingLiveRewards.getOrDefault(key, PendingLiveReward.empty());
        String bestMaterial = bestSwordMaterial(current.swordMaterial(), material);
        pendingLiveRewards.put(key, current.withSword(bestMaterial));
        touchMenus();
        message(server, username + " recibira espada " + bestMaterial + " al entrar");
    }

    private void queuePendingArmor(MinecraftServer server, String username, String material) {
        String key = pendingRewardKey(username);
        if (key.isBlank()) {
            return;
        }
        PendingLiveReward current = pendingLiveRewards.getOrDefault(key, PendingLiveReward.empty());
        String bestMaterial = bestArmorMaterial(current.armorMaterial(), material);
        pendingLiveRewards.put(key, current.withArmor(bestMaterial));
        touchMenus();
        message(server, username + " recibira armadura " + bestMaterial + " al entrar");
    }

    private void queuePendingTotem(MinecraftServer server, String username, int amount) {
        String key = pendingRewardKey(username);
        if (key.isBlank()) {
            return;
        }
        int add = Math.max(1, amount);
        PendingLiveReward current = pendingLiveRewards.getOrDefault(key, PendingLiveReward.empty());
        int total = Math.min(MAX_PENDING_TOTEMS, current.totems() + add);
        pendingLiveRewards.put(key, current.withTotems(total));
        touchMenus();
        message(server, username + " recibira totem x" + total + " al entrar");
    }

    private void queuePendingTotemAtLeast(MinecraftServer server, String username, int totalWanted) {
        String key = pendingRewardKey(username);
        if (key.isBlank()) {
            return;
        }
        PendingLiveReward current = pendingLiveRewards.getOrDefault(key, PendingLiveReward.empty());
        int total = Math.min(MAX_PENDING_TOTEMS, Math.max(current.totems(), Math.max(1, totalWanted)));
        pendingLiveRewards.put(key, current.withTotems(total));
        touchMenus();
        message(server, username + " recibira totem x" + total + " al entrar");
    }

    private void queuePendingShield(MinecraftServer server, String username) {
        String key = pendingRewardKey(username);
        if (key.isBlank()) {
            return;
        }
        PendingLiveReward current = pendingLiveRewards.getOrDefault(key, PendingLiveReward.empty());
        pendingLiveRewards.put(key, current.withShield(true));
        touchMenus();
        message(server, username + " recibira escudo al entrar");
    }

    private void queuePendingHeal(MinecraftServer server, String username, int amount) {
        String key = pendingRewardKey(username);
        if (key.isBlank()) {
            return;
        }
        int add = Math.max(1, amount);
        PendingLiveReward current = pendingLiveRewards.getOrDefault(key, PendingLiveReward.empty());
        int total = Math.min(MAX_PENDING_HEAL, current.healAmount() + add);
        pendingLiveRewards.put(key, current.withHealAmount(total));
        touchMenus();
        message(server, username + " recibira curacion pendiente al entrar");
    }

    private String pendingRewardKey(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String bestSwordMaterial(String current, String incoming) {
        return swordMaterialTier(incoming) >= swordMaterialTier(current) ? normalizeSwordMaterial(incoming) : normalizeSwordMaterial(current);
    }

    private String bestArmorMaterial(String current, String incoming) {
        return armorMaterialTier(incoming) >= armorMaterialTier(current) ? normalizeArmorMaterial(incoming) : normalizeArmorMaterial(current);
    }

    private String normalizeSwordMaterial(String material) {
        String normalized = material == null ? "" : material.trim().toLowerCase();
        return switch (normalized) {
            case "iron", "diamond", "netherite" -> normalized;
            default -> "wood";
        };
    }

    private String normalizeArmorMaterial(String material) {
        String normalized = material == null ? "" : material.trim().toLowerCase();
        return switch (normalized) {
            case "chainmail", "iron", "diamond" -> normalized;
            default -> "leather";
        };
    }

    private int swordMaterialTier(String material) {
        return switch (normalizeSwordMaterial(material)) {
            case "netherite" -> 4;
            case "diamond" -> 3;
            case "iron" -> 2;
            default -> 1;
        };
    }

    private int armorMaterialTier(String material) {
        return switch (normalizeArmorMaterial(material)) {
            case "diamond" -> 4;
            case "iron" -> 3;
            case "chainmail" -> 2;
            default -> 1;
        };
    }

    private void buildStaticArenaShell(ServerLevel level) {
        int buildRadius = GLASS_WALL_RADIUS + 2;
        int centerBlockX = Mth.floor(BASE_ARENA_CENTER_X);
        int centerBlockZ = Mth.floor(BASE_ARENA_CENTER_Z);
        for (int x = centerBlockX - buildRadius; x <= centerBlockX + buildRadius; x++) {
            for (int z = centerBlockZ - buildRadius; z <= centerBlockZ + buildRadius; z++) {
                double dx = (x + 0.5D) - BASE_ARENA_CENTER_X;
                double dz = (z + 0.5D) - BASE_ARENA_CENTER_Z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                for (int y = LAVA_Y; y <= ARENA_Y + ARENA_WALL_TOP_EXTRA + 1; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                if (ARENA_Y != LEGACY_ARENA_Y) {
                    for (int y = LEGACY_LAVA_Y; y <= LEGACY_ARENA_Y + 6; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                if (distance <= LAVA_RADIUS) {
                    for (int y = LAVA_Y; y <= ARENA_Y - LAVA_SURFACE_GAP; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.LAVA.defaultBlockState(), 3);
                    }
                }
                if (distance > GLASS_WALL_RADIUS - 0.75D && distance <= GLASS_WALL_RADIUS + 0.75D) {
                    for (int y = LAVA_Y; y <= ARENA_Y + ARENA_WALL_TOP_EXTRA; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.LIME_STAINED_GLASS.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void buildCombatPlatform(ServerLevel level, int radius) {
        int buildRadius = LAVA_RADIUS;
        int centerBlockX = Mth.floor(BASE_ARENA_CENTER_X);
        int centerBlockZ = Mth.floor(BASE_ARENA_CENTER_Z);
        for (int x = centerBlockX - buildRadius; x <= centerBlockX + buildRadius; x++) {
            for (int z = centerBlockZ - buildRadius; z <= centerBlockZ + buildRadius; z++) {
                double fixedDx = (x + 0.5D) - BASE_ARENA_CENTER_X;
                double fixedDz = (z + 0.5D) - BASE_ARENA_CENTER_Z;
                double fixedDistance = Math.sqrt(fixedDx * fixedDx + fixedDz * fixedDz);
                if (fixedDistance > LAVA_RADIUS) {
                    continue;
                }
                double dx = (x + 0.5D) - arenaCenterX;
                double dz = (z + 0.5D) - arenaCenterZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                BlockPos floor = new BlockPos(x, ARENA_Y, z);
                if (distance <= radius) {
                    level.setBlock(floor, arenaFloorBlock(distance, radius).defaultBlockState(), 3);
                } else {
                    level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private net.minecraft.world.level.block.Block arenaFloorBlock(double distance, int radius) {
        if (distance <= ARENA_CENTER_MARK_RADIUS) {
            return Blocks.POLISHED_BLACKSTONE;
        }
        if (distance >= Math.max(0.0D, radius - ARENA_EDGE_MARK_WIDTH)) {
            return Blocks.POLISHED_BLACKSTONE_BRICKS;
        }
        return Blocks.BLACKSTONE;
    }

    private void updateArenaShrink(MinecraftServer server) {
        if (!"fighting".equals(roundState)) {
            return;
        }
        fightTickCounter++;
        if (fightTickCounter < shrinkSeconds * 20) {
            return;
        }
        fightTickCounter = 0;
        if (currentRadius <= minimumRadius) {
            return;
        }

        currentRadius = Math.max(minimumRadius, currentRadius - shrinkBlocks);
        ServerLevel level = arenaLevel(server);
        if (level != null) {
            updateDynamicArenaCenter(level);
            buildCombatPlatform(level, currentRadius);
            message(server, "La arena se achico. Radio actual: " + currentRadius);
        }
    }

    private void updateDynamicArenaCenter(ServerLevel level) {
        int alive = 0;
        double totalX = 0.0D;
        double totalZ = 0.0D;
        for (LivingEntity player : avatars.values()) {
            if (!player.isAlive() || player.isRemoved()) {
                continue;
            }
            if (player.getY() < ARENA_Y + 0.90D || !hasArenaFloorAt(level, player.getX(), player.getZ())) {
                continue;
            }
            alive++;
            totalX += player.getX();
            totalZ += player.getZ();
        }
        if (alive == 0 || alive > DYNAMIC_CENTER_ALIVE_LIMIT) {
            return;
        }

        double targetX = totalX / alive;
        double targetZ = totalZ / alive;
        double dx = targetX - arenaCenterX;
        double dz = targetZ - arenaCenterZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= MAX_CENTER_MOVE_PER_SHRINK) {
            arenaCenterX = targetX;
            arenaCenterZ = targetZ;
            return;
        }
        arenaCenterX += (dx / distance) * MAX_CENTER_MOVE_PER_SHRINK;
        arenaCenterZ += (dz / distance) * MAX_CENTER_MOVE_PER_SHRINK;
    }

    private void moveArenaRandom(ServerLevel level) {
        randomArenaMovementEnabled = !randomArenaMovementEnabled;
        randomArenaMoveTicks = 0;
        if (randomArenaMovementEnabled) {
            chooseRandomArenaTarget();
            message(level.getServer(), "Movimiento aleatorio de arena activado");
        } else {
            message(level.getServer(), "Movimiento aleatorio de arena detenido");
        }
        touchMenus();
    }

    private void chooseRandomArenaTarget() {
        double maxOffset = Math.max(0.0D, LAVA_RADIUS - currentRadius - 3.0D);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
        double distance = ThreadLocalRandom.current().nextDouble(0.0D, maxOffset + 0.001D);
        randomArenaTargetX = BASE_ARENA_CENTER_X + Math.cos(angle) * distance;
        randomArenaTargetZ = BASE_ARENA_CENTER_Z + Math.sin(angle) * distance;
    }

    private void updateRandomArenaMovement(MinecraftServer server) {
        if (!randomArenaMovementEnabled) {
            return;
        }
        ServerLevel level = arenaLevel(server);
        if (level == null) {
            return;
        }
        randomArenaMoveTicks++;
        if (randomArenaMoveTicks < RANDOM_ARENA_MOVE_INTERVAL_TICKS) {
            return;
        }
        randomArenaMoveTicks = 0;

        double dx = randomArenaTargetX - arenaCenterX;
        double dz = randomArenaTargetZ - arenaCenterZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= RANDOM_ARENA_TARGET_EPSILON) {
            chooseRandomArenaTarget();
            return;
        }

        double step = Math.min(RANDOM_ARENA_MOVE_STEP, distance);
        arenaCenterX += (dx / distance) * step;
        arenaCenterZ += (dz / distance) * step;
        buildCombatPlatform(level, currentRadius);
        touchMenus();
    }

    private void updateCombatTargets(MinecraftServer server) {
        if (!"fighting".equals(roundState)) {
            return;
        }
        updateArenaFighterCombat(server);
    }

    private void updateArenaFighterCombat(MinecraftServer server) {
        List<Map.Entry<String, LivingEntity>> fighters = new ArrayList<>(arenaFighters.entrySet());
        ServerLevel level = arenaLevel(server);
        if (fighters.isEmpty()) {
            if (level != null && level.getGameTime() % 100L == 0L) {
                    LOGGER.warn("Arena combate sin fighters: avatars={} fighters={} worldTagged={}", avatars.size(), arenaFighters.size(), countTaggedAvatars(level));
            }
            return;
        }
        for (Map.Entry<String, LivingEntity> entry : fighters) {
            String key = entry.getKey();
            LivingEntity actor = entry.getValue();
            if (!arenaFighters.containsKey(key)) {
                continue;
            }
            if (actor == null || actor.isRemoved() || !actor.isAlive()) {
                LOGGER.warn("Arena fighter {} invalido en combate: null={} removed={} alive={} hp={}",
                    key,
                    actor == null,
                    actor != null && actor.isRemoved(),
                    actor != null && actor.isAlive(),
                    actor == null ? -1.0F : actor.getHealth()
                );
                removeAvatarByKey(server, key, true);
                continue;
            }
            long now = level == null ? 0L : level.getGameTime();

            int cooldown = Math.max(0, fighterAttackCooldowns.getOrDefault(key, 0) - 1);
            fighterAttackCooldowns.put(key, cooldown);

            int hurtRecovery = Math.max(0, hurtRecoveryTicks.getOrDefault(key, 0) - 1);
            hurtRecoveryTicks.put(key, hurtRecovery);

            if (updateShieldBlockState(server, key, actor)) {
                continue;
            }

            PendingAttack pending = pendingAttacks.get(key);
            if (pending != null) {
                LivingEntity pendingTarget = arenaFighters.get(pending.targetKey());
                if (pendingTarget == null || pendingTarget.isRemoved() || !pendingTarget.isAlive()) {
                    LOGGER.info("Arena ataque cancelado: {} target={} invalido removed={} alive={}",
                        key,
                        pending.targetKey(),
                        pendingTarget != null && pendingTarget.isRemoved(),
                        pendingTarget != null && pendingTarget.isAlive()
                    );
                    pendingAttacks.remove(key);
                    continue;
                }
                smoothLookAt(actor, pendingTarget, 18.0F);
                holdAttackPosition(actor);
                PendingAttack nextPending = pending.tick();
                if (nextPending.windupTicks() > 0) {
                    pendingAttacks.put(key, nextPending);
                    continue;
                }
                pendingAttacks.remove(key);
                resolveArenaAttack(server, key, actor, pending.targetKey(), pendingTarget, pending.totalTicks(), pending.initialWindupTicks(), pending.rangeSqr());
                continue;
            }

            if (hurtRecovery > 0) {
                LivingEntity target = nearestArenaFighter(key, actor);
                if (target != null) {
                    smoothLookAt(actor, target, 20.0F);
                }
                continueHurtKnockback(actor);
                continue;
            }

            LivingEntity target = nearestArenaFighter(key, actor);
            if (target == null) {
                if (level != null && level.getGameTime() % 100L == 0L) {
                    LOGGER.info("Arena fighter {} sin target: fighters={}", key, fighters.size());
                }
                continue;
            }
            String targetKey = avatarKey(target);
            double distanceSqr = actor.distanceToSqr(target);
            smoothLookAt(actor, target, 16.0F);
            moveArenaFighterToward(key, actor, target, distanceSqr);

            double attackRangeSqr = attackRangeSqr(key, actor);
            if (distanceSqr <= attackRangeSqr && cooldown <= 0) {
                int totalTicks = attackCooldownTicks(actor);
                int windupTicks = attackWindupTicks(totalTicks);
                pendingAttacks.put(key, new PendingAttack(targetKey, windupTicks, windupTicks, totalTicks, attackRangeSqr));
                fighterAttackCooldowns.put(key, totalTicks);
                lastCombatActionTicks.put(key, now);
                holdAttackPosition(actor);
                LOGGER.info("Arena ataque start: {} -> {} weapon={} attackSpeed={} cd={} windup={} range={}",
                    key,
                    targetKey,
                    ForgeRegistries.ITEMS.getKey(actor.getMainHandItem().getItem()),
                    String.format("%.2f", attackSpeed(actor)),
                    totalTicks,
                    windupTicks,
                    String.format("%.2f", Math.sqrt(attackRangeSqr))
                );
            } else if (cooldown <= 0) {
                closeCombatGapIfNeeded(actor, target, distanceSqr);
                logPossibleCombatStall(level, key, actor, target, actor.distanceToSqr(target), cooldown, hurtRecovery);
            }
        }
    }

    private double attackRangeSqr(String key, LivingEntity actor) {
        int phase = Math.floorMod(key.hashCode() + actor.tickCount / 13, 6);
        double range = ATTACK_RANGE_MIN + (phase * 0.10D);
        return range * range;
    }

    private void closeCombatGapIfNeeded(LivingEntity actor, LivingEntity target, double distanceSqr) {
        double distance = Math.sqrt(distanceSqr);
        double attackRange = Math.sqrt(ATTACK_RANGE_SQR);
        if (distance <= attackRange + 0.05D || distance > attackRange + 2.0D) {
            return;
        }
        double dx = (target.getX() - actor.getX()) / Math.max(0.001D, distance);
        double dz = (target.getZ() - actor.getZ()) / Math.max(0.001D, distance);
        double step = Math.min(0.16D, Math.max(0.04D, distance - attackRange + 0.04D));
        double[] safeMove = voluntaryArenaMove(actor, dx * step, dz * step);
        if (Math.abs(safeMove[0]) <= 0.001D && Math.abs(safeMove[1]) <= 0.001D) {
            return;
        }
        actor.move(MoverType.SELF, new Vec3(safeMove[0], 0.0D, safeMove[1]));
        actor.setDeltaMovement(safeMove[0], actor.getDeltaMovement().y, safeMove[1]);
        actor.hurtMarked = true;
    }

    private void logPossibleCombatStall(ServerLevel level, String key, LivingEntity actor, LivingEntity target, double distanceSqr, int cooldown, int hurtRecovery) {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        long lastAction = lastCombatActionTicks.getOrDefault(key, now);
        if (now - lastAction < COMBAT_STALL_LOG_TICKS) {
            return;
        }
        long lastWarn = lastCombatWarnTicks.getOrDefault(key, 0L);
        if (now - lastWarn < COMBAT_STALL_LOG_TICKS) {
            return;
        }
        lastCombatWarnTicks.put(key, now);
        LOGGER.warn("Arena posible stall: {} target={} dist={} cd={} hurt={} pending={} pos=({}, {}, {}) targetPos=({}, {}, {}) inside={} targetInside={}",
            key,
            avatarKey(target),
            String.format("%.2f", Math.sqrt(distanceSqr)),
            cooldown,
            hurtRecovery,
            pendingAttacks.containsKey(key),
            String.format("%.2f", actor.getX()),
            String.format("%.2f", actor.getY()),
            String.format("%.2f", actor.getZ()),
            String.format("%.2f", target.getX()),
            String.format("%.2f", target.getY()),
            String.format("%.2f", target.getZ()),
            isInsideArena(actor.getX(), actor.getZ()),
            isInsideArena(target.getX(), target.getZ())
        );
    }

    private void holdAttackPosition(LivingEntity actor) {
        if (actor instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        Vec3 motion = actor.getDeltaMovement();
        actor.setDeltaMovement(motion.x * 0.35D, motion.y, motion.z * 0.35D);
        actor.hurtMarked = true;
    }

    private void resolveArenaAttack(MinecraftServer server, String key, LivingEntity actor, String targetKey, LivingEntity target, int totalTicks, int windupTicks, double rangeSqr) {
        if (actor.distanceToSqr(target) > rangeSqr + 1.25D) {
            LOGGER.info("Arena ataque fallo: {} -> {} fuera de rango cycle={} windup={}", key, targetKey, totalTicks, windupTicks);
            return;
        }
        float beforeHealth = target.getHealth();
        playAttackLunge(actor, target);
        actor.swing(InteractionHand.MAIN_HAND, true);
        if (tryShieldBlock(server, key, actor, targetKey, target)) {
            return;
        }
        if (actor instanceof Mob mob) {
            mob.setTarget(target);
            mob.doHurtTarget(target);
        }
        if (target.getHealth() >= beforeHealth - 0.01F) {
            applyLivingCombatHit(server, actor, target, livingAttackDamage(actor));
        } else {
            broadcastHurt(server, target);
            showHealthDelta(target, target.getHealth() - beforeHealth);
            applyKnockback(actor, target);
        }
        markHurtRecovery(targetKey);
        if (actor.level() instanceof ServerLevel actionLevel) {
            lastCombatActionTicks.put(key, actionLevel.getGameTime());
            if (!targetKey.isBlank()) {
                lastCombatActionTicks.put(targetKey, actionLevel.getGameTime());
            }
        }
        spawnHitParticles(server, target);
        applyPostHitFootwork(key, actor, target);
        LOGGER.info("Arena golpe: {} -> {} hp {} -> {} raw={} final={} weapon={} cd={} windup={} range={} kb={} cdLeft={}",
            key,
            targetKey,
            String.format("%.1f", beforeHealth),
            String.format("%.1f", target.getHealth()),
            String.format("%.2f", livingAttackDamage(actor)),
            String.format("%.2f", computedCombatDamage(actor, target)),
            ForgeRegistries.ITEMS.getKey(actor.getMainHandItem().getItem()),
            totalTicks,
            windupTicks,
            String.format("%.2f", Math.sqrt(rangeSqr)),
            String.format("%.2f", knockbackPower(target)),
            fighterAttackCooldowns.getOrDefault(key, 0)
        );

        if (!target.isAlive() || target.getHealth() <= 0.0F) {
            handleArenaFighterDefeat(server, key, targetKey, target);
        } else if (!targetKey.isBlank()) {
            updateAvatarName(target, targetKey);
        }
    }

    private void playAttackLunge(LivingEntity actor, LivingEntity target) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001D) {
            return;
        }
        double lunge = Math.min(ATTACK_LUNGE_BLOCKS, Math.max(0.0D, (distance - 1.35D) * 0.35D));
        if (lunge <= 0.01D) {
            actor.hurtMarked = true;
            return;
        }
        double moveX = (dx / distance) * lunge;
        double moveZ = (dz / distance) * lunge;
        if (!isInsideArena(actor.getX() + moveX, actor.getZ() + moveZ)) {
            return;
        }
        actor.move(MoverType.SELF, new Vec3(moveX, 0.0D, moveZ));
        actor.setDeltaMovement(moveX * 0.5D, actor.getDeltaMovement().y, moveZ * 0.5D);
        actor.hurtMarked = true;
    }

    private void markHurtRecovery(String key) {
        if (key != null && !key.isBlank()) {
            hurtRecoveryTicks.put(key, HURT_RECOVERY_TICKS);
        }
    }

    private void continueHurtKnockback(LivingEntity actor) {
        Vec3 motion = actor.getDeltaMovement();
        double decay = currentRadius <= 10 ? 0.82D : 0.70D;
        double moveX = motion.x * decay;
        double moveZ = motion.z * decay;
        if (Math.abs(moveX) > 0.003D || Math.abs(moveZ) > 0.003D) {
            actor.move(MoverType.SELF, new Vec3(moveX, 0.0D, moveZ));
            actor.setDeltaMovement(moveX, Math.min(0.0D, motion.y), moveZ);
        }
        actor.hurtMarked = true;
    }

    private void spawnHitParticles(MinecraftServer server, LivingEntity target) {
        if (server == null || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, target.getX(), target.getY() + 1.0D, target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.65F, 1.05F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + 1.05D, target.getZ(), 3, 0.18D, 0.12D, 0.18D, 0.03D);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0D, target.getZ(), 3, 0.2D, 0.2D, 0.2D, 0.035D);
    }

    private LivingEntity nearestArenaFighter(String key, LivingEntity actor) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, LivingEntity> otherEntry : arenaFighters.entrySet()) {
            LivingEntity other = otherEntry.getValue();
            if (otherEntry.getKey().equals(key) || other == null || other.isRemoved() || !other.isAlive()) {
                continue;
            }
            double distance = actor.distanceToSqr(other);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    private void moveArenaFighterToward(String key, LivingEntity actor, LivingEntity target, double distanceSqr) {
        if (actor instanceof Mob mob) {
            mob.setTarget(target);
            if (distanceSqr > ATTACK_RANGE_SQR) {
                mob.getNavigation().moveTo(target, 1.15D);
            } else {
                mob.getNavigation().stop();
            }
        }

        double distance = Math.sqrt(distanceSqr);
        if (distance < 0.001D) {
            return;
        }
        double toTargetX = (target.getX() - actor.getX()) / distance;
        double toTargetZ = (target.getZ() - actor.getZ()) / distance;
        int ticks = Math.max(0, strafeTicks.getOrDefault(key, 0) - 1);
        if (ticks <= 0) {
            int direction = -strafeDirections.getOrDefault(key, 1);
            strafeDirections.put(key, direction);
            strafeTicks.put(key, 26 + Math.floorMod(key.hashCode() + actor.tickCount, 36));
        } else {
            strafeTicks.put(key, ticks);
        }

        double preferredDistance = preferredMeleeDistance(key);
        double aggression = aggressionFactor(key);
        double moveForward = distance > preferredDistance + 0.55D
            ? PLAYER_WALK_BLOCKS_PER_TICK * aggression
            : distance < preferredDistance - 0.34D ? -PLAYER_WALK_BLOCKS_PER_TICK * 0.48D : closeRangeDrift(key, actor);
        double strafe = distance <= preferredDistance + 1.25D
            ? strafeDirections.getOrDefault(key, 1) * PLAYER_STRAFE_BLOCKS_PER_TICK * strafeFactor(key, actor)
            : 0.0D;
        double moveX = toTargetX * moveForward + (-toTargetZ) * strafe;
        double moveZ = toTargetZ * moveForward + toTargetX * strafe;
        double edgeRatio = arenaEdgeRatio(actor);
        if (edgeRatio >= 0.72D) {
            double centerX = arenaCenterX - actor.getX();
            double centerZ = arenaCenterZ - actor.getZ();
            double centerDistance = Math.max(0.001D, Math.sqrt(centerX * centerX + centerZ * centerZ));
            double correction = PLAYER_WALK_BLOCKS_PER_TICK * (edgeRatio >= 0.88D ? 1.75D : 1.15D);
            moveX = moveX * 0.35D + (centerX / centerDistance) * correction;
            moveZ = moveZ * 0.35D + (centerZ / centerDistance) * correction;
        }
        double[] safeMove = voluntaryArenaMove(actor, moveX, moveZ);
        moveX = safeMove[0];
        moveZ = safeMove[1];
        actor.setDeltaMovement(moveX, actor.getDeltaMovement().y, moveZ);
        if (Math.abs(moveX) > 0.001D || Math.abs(moveZ) > 0.001D) {
            actor.move(MoverType.SELF, new Vec3(moveX, 0.0D, moveZ));
            actor.hurtMarked = true;
        }
    }

    private double preferredMeleeDistance(String key) {
        return 1.28D + (Math.floorMod(key.hashCode(), 58) / 100.0D);
    }

    private double aggressionFactor(String key) {
        return 0.82D + (Math.floorMod(key.hashCode() * 31, 36) / 100.0D);
    }

    private double strafeFactor(String key, LivingEntity actor) {
        int phase = Math.floorMod(key.hashCode() + actor.tickCount / 8, 10);
        if (phase <= 1) {
            return 0.18D;
        }
        return 0.45D + (Math.floorMod(key.hashCode() + actor.tickCount / 17, 35) / 100.0D);
    }

    private double closeRangeDrift(String key, LivingEntity actor) {
        int phase = Math.floorMod(key.hashCode() + actor.tickCount / 11, 12);
        if (phase <= 2) {
            return PLAYER_WALK_BLOCKS_PER_TICK * 0.12D;
        }
        if (phase == 3) {
            return -PLAYER_WALK_BLOCKS_PER_TICK * 0.08D;
        }
        return 0.0D;
    }

    private void applyPostHitFootwork(String key, LivingEntity actor, LivingEntity target) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        double distance = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        int direction = strafeDirections.getOrDefault(key, 1);
        double backstep = PLAYER_WALK_BLOCKS_PER_TICK * 0.72D;
        double sidestep = PLAYER_STRAFE_BLOCKS_PER_TICK * 0.58D * direction;
        double moveX = -(dx / distance) * backstep + (-dz / distance) * sidestep;
        double moveZ = -(dz / distance) * backstep + (dx / distance) * sidestep;
        double[] safeMove = voluntaryArenaMove(actor, moveX, moveZ);
        if (Math.abs(safeMove[0]) <= 0.001D && Math.abs(safeMove[1]) <= 0.001D) {
            return;
        }
        actor.move(MoverType.SELF, new Vec3(safeMove[0], 0.0D, safeMove[1]));
        actor.setDeltaMovement(safeMove[0], actor.getDeltaMovement().y, safeMove[1]);
        actor.hurtMarked = true;
    }

    private double arenaEdgeRatio(LivingEntity actor) {
        double dx = actor.getX() - arenaCenterX;
        double dz = actor.getZ() - arenaCenterZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double edgeLimit = Math.max(2.0D, currentRadius - 2.0D);
        return distance / edgeLimit;
    }

    private double[] voluntaryArenaMove(LivingEntity actor, double moveX, double moveZ) {
        double nextX = actor.getX() + moveX;
        double nextZ = actor.getZ() + moveZ;
        if (isInsideArenaWithPadding(nextX, nextZ, VOLUNTARY_EDGE_PADDING)) {
            return new double[] { moveX, moveZ };
        }
        double centerX = arenaCenterX - actor.getX();
        double centerZ = arenaCenterZ - actor.getZ();
        double length = Math.max(0.001D, Math.sqrt(centerX * centerX + centerZ * centerZ));
        double correctionX = (centerX / length) * PLAYER_WALK_BLOCKS_PER_TICK * 1.35D;
        double correctionZ = (centerZ / length) * PLAYER_WALK_BLOCKS_PER_TICK * 1.35D;
        if (!isInsideArena(actor.getX(), actor.getZ())) {
            return new double[] { correctionX, correctionZ };
        }
        if (isInsideArena(actor.getX() + correctionX, actor.getZ() + correctionZ)) {
            return new double[] { correctionX, correctionZ };
        }
        return new double[] { 0.0D, 0.0D };
    }

    private void handleArenaFighterDefeat(MinecraftServer server, String attackerKey, String defeatedKey, LivingEntity defeated) {
        if (defeatedKey.isBlank()) {
            return;
        }
        if (tryUseTotemRescue(server, defeated, defeatedKey)) {
            return;
        }
        ServerLevel level = arenaLevel(server);
        if (level != null) {
            spawnDeathParticles(level, defeated);
        }
        enqueueArenaNotice(
            Component.literal(defeatedKey + " eliminado").withStyle(ChatFormatting.RED),
            Component.literal("por " + attackerKey).withStyle(ChatFormatting.GRAY),
            30
        );
        message(server, defeatedKey + " eliminado por " + attackerKey);
        recordElimination(defeatedKey);
        removeAvatarByKey(server, defeatedKey, false);
        finishRoundIfWinner(server, attackerKey);
    }

    private String avatarKey(LivingEntity npc) {
        for (Map.Entry<String, LivingEntity> entry : arenaFighters.entrySet()) {
            if (entry.getValue() == npc) {
                return entry.getKey();
            }
        }
        return "";
    }

    private float livingAttackDamage(LivingEntity actor) {
        ItemStack stack = actor.getMainHandItem();
        if (stack.is(Items.NETHERITE_SWORD)) {
            return 8.0F;
        }
        if (stack.is(Items.DIAMOND_SWORD)) {
            return 7.0F;
        }
        if (stack.is(Items.IRON_SWORD)) {
            return 6.0F;
        }
        if (stack.is(Items.STONE_SWORD)) {
            return 5.0F;
        }
        if (stack.is(Items.WOODEN_SWORD)) {
            return 4.0F;
        }
        return 1.0F;
    }

    private void applyLivingCombatHit(MinecraftServer server, LivingEntity attacker, LivingEntity target, float rawDamage) {
        float finalDamage = computedCombatDamage(rawDamage, target);
        float beforeHealth = target.getHealth();
        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().mobAttack(attacker), 0.001F);
        target.setHealth(Math.max(0.0F, target.getHealth() - finalDamage));
        broadcastHurt(server, target);
        showHealthDelta(target, target.getHealth() - beforeHealth);
        applyKnockback(attacker, target);
    }

    private float computedCombatDamage(LivingEntity attacker, LivingEntity target) {
        return computedCombatDamage(livingAttackDamage(attacker), target);
    }

    private float computedCombatDamage(float rawDamage, LivingEntity target) {
        return CombatRules.getDamageAfterAbsorb(
            rawDamage,
            (float) target.getArmorValue(),
            (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
        ) * resistanceDamageMultiplier(target);
    }

    private float resistanceDamageMultiplier(LivingEntity target) {
        MobEffectInstance resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance == null) {
            return 1.0F;
        }
        int levels = resistance.getAmplifier() + 1;
        return Math.max(0.0F, 1.0F - (0.20F * levels));
    }

    private void lookAt(LivingEntity actor, LivingEntity target) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
        actor.yRotO = yaw;
        actor.yHeadRotO = yaw;
        actor.yBodyRotO = yaw;
    }

    private void smoothLookAt(LivingEntity actor, LivingEntity target, float maxStep) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float delta = Mth.clamp(Mth.wrapDegrees(targetYaw - actor.getYRot()), -maxStep, maxStep);
        float yaw = actor.getYRot() + delta;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
        actor.yRotO = yaw;
        actor.yHeadRotO = yaw;
        actor.yBodyRotO = yaw;
    }

    private void applyKnockback(LivingEntity attacker, LivingEntity target) {
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double power = knockbackPower(target);
        double pushX = (dx / length) * power;
        double pushZ = (dz / length) * power;
        target.knockback(power, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        target.setDeltaMovement(pushX, Math.min(0.0D, target.getDeltaMovement().y), pushZ);
        target.hurtMarked = true;
    }

    private double knockbackPower(LivingEntity target) {
        double dx = target.getX() - arenaCenterX;
        double dz = target.getZ() - arenaCenterZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double edgeLimit = Math.max(2.0D, currentRadius - 2.0D);
        double edgeRatio = distance / edgeLimit;
        double power = VANILLA_HURT_KNOCKBACK;
        if (currentRadius <= 10) {
            power *= 1.65D;
        } else if (currentRadius <= 16) {
            power *= 1.25D;
        }
        if (edgeRatio >= 0.92D) {
            return power * 1.35D;
        }
        if (edgeRatio >= 0.78D) {
            return power * 1.18D;
        }
        return power;
    }

    private void moveAvatarToSafeArenaPoint(LivingEntity npc, String key) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        double centerX = arenaCenterX;
        double centerZ = arenaCenterZ;
        double dx = npc.getX() - centerX;
        double dz = npc.getZ() - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double safeLimit = Math.max(0.0D, currentRadius - 3.0D);
        double targetRadius = Math.min(distance, safeLimit);
        if (distance < 0.001D) {
            double angle = (Math.floorMod(key.hashCode(), 360) * Math.PI) / 180.0D;
            targetRadius = Math.min(2.0D, safeLimit);
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            distance = 1.0D;
        }
        double wantedX = centerX + (dx / distance) * targetRadius;
        double wantedZ = centerZ + (dz / distance) * targetRadius;
        double[] safe = findSafeRescuePoint(level, wantedX, wantedZ, safeLimit);
        npc.setDeltaMovement(0.0D, 0.0D, 0.0D);
        npc.fallDistance = 0.0F;
        npc.moveTo(safe[0], ARENA_Y + 1.0D, safe[1], npc.getYRot(), 0.0F);
        npc.hurtMarked = true;
    }

    private void updateAvatarPhysics(MinecraftServer server) {
        ServerLevel level = arenaLevel(server);
        if (level == null) {
            return;
        }
        Iterator<Map.Entry<String, LivingEntity>> iterator = avatars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LivingEntity> entry = iterator.next();
            LivingEntity player = entry.getValue();
            if (!player.isAlive()) {
                LOGGER.warn("Arena NPC {} removido por fisica: alive=false removed={} hp={} pos=({}, {}, {})",
                    entry.getKey(),
                    player.isRemoved(),
                    player.getHealth(),
                    String.format("%.2f", player.getX()),
                    String.format("%.2f", player.getY()),
                    String.format("%.2f", player.getZ())
                );
                iterator.remove();
                avatarProfiles.remove(entry.getKey());
                avatarSkinNames.remove(entry.getKey());
                strafeDirections.remove(entry.getKey());
                strafeTicks.remove(entry.getKey());
                hurtRecoveryTicks.remove(entry.getKey());
                arenaFighters.remove(entry.getKey());
                forgetAttackState(entry.getKey());
                removeAvatarNameplate(server, entry.getKey());
                if (player instanceof ServerPlayer serverPlayer) {
                    removeHiddenNameTeam(server, serverPlayer);
                }
                despawnAvatar(player);
                touchMenus();
                continue;
            }
            if (level.getGameTime() % 10L == 0L) {
                updateAvatarName(player, entry.getKey());
            }
            if (!updateAvatarPhysicsAndHazards(server, level, entry.getKey(), player)) {
                iterator.remove();
                avatarProfiles.remove(entry.getKey());
                avatarSkinNames.remove(entry.getKey());
                strafeDirections.remove(entry.getKey());
                strafeTicks.remove(entry.getKey());
                hurtRecoveryTicks.remove(entry.getKey());
                arenaFighters.remove(entry.getKey());
                forgetAttackState(entry.getKey());
                removeAvatarNameplate(server, entry.getKey());
                if (player instanceof ServerPlayer serverPlayer) {
                    removeHiddenNameTeam(server, serverPlayer);
                }
                touchMenus();
            } else {
                updateAvatarNameplate(entry.getKey(), player);
            }
        }
        if (!avatars.isEmpty() && level.getGameTime() % 100L == 0L) {
            LOGGER.info("Arena NPC estado: avatars={} fighters={} worldTagged={} state={}",
                avatars.size(),
                arenaFighters.size(),
                countTaggedAvatars(level),
                roundState
            );
        }
    }

    private void updateCombatMovement(String key, ServerPlayer attacker, LivingEntity target, double distanceSqr) {
        double distance = Math.sqrt(distanceSqr);
        if (distance < 0.001D) {
            return;
        }
        lookAt(attacker, target);

        int ticks = Math.max(0, strafeTicks.getOrDefault(key, 0) - 1);
        if (ticks <= 0) {
            int direction = -strafeDirections.getOrDefault(key, 1);
            strafeDirections.put(key, direction);
            strafeTicks.put(key, 24 + Math.floorMod(key.hashCode() + attacker.tickCount, 34));
        } else {
            strafeTicks.put(key, ticks);
        }

        double toTargetX = (target.getX() - attacker.getX()) / distance;
        double toTargetZ = (target.getZ() - attacker.getZ()) / distance;
        double moveForward = distance > 3.0D ? PLAYER_WALK_BLOCKS_PER_TICK : distance < 1.7D ? -PLAYER_WALK_BLOCKS_PER_TICK * 0.65D : 0.03D;
        double strafe = strafeDirections.getOrDefault(key, 1) * PLAYER_STRAFE_BLOCKS_PER_TICK;
        double moveX = toTargetX * moveForward + (-toTargetZ) * strafe;
        double moveZ = toTargetZ * moveForward + toTargetX * strafe;

        if (!isInsideArena(attacker.getX() + moveX, attacker.getZ() + moveZ)) {
            moveX = toTargetX * PLAYER_WALK_BLOCKS_PER_TICK;
            moveZ = toTargetZ * PLAYER_WALK_BLOCKS_PER_TICK;
        }

        attacker.setPos(attacker.getX() + moveX, attacker.getY(), attacker.getZ() + moveZ);
        attacker.setDeltaMovement(moveX, 0.0D, moveZ);
        attacker.hurtMarked = true;
    }

    private boolean isInsideArena(double x, double z) {
        double dx = x - arenaCenterX;
        double dz = z - arenaCenterZ;
        double limit = Math.max(2.0D, currentRadius - 2.0D);
        return dx * dx + dz * dz <= limit * limit;
    }

    private boolean isInsideArenaWithPadding(double x, double z, double padding) {
        double dx = x - arenaCenterX;
        double dz = z - arenaCenterZ;
        double limit = Math.max(1.0D, currentRadius - 2.0D - padding);
        return dx * dx + dz * dz <= limit * limit;
    }

    private void finishRoundIfWinner(MinecraftServer server, String fallbackWinnerKey) {
        if (!"fighting".equals(roundState)) {
            return;
        }
        List<String> alive = aliveAvatarKeys();
        if (alive.size() > 1) {
            return;
        }
        roundState = "ended";
        fightTickCounter = 0;
        stopArenaMotion();
        pendingAttacks.clear();
        String winnerKey = alive.isEmpty() ? fallbackWinnerKey : alive.get(0);
        if (winnerKey != null && !winnerKey.isBlank()) {
            roundWinnerKey = winnerKey;
            capturePodiumSnapshot(winnerKey, avatars.getOrDefault(winnerKey, arenaFighters.get(winnerKey)));
            markWinnerReward(winnerKey);
            int wins = addWinnerWin(server, winnerKey);
            enqueueArenaNotice(
                Component.literal(winnerKey + " gano").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Victorias: " + wins).withStyle(ChatFormatting.YELLOW),
                55
            );
            message(server, "Ganador: " + winnerKey + " | victorias: " + wins);
            startWinnerCelebration(server, winnerKey);
        } else {
            message(server, "Ronda terminada sin ganador");
        }
        touchMenus();
    }

    private List<String> aliveAvatarKeys() {
        List<String> alive = new ArrayList<>();
        for (Map.Entry<String, LivingEntity> entry : avatars.entrySet()) {
            LivingEntity avatar = entry.getValue();
            if (avatar != null && avatar.isAlive() && !avatar.isRemoved()) {
                alive.add(entry.getKey());
            }
        }
        return alive;
    }

    private void markWinnerReward(String winnerKey) {
        if (KEEP_WINNER_START_REWARD && winnerKey != null && !winnerKey.isBlank()) {
            victoriousStartEnabled.put(winnerKey, true);
        }
    }

    private void stopArenaMotion() {
        randomArenaMovementEnabled = false;
        randomArenaMoveTicks = 0;
        randomArenaTargetX = arenaCenterX;
        randomArenaTargetZ = arenaCenterZ;
    }

    private void lookAt(ServerPlayer actor, LivingEntity target) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
        actor.yRotO = yaw;
        actor.yHeadRotO = yaw;
        actor.yBodyRotO = yaw;
    }

    private int attackCooldownTicks(LivingEntity player) {
        if (player instanceof Player realPlayer) {
            return Math.max(1, Mth.ceil(realPlayer.getCurrentItemAttackStrengthDelay()));
        }
        double attackSpeed = attackSpeed(player);
        return Math.max(1, Mth.ceil(20.0D / Math.max(0.1D, attackSpeed)));
    }

    private int attackWindupTicks(int totalTicks) {
        int windup = Mth.ceil(totalTicks * FAKE_PLAYER_ATTACK_WINDUP_RATIO);
        return Mth.clamp(windup, 3, Math.max(3, totalTicks - 2));
    }

    private float attackDamage(LivingEntity player) {
        return (float) Math.max(1.0D, itemModifiedAttribute(player, Attributes.ATTACK_DAMAGE, 1.0D));
    }

    private void applyPlayerCombatHit(MinecraftServer server, ServerPlayer attacker, LivingEntity target, float rawDamage) {
        float finalDamage = computedCombatDamage(rawDamage, target);
        float beforeHealth = target.getHealth();
        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().playerAttack(attacker), 0.001F);
        target.setHealth(Math.max(0.0F, target.getHealth() - finalDamage));
        broadcastHurt(server, target);
        showHealthDelta(target, target.getHealth() - beforeHealth);
        applyKnockback(attacker, target);
    }

    private double attackSpeed(LivingEntity player) {
        return Math.max(0.1D, itemModifiedAttribute(player, Attributes.ATTACK_SPEED, 4.0D));
    }

    private double itemModifiedAttribute(LivingEntity player, Attribute attribute, double baseValue) {
        double value = baseValue;
        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty()) {
            Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
            for (AttributeModifier modifier : modifiers.get(attribute)) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    value += modifier.getAmount();
                } else if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) {
                    value += baseValue * modifier.getAmount();
                } else if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                    value *= 1.0D + modifier.getAmount();
                }
            }
        }
        return value;
    }

    private void equipBaseJoinLoadout(LivingEntity avatar, String key) {
        setEquipment(avatar, EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        setEquipment(avatar, EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        setEquipment(avatar, EquipmentSlot.HEAD, ItemStack.EMPTY);
        setEquipment(avatar, EquipmentSlot.CHEST, ItemStack.EMPTY);
        setEquipment(avatar, EquipmentSlot.LEGS, ItemStack.EMPTY);
        setEquipment(avatar, EquipmentSlot.FEET, ItemStack.EMPTY);
        avatar.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
        boolean startsVictorious = victoriousStartEnabled.getOrDefault(key, false);
        if (startsVictorious) {
            setEquipment(avatar, EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        }
    }

    private void setEquipment(LivingEntity player, EquipmentSlot slot, ItemStack newStack) {
        ItemStack oldStack = player.getItemBySlot(slot);
        if (!oldStack.isEmpty() && player.getAttributes() != null) {
            player.getAttributes().removeAttributeModifiers(oldStack.getAttributeModifiers(slot));
        }
        player.setItemSlot(slot, newStack);
        if (!newStack.isEmpty() && player.getAttributes() != null) {
            player.getAttributes().addTransientAttributeModifiers(newStack.getAttributeModifiers(slot));
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
            broadcastEquipment(serverLevel.getServer(), serverPlayer);
        }
    }

    private LivingEntity avatar(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return avatars.get(username.toLowerCase());
    }

    private void applyKnockback(ServerPlayer attacker, LivingEntity target) {
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double power = knockbackPower(target);
        target.knockback(power, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        if (target instanceof ServerPlayer player) {
            double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double pushX = (dx / length) * power;
            double pushZ = (dz / length) * power;
            player.setPos(player.getX() + pushX, player.getY(), player.getZ() + pushZ);
        }
        target.hurtMarked = true;
    }

    private boolean updateAvatarPhysicsAndHazards(MinecraftServer server, ServerLevel level, String key, LivingEntity player) {
        if (isTouchingLava(level, player)) {
            return handleLavaDeathOrTotem(server, player, key);
        }

        player.fallDistance = 0.0F;
        applyAvatarGravity(level, key, player);
        if (isTouchingLava(level, player)) {
            return handleLavaDeathOrTotem(server, player, key);
        }
        return true;
    }

    private void applyAvatarGravity(ServerLevel level, String key, LivingEntity player) {
        if (hasGroundBelow(level, player)) {
            verticalVelocities.put(key, 0.0D);
            return;
        }

        double velocity = Math.max(-0.65D, verticalVelocities.getOrDefault(key, player.getDeltaMovement().y) - 0.08D);
        double nextY = player.getY() + velocity;
        if (velocity < 0.0D && player.getY() >= ARENA_Y + 0.99D && nextY <= ARENA_Y + 1.0D && hasArenaFloorAt(level, player.getX(), player.getZ())) {
            player.moveTo(player.getX(), ARENA_Y + 1.0D, player.getZ(), player.getYRot(), player.getXRot());
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
            verticalVelocities.put(key, 0.0D);
            player.hurtMarked = true;
            return;
        }

        player.move(MoverType.SELF, new Vec3(0.0D, velocity, 0.0D));
        player.setDeltaMovement(player.getDeltaMovement().x, velocity, player.getDeltaMovement().z);
        verticalVelocities.put(key, velocity);
        player.hurtMarked = true;
    }

    private boolean hasGroundBelow(ServerLevel level, LivingEntity player) {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.15D, player.getZ());
        var state = level.getBlockState(below);
        if (below.getY() == ARENA_Y && player.getY() < ARENA_Y + 0.99D) {
            return false;
        }
        return !state.isAir() && !state.is(Blocks.LAVA);
    }

    private boolean hasArenaFloorAt(ServerLevel level, double x, double z) {
        var state = level.getBlockState(BlockPos.containing(x, ARENA_Y, z));
        return !state.isAir() && !state.is(Blocks.LAVA);
    }

    private boolean isTouchingLava(ServerLevel level, LivingEntity player) {
        if (player.getY() <= LAVA_Y + 1.02D) {
            return true;
        }
        return level.getBlockState(BlockPos.containing(player.getX(), player.getY(), player.getZ())).is(Blocks.LAVA);
    }

    private boolean handleLavaDeathOrTotem(MinecraftServer server, LivingEntity player, String key) {
        if (tryUseTotemRescue(server, player, key)) {
            return true;
        }

        LOGGER.info("Arena lava muerte: {} hp={} pos=({}, {}, {}) radius={} inside={}",
            key,
            String.format("%.1f", player.getHealth()),
            String.format("%.2f", player.getX()),
            String.format("%.2f", player.getY()),
            String.format("%.2f", player.getZ()),
            currentRadius,
            isInsideArena(player.getX(), player.getZ())
        );
        player.setHealth(0.0F);
        ServerLevel level = arenaLevel(server);
        if (level != null) {
            spawnDeathParticles(level, player);
        }
        enqueueArenaNotice(
            Component.literal(key + " cayo en lava").withStyle(ChatFormatting.RED),
            Component.literal("Quedan " + aliveFightersAfter(key) + " jugadores").withStyle(ChatFormatting.GOLD),
            30
        );
        recordElimination(key);
        despawnAvatar(player);
        totemCounts.remove(key);
        verticalVelocities.remove(key);
        avatarProfiles.remove(key);
        avatarSkinNames.remove(key);
        arenaFighters.remove(key);
        forgetAttackState(key);
        touchMenus();
        message(server, player.getName().getString() + " murio en lava");
        return false;
    }

    private boolean tryUseTotemRescue(MinecraftServer server, LivingEntity player, String key) {
        int count = totemCounts.getOrDefault(key, 0);
        if (count > 0) {
            count--;
            totemCounts.put(key, count);
            if (server != null) {
                server.getPlayerList().broadcastAll(new ClientboundEntityEventPacket(player, (byte) 35));
            }
            float beforeHealth = player.getHealth();
            player.setHealth(player.getMaxHealth());
            showHealthDelta(player, player.getHealth() - beforeHealth);
            player.fallDistance = 0.0F;
            verticalVelocities.put(key, 0.0D);
            moveToSafeArenaPoint(player, key);
            updateAvatarName(player, key);
            touchMenus();
            enqueueArenaNotice(
                Component.literal(key + " uso totem").withStyle(ChatFormatting.GOLD),
                Component.literal("Vidas restantes: " + count).withStyle(ChatFormatting.YELLOW),
                36
            );
            message(server, player.getName().getString() + " uso totem. Vidas extra restantes: " + count);
            return true;
        }
        return false;
    }

    private void recordElimination(String key) {
        LivingEntity avatar = arenaFighters.get(key);
        if (avatar == null) {
            avatar = avatars.get(key);
        }
        capturePodiumSnapshot(key, avatar);
        if (key != null && !key.isBlank() && !eliminationOrder.contains(key)) {
            eliminationOrder.add(key);
        }
    }

    private void showPodium(MinecraftServer server) {
        ServerLevel level = arenaLevel(server);
        if (level == null) {
            return;
        }
        clearPodiumDisplayAvatars(server);
        PodiumConfig podium = selectedPodium();
        List<String> ranking = currentRanking();
        for (int index = 0; index < Math.min(3, ranking.size()); index++) {
            String key = ranking.get(index);
            LivingEntity avatar = avatars.get(key);
            if (avatar == null) {
                avatar = arenaFighters.get(key);
            }
            capturePodiumSnapshot(key, avatar);
        }
        removeAvatars(level);
        avatars.clear();
        arenaFighters.clear();
        avatarNameplates.clear();
        attackCooldowns.clear();
        fighterAttackCooldowns.clear();
        pendingAttacks.clear();
        strafeDirections.clear();
        strafeTicks.clear();
        hurtRecoveryTicks.clear();
        shieldBlockTicks.clear();
        lastCombatActionTicks.clear();
        lastCombatWarnTicks.clear();
        verticalVelocities.clear();
        moveRankingAvatar(level, ranking, 0, podium.first, podium.view);
        moveRankingAvatar(level, ranking, 1, podium.second, podium.view);
        moveRankingAvatar(level, ranking, 2, podium.third, podium.view);
        spawnPodiumNumber(level, activePodium, 1, podium.first, podium.view);
        spawnPodiumNumber(level, activePodium, 2, podium.second, podium.view);
        spawnPodiumNumber(level, activePodium, 3, podium.third, podium.view);
        if (podium.view != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.getTags().contains(AVATAR_TAG)) {
                    teleportTo(level, player, podium.view);
                }
            }
        }
    }

    private PodiumConfig selectedPodium() {
        if (!randomPodium) {
            return podiums.computeIfAbsent(activePodium, ignored -> defaultPodium());
        }
        List<PodiumConfig> complete = podiums.values().stream()
            .filter(podium -> podium.first != null || podium.second != null || podium.third != null)
            .toList();
        if (complete.isEmpty()) {
            return podiums.computeIfAbsent(activePodium, ignored -> defaultPodium());
        }
        return complete.get(ThreadLocalRandom.current().nextInt(complete.size()));
    }

    private List<String> currentRanking() {
        List<String> ranking = new ArrayList<>();
        if (roundWinnerKey != null && !roundWinnerKey.isBlank()) {
            ranking.add(roundWinnerKey);
        }
        avatars.entrySet().stream()
            .filter(entry -> entry.getValue().isAlive())
            .map(Map.Entry::getKey)
            .filter(key -> !ranking.contains(key))
            .forEach(ranking::add);
        for (int index = eliminationOrder.size() - 1; index >= 0 && ranking.size() < 3; index--) {
            String key = eliminationOrder.get(index);
            if (!ranking.contains(key)) {
                ranking.add(key);
            }
        }
        return ranking;
    }

    private void moveRankingAvatar(ServerLevel level, List<String> ranking, int index, SavedPos pos, SavedPos view) {
        if (index >= ranking.size() || pos == null) {
            return;
        }
        String key = ranking.get(index);
        SavedPos targetPos = facingPodiumView(pos, view);
        PodiumSnapshot snapshot = podiumSnapshots.get(key);
        if (snapshot == null) {
            snapshot = fallbackPodiumSnapshot(key);
            podiumSnapshots.put(key, snapshot);
        }
        if (snapshot != null) {
            spawnPodiumDisplayAvatar(level, snapshot, targetPos, index + 1);
        }
    }

    private boolean tryShieldBlock(MinecraftServer server, String attackerKey, LivingEntity attacker, String targetKey, LivingEntity target) {
        if (targetKey == null || targetKey.isBlank() || !target.getOffhandItem().is(Items.SHIELD)) {
            return false;
        }
        if (!isFacingAttacker(target, attacker)) {
            return false;
        }
        int phase = Math.floorMod(targetKey.hashCode() + target.tickCount / 5, 10);
        boolean nearEdge = arenaEdgeRatio(target) >= 0.72D;
        if (phase > (nearEdge ? 4 : 2)) {
            return false;
        }

        startShieldBlock(server, targetKey, target);
        target.swing(InteractionHand.OFF_HAND, true);
        if (target.level() instanceof ServerLevel level) {
            level.playSound(null, target.getX(), target.getY() + 1.0D, target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.85F, 0.95F);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.1D, target.getZ(), 4, 0.18D, 0.18D, 0.18D, 0.02D);
        }
        pushAttackerFromShield(attacker, target);
        if (server != null) {
            lastCombatActionTicks.put(attackerKey, server.overworld().getGameTime());
            lastCombatActionTicks.put(targetKey, server.overworld().getGameTime());
        }
        LOGGER.info("Arena escudo bloquea: {} bloqueo ataque de {}", targetKey, attackerKey);
        return true;
    }

    private boolean updateShieldBlockState(MinecraftServer server, String key, LivingEntity actor) {
        int ticks = Math.max(0, shieldBlockTicks.getOrDefault(key, 0) - 1);
        if (ticks > 0 && actor.getOffhandItem().is(Items.SHIELD)) {
            shieldBlockTicks.put(key, ticks);
            if (!actor.isUsingItem()) {
                actor.startUsingItem(InteractionHand.OFF_HAND);
            }
            LivingEntity target = nearestArenaFighter(key, actor);
            if (target != null) {
                smoothLookAt(actor, target, 24.0F);
            }
            holdAttackPosition(actor);
            actor.hurtMarked = true;
            if (actor instanceof ServerPlayer player) {
                broadcastEntityData(server, player);
            }
            return true;
        }
        if (shieldBlockTicks.remove(key) != null && actor.isUsingItem()) {
            actor.stopUsingItem();
            actor.hurtMarked = true;
            if (actor instanceof ServerPlayer player) {
                broadcastEntityData(server, player);
            }
        }
        return false;
    }

    private void startShieldBlock(MinecraftServer server, String key, LivingEntity target) {
        if (target == null || !target.getOffhandItem().is(Items.SHIELD)) {
            return;
        }
        target.startUsingItem(InteractionHand.OFF_HAND);
        shieldBlockTicks.put(key, 8);
        holdAttackPosition(target);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) {
            broadcastEntityData(server, player);
        }
    }

    private boolean isFacingAttacker(LivingEntity target, LivingEntity attacker) {
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double distance = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double lookX = -Math.sin(target.getYRot() * Math.PI / 180.0D);
        double lookZ = Math.cos(target.getYRot() * Math.PI / 180.0D);
        double dot = lookX * (dx / distance) + lookZ * (dz / distance);
        return dot > 0.18D;
    }

    private void pushAttackerFromShield(LivingEntity attacker, LivingEntity target) {
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double distance = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double push = 0.22D;
        double[] safeMove = voluntaryArenaMove(attacker, (dx / distance) * push, (dz / distance) * push);
        attacker.move(MoverType.SELF, new Vec3(safeMove[0], 0.0D, safeMove[1]));
        attacker.setDeltaMovement(safeMove[0], attacker.getDeltaMovement().y, safeMove[1]);
        attacker.hurtMarked = true;
    }

    private void capturePodiumSnapshot(String key, LivingEntity avatar) {
        if (key == null || key.isBlank() || avatar == null) {
            return;
        }
        GameProfile profile = avatarProfiles.get(key);
        if (profile == null && avatar instanceof ServerPlayer serverPlayer) {
            profile = serverPlayer.getGameProfile();
        }
        if (profile == null) {
            profile = avatarProfile(key, podiumSnapshots.size(), avatarSkinNames.getOrDefault(key, randomCachedSkinName(key, podiumSnapshots.size())));
        }
        podiumSnapshots.put(key, new PodiumSnapshot(
            key,
            profile,
            avatar.getItemBySlot(EquipmentSlot.MAINHAND),
            avatar.getItemBySlot(EquipmentSlot.OFFHAND),
            avatar.getItemBySlot(EquipmentSlot.HEAD),
            avatar.getItemBySlot(EquipmentSlot.CHEST),
            avatar.getItemBySlot(EquipmentSlot.LEGS),
            avatar.getItemBySlot(EquipmentSlot.FEET)
        ));
    }

    private PodiumSnapshot fallbackPodiumSnapshot(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        GameProfile profile = avatarProfiles.get(key);
        if (profile == null) {
            profile = avatarProfile(key, podiumSnapshots.size(), avatarSkinNames.getOrDefault(key, randomCachedSkinName(key, podiumSnapshots.size())));
        }
        return new PodiumSnapshot(
            key,
            profile,
            new ItemStack(Items.WOODEN_SWORD),
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
        );
    }

    private SavedPos facingPodiumView(SavedPos pos, SavedPos view) {
        if (view == null) {
            return pos;
        }
        double dx = view.x - pos.x;
        double dz = view.z - pos.z;
        if (Math.abs(dx) < 0.001D && Math.abs(dz) < 0.001D) {
            return pos;
        }
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F + PODIUM_FACE_YAW_OFFSET);
        return new SavedPos(pos.x, pos.y, pos.z, yaw, 0.0F);
    }

    private void spawnPodiumDisplayAvatar(ServerLevel level, PodiumSnapshot snapshot, SavedPos pos, int place) {
        ArenaServerPlayer avatar = new ArenaServerPlayer(level, snapshot.profile());
        avatar.moveTo(pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
        applyRotation(avatar, pos.yaw, pos.pitch);
        avatar.setDeltaMovement(0.0D, 0.0D, 0.0D);
        avatar.setNoGravity(true);
        avatar.fallDistance = 0.0F;
        avatar.addTag(AVATAR_TAG);
        avatar.setCustomName(Component.literal(snapshot.key()).withStyle(ChatFormatting.WHITE));
        avatar.setCustomNameVisible(true);
        if (avatar.getAttribute(Attributes.MAX_HEALTH) != null) {
            avatar.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
            avatar.setHealth(20.0F);
        }
        if (!level.addFreshEntity(avatar)) {
            LOGGER.warn("Podio no pudo recrear avatar {}", snapshot.key());
            return;
        }
        setEquipment(avatar, EquipmentSlot.MAINHAND, snapshot.mainHand().copy());
        setEquipment(avatar, EquipmentSlot.OFFHAND, snapshot.offHand().copy());
        setEquipment(avatar, EquipmentSlot.HEAD, snapshot.head().copy());
        setEquipment(avatar, EquipmentSlot.CHEST, snapshot.chest().copy());
        setEquipment(avatar, EquipmentSlot.LEGS, snapshot.legs().copy());
        setEquipment(avatar, EquipmentSlot.FEET, snapshot.feet().copy());
        spawnAvatarForClients(level.getServer(), avatar);
        podiumDisplayAvatars.add(avatar);
        podiumEmotePlaces.put(avatar.getUUID(), place);
        podiumEmoteCooldowns.put(avatar.getUUID(), 8 + place * 8);
        tryPlayPodiumEmote(avatar, place);
    }

    private List<Object> podiumEmoteAnimations() {
        if (podiumEmoteLoadAttempted) {
            return podiumEmoteAnimations;
        }
        podiumEmoteLoadAttempted = true;
        try {
            Class<?> api = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteAPI");
            Method getEmote = api.getMethod("getEmote", UUID.class);
            UUID[] preferred = {
                UUID.fromString("f9f6669d-f1b3-4170-8bc5-475a9a600439"),
                UUID.fromString("96506a5e-a69c-4a18-9add-d43dfd272fa6"),
                UUID.fromString("33b912f8-0aa0-45e6-a2d4-9b5677e6f35c")
            };
            for (UUID id : preferred) {
                Object animation = getEmote.invoke(null, id);
                if (animation != null && !podiumEmoteAnimations.contains(animation)) {
                    podiumEmoteAnimations.add(animation);
                }
            }
            Method getLoaded = api.getMethod("getLoadedEmotes");
            Object loaded = getLoaded.invoke(null);
            if (loaded instanceof Map<?, ?> loadedEmotes && !loadedEmotes.isEmpty()) {
                for (Object animation : loadedEmotes.values()) {
                    if (animation != null && !podiumEmoteAnimations.contains(animation)) {
                        podiumEmoteAnimations.add(animation);
                    }
                }
            }
            if (!podiumEmoteAnimations.isEmpty()) {
                LOGGER.info("Emotecraft: {} emotes de podio cargados", podiumEmoteAnimations.size());
            } else {
                LOGGER.warn("Emotecraft: no hay emotes cargados para el podio");
            }
        } catch (Throwable error) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            LOGGER.warn("Emotecraft no disponible para podio: {}", cause.toString());
        }
        return podiumEmoteAnimations;
    }

    private void tryPlayPodiumEmote(ServerPlayer avatar, int place) {
        List<Object> animations = podiumEmoteAnimations();
        if (animations.isEmpty()) {
            return;
        }
        Object animation = animations.get(ThreadLocalRandom.current().nextInt(animations.size()));
        if (animation == null) {
            return;
        }
        try {
            Class<?> animationClass = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
            Class<?> builderClass = Class.forName("io.github.kosmx.emotes.common.network.EmotePacket$Builder");
            Object builder = builderClass.getConstructor().newInstance();
            builderClass.getMethod("configureToStreamEmote", animationClass).invoke(builder, animation);
            Object packet = builderClass.getMethod("build").invoke(builder);
            Field dataField = packet.getClass().getField("data");
            Object netData = dataField.get(packet);
            netData.getClass().getField("player").set(netData, avatar.getUUID());
            netData.getClass().getField("isForced").setBoolean(netData, true);

            Class<?> serverNetwork = Class.forName("io.github.kosmx.emotes.forge.network.ServerNetwork");
            Method newPacket = serverNetwork.getMethod("newS2CEmotesPacket", netData.getClass(), ServerPlayer.class);
            int sent = 0;
            MinecraftServer server = avatar.getServer();
            if (server != null) {
                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    if (viewer == avatar || viewer.getTags().contains(AVATAR_TAG)) {
                        continue;
                    }
                    Object outbound = newPacket.invoke(null, netData, viewer);
                    viewer.connection.send((Packet<?>) outbound);
                    sent++;
                }
            }
            LOGGER.info("Emotecraft: paquete de emote de podio enviado a {} clientes para {}",
                sent,
                avatar.getScoreboardName());
        } catch (Throwable directError) {
            try {
                Class<?> api = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteAPI");
                Class<?> animationClass = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
                Method forcePlay = api.getMethod("forcePlayEmote", UUID.class, animationClass);
                forcePlay.invoke(null, avatar.getUUID(), animation);
                LOGGER.info("Emotecraft: emote de podio enviado a {} puesto {}", avatar.getScoreboardName(), place);
            } catch (Throwable apiError) {
                Throwable cause = apiError.getCause() != null ? apiError.getCause() : apiError;
                Throwable directCause = directError.getCause() != null ? directError.getCause() : directError;
                LOGGER.warn("Emotecraft no pudo animar podio para {}: directo={} api={}",
                    avatar.getScoreboardName(),
                    directCause.toString(),
                    cause.toString());
            }
        }
    }

    private void updatePodiumEmotes() {
        if (podiumEmotePlaces.isEmpty()) {
            return;
        }
        for (Entity entity : new ArrayList<>(podiumDisplayAvatars)) {
            if (!(entity instanceof ServerPlayer avatar)) {
                continue;
            }
            UUID id = avatar.getUUID();
            Integer place = podiumEmotePlaces.get(id);
            if (place == null || !avatar.isAlive() || avatar.isRemoved()) {
                podiumEmotePlaces.remove(id);
                podiumEmoteCooldowns.remove(id);
                continue;
            }
            int cooldown = podiumEmoteCooldowns.getOrDefault(id, 0) - 1;
            if (cooldown > 0) {
                podiumEmoteCooldowns.put(id, cooldown);
                continue;
            }
            tryPlayPodiumEmote(avatar, place);
            podiumEmoteCooldowns.put(id,
                PODIUM_EMOTE_MIN_COOLDOWN_TICKS + ThreadLocalRandom.current().nextInt(PODIUM_EMOTE_RANDOM_COOLDOWN_TICKS + 1));
        }
    }

    private void spawnPodiumNumber(ServerLevel level, int podiumId, int place, SavedPos pos, SavedPos view) {
        if (pos == null) {
            return;
        }
        removePodiumNumberMarker(podiumNumberKey(podiumId, place));
        int[] front = podiumBlockFace(pos, view);
        Display.TextDisplay number = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        number.addTag(NAMEPLATE_TAG);
        number.setNoGravity(true);
        number.setInvulnerable(true);
        number.moveTo(
            pos.x + front[0] * 0.515D,
            pos.y - 0.48D,
            pos.z + front[1] * 0.515D,
            yawForPodiumBlockFace(front[0], front[1]),
            0.0F);
        applyPodiumNumberNbt(number, place);
        if (level.addFreshEntity(number)) {
            podiumNumberMarkers.put(podiumNumberKey(podiumId, place), number);
            podiumDisplayAvatars.add(number);
        }
    }

    private void applyPodiumNumberNbt(Display.TextDisplay number, int place) {
        CompoundTag tag = number.saveWithoutId(new CompoundTag());
        Component text = Component.literal(podiumPlaceLabel(place)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        tag.putString("text", Component.Serializer.toJson(text));
        tag.putString("billboard", "fixed");
        tag.putFloat("view_range", 96.0F);
        tag.putInt("line_width", 120);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putInt("background", 0x00000000);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putBoolean("default_background", false);
        tag.putFloat("shadow_radius", 0.0F);
        tag.putFloat("shadow_strength", 1.0F);
        tag.putFloat("width", 1.2F);
        tag.putFloat("height", 0.8F);
        number.load(tag);
    }

    private int podiumPlaceForMode(String mode) {
        return switch (mode) {
            case "first" -> 1;
            case "second" -> 2;
            case "third" -> 3;
            default -> 0;
        };
    }

    private String podiumPlaceLabel(int place) {
        return switch (place) {
            case 1 -> "1ro";
            case 2 -> "2do";
            case 3 -> "3ro";
            default -> String.valueOf(place);
        };
    }

    private int[] podiumBlockFace(SavedPos pos, SavedPos view) {
        if (view == null) {
            return new int[] { 0, 1 };
        }
        double dx = view.x - pos.x;
        double dz = view.z - pos.z;
        if (Math.abs(dx) > Math.abs(dz)) {
            return new int[] { dx >= 0.0D ? 1 : -1, 0 };
        }
        return new int[] { 0, dz >= 0.0D ? 1 : -1 };
    }

    private float yawForPodiumBlockFace(int faceX, int faceZ) {
        if (faceX > 0) {
            return -90.0F;
        }
        if (faceX < 0) {
            return 90.0F;
        }
        if (faceZ < 0) {
            return 180.0F;
        }
        return 0.0F;
    }

    private String podiumNumberKey(int podiumId, int place) {
        return podiumId + ":" + place;
    }

    private void removePodiumNumberMarker(String key) {
        Display.TextDisplay existing = podiumNumberMarkers.remove(key);
        if (existing != null) {
            podiumDisplayAvatars.remove(existing);
            despawnAvatar(existing);
        }
    }

    private void removePodiumNumbersForPodium(int podiumId) {
        for (int place = 1; place <= 3; place++) {
            removePodiumNumberMarker(podiumNumberKey(podiumId, place));
        }
    }

    private void refreshPodiumNumbers(ServerLevel level, int podiumId, PodiumConfig podium) {
        spawnPodiumNumber(level, podiumId, 1, podium.first, podium.view);
        spawnPodiumNumber(level, podiumId, 2, podium.second, podium.view);
        spawnPodiumNumber(level, podiumId, 3, podium.third, podium.view);
    }

    private void clearPodiumDisplayAvatars(MinecraftServer server) {
        for (Entity entity : new ArrayList<>(podiumDisplayAvatars)) {
            if (entity instanceof ServerPlayer player && server != null) {
                removeHiddenNameTeam(server, player);
                server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
                podiumEmoteCooldowns.remove(player.getUUID());
                podiumEmotePlaces.remove(player.getUUID());
            }
            despawnAvatar(entity);
        }
        podiumDisplayAvatars.clear();
        podiumNumberMarkers.clear();
        podiumEmoteCooldowns.clear();
        podiumEmotePlaces.clear();
    }

    private void teleportTo(ServerLevel level, LivingEntity player, SavedPos pos) {
        if (player.level() != level) {
            player.changeDimension(level);
        }
        player.moveTo(pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
        applyRotation(player, pos.yaw, pos.pitch);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    private void teleportTo(ServerLevel level, ServerPlayer player, SavedPos pos) {
        player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
        applyRotation(player, pos.yaw, pos.pitch);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    private void applyRotation(LivingEntity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
        entity.yHeadRotO = yaw;
        entity.yBodyRotO = yaw;
    }

    private void moveToSafeArenaPoint(LivingEntity player, String key) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();
        double centerX = arenaCenterX;
        double centerZ = arenaCenterZ;
        double dx = fromX - centerX;
        double dz = fromZ - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double safeLimit = Math.max(0.0D, currentRadius - 3.0D);
        double targetRadius = Math.min(distance, safeLimit);

        if (distance < 0.001D) {
            double angle = (Math.floorMod(key.hashCode(), 360) * Math.PI) / 180.0D;
            targetRadius = Math.min(2.0D, safeLimit);
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            distance = 1.0D;
        }

        double wantedX = centerX + (dx / distance) * targetRadius;
        double wantedZ = centerZ + (dz / distance) * targetRadius;
        double[] safe = findSafeRescuePoint(level, wantedX, wantedZ, safeLimit);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.moveTo(safe[0], ARENA_Y + 1.0D, safe[1], player.getYRot(), 0.0F);
        player.hurtMarked = true;
        LOGGER.info("Totem rescue {}: from ({}, {}, {}) to ({}, {}, {}), arenaRadius={}",
            key,
            String.format("%.2f", fromX),
            String.format("%.2f", fromY),
            String.format("%.2f", fromZ),
            String.format("%.2f", safe[0]),
            ARENA_Y + 1,
            String.format("%.2f", safe[1]),
            currentRadius
        );
    }

    private double[] findSafeRescuePoint(ServerLevel level, double wantedX, double wantedZ, double safeLimit) {
        int startX = Mth.floor(wantedX);
        int startZ = Mth.floor(wantedZ);
        double bestX = 0.5D;
        double bestZ = 0.5D;
        double bestDistance = Double.MAX_VALUE;

        for (int radius = 0; radius <= 8; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }
                    int blockX = startX + offsetX;
                    int blockZ = startZ + offsetZ;
                    double x = blockX + 0.5D;
                    double z = blockZ + 0.5D;
                    double arenaDistance = Math.sqrt(Math.pow(x - arenaCenterX, 2.0D) + Math.pow(z - arenaCenterZ, 2.0D));
                    if (arenaDistance > safeLimit) {
                        continue;
                    }
                    if (!isSafeRescueBlock(level, blockX, blockZ)) {
                        continue;
                    }
                    double candidateDistance = Math.pow(x - wantedX, 2.0D) + Math.pow(z - wantedZ, 2.0D);
                    if (candidateDistance < bestDistance) {
                        bestDistance = candidateDistance;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }
            if (bestDistance < Double.MAX_VALUE) {
                return new double[] { bestX, bestZ };
            }
        }

        return new double[] { arenaCenterX, arenaCenterZ };
    }

    private boolean isSafeRescueBlock(ServerLevel level, int blockX, int blockZ) {
        BlockPos floor = new BlockPos(blockX, ARENA_Y, blockZ);
        BlockPos feet = new BlockPos(blockX, ARENA_Y + 1, blockZ);
        BlockPos head = new BlockPos(blockX, ARENA_Y + 2, blockZ);
        return !level.getBlockState(floor).isAir()
            && !level.getBlockState(floor).is(Blocks.LAVA)
            && !level.getBlockState(feet).is(Blocks.LAVA)
            && !level.getBlockState(head).is(Blocks.LAVA);
    }

    private void broadcastHurt(MinecraftServer server, LivingEntity target) {
        if (server != null) {
            server.getPlayerList().broadcastAll(new ClientboundHurtAnimationPacket(target));
        }
    }

    private void showHealthDelta(LivingEntity target, float delta) {
        if (target == null || Math.abs(delta) < 0.05F || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        String amount = formatHealthDelta(Math.abs(delta));
        Component text = Component.literal((delta > 0.0F ? "+" : "-") + amount)
            .withStyle(delta > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD);
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.addTag(COMBAT_TEXT_TAG);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        double x = target.getX() + ThreadLocalRandom.current().nextDouble(-0.22D, 0.22D);
        double y = target.getY() + 2.38D;
        double z = target.getZ() + ThreadLocalRandom.current().nextDouble(-0.22D, 0.22D);
        display.moveTo(x, y, z, target.getYRot(), 0.0F);
        applyFloatingCombatTextNbt(display, text);
        if (level.addFreshEntity(display)) {
            floatingCombatTexts.add(new FloatingCombatText(display, x, y, z, 0, COMBAT_TEXT_TICKS));
        }
    }

    private String formatHealthDelta(float amount) {
        if (Math.abs(amount - Math.round(amount)) < 0.05F) {
            return String.valueOf(Math.max(1, Math.round(amount)));
        }
        return String.format("%.1f", amount);
    }

    private void applyFloatingCombatTextNbt(Display.TextDisplay display, Component text) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(text));
        tag.putString("billboard", "center");
        tag.putString("alignment", "center");
        tag.putFloat("view_range", 80.0F);
        tag.putInt("line_width", 80);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putInt("background", 0x00000000);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putBoolean("default_background", false);
        tag.putFloat("width", 1.0F);
        tag.putFloat("height", 0.35F);
        display.load(tag);
    }

    private void updateFloatingCombatTexts() {
        Iterator<FloatingCombatText> iterator = floatingCombatTexts.iterator();
        while (iterator.hasNext()) {
            FloatingCombatText text = iterator.next();
            if (text.display == null || text.display.isRemoved() || text.age >= text.maxAge) {
                if (text.display != null && !text.display.isRemoved()) {
                    text.display.discard();
                }
                iterator.remove();
                continue;
            }
            text.age++;
            double progress = text.age / (double) Math.max(1, text.maxAge);
            text.display.moveTo(text.x, text.y + progress * 0.72D, text.z, text.display.getYRot(), 0.0F);
            text.display.hurtMarked = true;
        }
    }

    private void clearFloatingCombatTexts() {
        for (FloatingCombatText text : floatingCombatTexts) {
            if (text.display != null && !text.display.isRemoved()) {
                text.display.discard();
            }
        }
        floatingCombatTexts.clear();
    }

    private void spawnDeathParticles(ServerLevel level, LivingEntity entity) {
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.9D, entity.getZ(), 18, 0.35D, 0.45D, 0.35D, 0.03D);
        level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.8D, entity.getZ(), 14, 0.3D, 0.35D, 0.3D, 0.02D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY() + 1.1D, entity.getZ(), 8, 0.25D, 0.2D, 0.25D, 0.05D);
    }

    private void startWinnerCelebration(MinecraftServer server, String winnerKey) {
        winnerCelebrationKey = winnerKey == null ? "" : winnerKey;
        winnerFireworkTicks = WINNER_FIREWORK_TICKS;
        winnerFireworkCooldown = 0;
        podiumFireworkTicks = 0;
        podiumFireworkCooldown = 0;
        LOGGER.info("Arena celebracion ganador: {} durante {} ticks", winnerCelebrationKey, WINNER_FIREWORK_TICKS);
    }

    private void startPodiumCelebration(MinecraftServer server) {
        podiumFireworkTicks = PODIUM_FIREWORK_TICKS;
        podiumFireworkCooldown = 8;
        LOGGER.info("Arena celebracion podio durante {} ticks", PODIUM_FIREWORK_TICKS);
    }

    private void stopCelebrationFireworks() {
        stopWinnerCelebrationFireworks();
        podiumFireworkTicks = 0;
        podiumFireworkCooldown = 0;
    }

    private void stopWinnerCelebrationFireworks() {
        winnerCelebrationKey = "";
        winnerFireworkTicks = 0;
        winnerFireworkCooldown = 0;
    }

    private void updateCelebrationFireworks(MinecraftServer server) {
        ServerLevel level = arenaLevel(server);
        if (level == null) {
            return;
        }
        updateWinnerFireworks(level);
        updatePodiumFireworks(level);
    }

    private void updateWinnerFireworks(ServerLevel level) {
        if (winnerFireworkTicks <= 0) {
            return;
        }
        winnerFireworkTicks--;
        if (winnerFireworkTicks <= 0) {
            stopWinnerCelebrationFireworks();
            return;
        }
        winnerFireworkCooldown--;
        if (winnerFireworkCooldown > 0) {
            return;
        }
        winnerFireworkCooldown = 10 + ThreadLocalRandom.current().nextInt(9);

        LivingEntity winner = avatars.get(winnerCelebrationKey);
        double baseX = winner == null ? arenaCenterX : winner.getX();
        double baseY = winner == null ? ARENA_Y + 2.0D : winner.getY() + 1.2D;
        double baseZ = winner == null ? arenaCenterZ : winner.getZ();
        spawnCelebrationFirework(level, baseX, baseY, baseZ, 1.2D, true);
        if (ThreadLocalRandom.current().nextInt(4) == 0) {
            spawnCelebrationFirework(level, baseX, baseY, baseZ, 1.8D, true);
        }
    }

    private void updatePodiumFireworks(ServerLevel level) {
        if (podiumFireworkTicks <= 0) {
            return;
        }
        podiumFireworkTicks--;
        if (podiumFireworkTicks <= 0) {
            podiumFireworkCooldown = 0;
            return;
        }
        podiumFireworkCooldown--;
        if (podiumFireworkCooldown > 0) {
            return;
        }
        podiumFireworkCooldown = 28 + ThreadLocalRandom.current().nextInt(24);

        List<SavedPos> podiumPositions = podiumFireworkPositions();
        if (podiumPositions.isEmpty()) {
            spawnCelebrationFirework(level, BASE_ARENA_CENTER_X, ARENA_Y + 2.0D, BASE_ARENA_CENTER_Z, 2.5D, false);
            return;
        }
        int rockets = podiumPositions.size() == 1 ? 1 : 1 + ThreadLocalRandom.current().nextInt(2);
        for (int index = 0; index < rockets; index++) {
            SavedPos pos = podiumPositions.get(ThreadLocalRandom.current().nextInt(podiumPositions.size()));
            spawnCelebrationFirework(level, pos.x, pos.y + 1.2D, pos.z, 1.3D, false);
        }
    }

    private List<SavedPos> podiumFireworkPositions() {
        PodiumConfig podium = selectedPodium();
        List<SavedPos> positions = new ArrayList<>();
        if (podium.first != null) {
            positions.add(podium.first);
        }
        if (podium.second != null) {
            positions.add(podium.second);
        }
        if (podium.third != null) {
            positions.add(podium.third);
        }
        return positions;
    }

    private void spawnCelebrationFirework(ServerLevel level, double baseX, double baseY, double baseZ, double spread, boolean winnerColors) {
        double x = baseX + ThreadLocalRandom.current().nextDouble(-spread, spread);
        double y = baseY + ThreadLocalRandom.current().nextDouble(0.0D, 0.8D);
        double z = baseZ + ThreadLocalRandom.current().nextDouble(-spread, spread);
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fireworks = new CompoundTag();
        fireworks.putByte("Flight", (byte) 1);
        ListTag explosions = new ListTag();
        CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", (byte) ThreadLocalRandom.current().nextInt(0, 5));
        explosion.putIntArray("Colors", fireworkColors(winnerColors));
        explosion.putIntArray("FadeColors", fireworkFadeColors(winnerColors));
        explosion.putBoolean("Trail", true);
        explosion.putBoolean("Flicker", ThreadLocalRandom.current().nextBoolean());
        explosions.add(explosion);
        fireworks.put("Explosions", explosions);
        rocket.getOrCreateTag().put("Fireworks", fireworks);
        level.addFreshEntity(new FireworkRocketEntity(level, x, y, z, rocket));
    }

    private int[] fireworkColors(boolean winnerColors) {
        return winnerColors
            ? new int[] { 0xFFD700, 0xFF6A00, 0xFFFFFF }
            : new int[] { 0x55FFFF, 0xFFD700, 0xFF55FF };
    }

    private int[] fireworkFadeColors(boolean winnerColors) {
        return winnerColors
            ? new int[] { 0xFF55FF, 0x55FFFF }
            : new int[] { 0xFFFFFF, 0xAAFFAA };
    }

    private void updateAvatarName(LivingEntity mob, String username) {
        mob.setCustomName(Component.literal(username).withStyle(ChatFormatting.WHITE));
        mob.setCustomNameVisible(false);
        if (mob instanceof ServerPlayer player) {
            hideVanillaNameplate(player);
            updateHealthScore(player);
            updateAvatarNameplate(username, player);
            broadcastEntityData(player.level().getServer(), player);
        }
    }

    private void ensureAvatarNameplate(ServerLevel level, String key, LivingEntity avatar) {
        if (avatarNameplates.containsKey(key)) {
            return;
        }
        Display.TextDisplay nameplate = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        nameplate.addTag(NAMEPLATE_TAG);
        nameplate.setNoGravity(true);
        nameplate.setInvulnerable(true);
        positionNameplate(nameplate, avatar);
        applyNameplateNbt(nameplate, nameplateText(key, Math.max(0, Math.round(avatar.getHealth()))));
        if (level.addFreshEntity(nameplate)) {
            avatarNameplates.put(key, nameplate);
        } else {
            LOGGER.warn("Arena no pudo crear nameplate para {}", key);
        }
    }

    private void updateAvatarNameplate(String key, LivingEntity avatar) {
        Display.TextDisplay nameplate = avatarNameplates.get(key);
        if (nameplate == null || nameplate.isRemoved()) {
            if (avatar.level() instanceof ServerLevel level) {
                ensureAvatarNameplate(level, key, avatar);
                nameplate = avatarNameplates.get(key);
            }
        }
        if (nameplate == null) {
            return;
        }
        int hp = Math.max(0, Math.round(avatar.getHealth()));
        applyNameplateNbt(nameplate, nameplateText(key, hp));
        positionNameplate(nameplate, avatar);
        nameplate.hurtMarked = true;
    }

    private Component nameplateText(String key, int hp) {
        int totems = Math.max(0, totemCounts.getOrDefault(key, 0));
        var text = Component.empty();
        if (totems > 0) {
            text.append(Component.literal("\uE001").withStyle(style -> style.withFont(new ResourceLocation(MOD_ID, "icons"))))
                .append(Component.literal(" x" + totems).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n").withStyle(ChatFormatting.WHITE));
        }
        text.append(Component.literal(key + "\n" + hp + " ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\u2764").withStyle(ChatFormatting.RED));
        return text;
    }

    private void applyNameplateNbt(Display.TextDisplay nameplate, Component text) {
        String nextText = Component.Serializer.toJson(text);
        float expectedHeight = 1.15F;
        CompoundTag tag = nameplate.saveWithoutId(new CompoundTag());
        if (nextText.equals(tag.getString("text"))
            && "center".equals(tag.getString("billboard"))
            && "center".equals(tag.getString("alignment"))
            && tag.getInt("line_width") == 220
            && Math.abs(tag.getFloat("width") - 2.6F) < 0.001F
            && Math.abs(tag.getFloat("height") - expectedHeight) < 0.001F) {
            return;
        }
        tag.putString("text", nextText);
        tag.putString("billboard", "center");
        tag.putString("alignment", "center");
        tag.putFloat("view_range", 96.0F);
        tag.putInt("line_width", 220);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putInt("background", 0x00000000);
        tag.putBoolean("shadow", false);
        tag.putBoolean("see_through", false);
        tag.putBoolean("default_background", false);
        tag.putFloat("shadow_radius", 0.0F);
        tag.putFloat("shadow_strength", 0.0F);
        tag.putFloat("width", 2.6F);
        tag.putFloat("height", expectedHeight);
        nameplate.load(tag);
    }

    private void positionNameplate(Display.TextDisplay nameplate, LivingEntity avatar) {
        nameplate.moveTo(avatar.getX(), avatar.getY() + 2.12D, avatar.getZ(), avatar.getYRot(), 0.0F);
    }

    private void hideVanillaNameplate(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(HIDDEN_NAME_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(HIDDEN_NAME_TEAM);
            team.setNameTagVisibility(Team.Visibility.NEVER);
        } else if (team.getNameTagVisibility() != Team.Visibility.NEVER) {
            team.setNameTagVisibility(Team.Visibility.NEVER);
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }

    private void removeAvatarNameplate(MinecraftServer server, String key) {
        Display.TextDisplay nameplate = avatarNameplates.remove(key);
        if (nameplate != null) {
            nameplate.discard();
        }
    }

    private void removeHiddenNameTeam(MinecraftServer server, ServerPlayer player) {
        if (server != null) {
            Scoreboard scoreboard = server.getScoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(HIDDEN_NAME_TEAM);
            if (team != null && scoreboard.getPlayersTeam(player.getScoreboardName()) == team) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
            }
        }
    }

    private void updateHealthScore(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective("arena_hp");
        if (objective == null) {
            objective = scoreboard.addObjective("arena_hp", ObjectiveCriteria.DUMMY, Component.literal("\u2764").withStyle(ChatFormatting.RED), ObjectiveCriteria.RenderType.INTEGER);
            scoreboard.setDisplayObjective(2, objective);
        }
        if (scoreboard.getDisplayObjective(2) != objective) {
            scoreboard.setDisplayObjective(2, objective);
        }
        scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(Math.max(0, Math.round(player.getHealth())));
    }

    private void spawnAvatarForClients(MinecraftServer server, ServerPlayer avatar) {
        if (server == null) {
            return;
        }
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(avatar)));
        server.getPlayerList().broadcastAll(new ClientboundAddPlayerPacket(avatar));
        broadcastRotation(server, avatar);
        broadcastEntityData(server, avatar);
        broadcastEquipment(server, avatar);
    }

    private void broadcastRotation(MinecraftServer server, ServerPlayer avatar) {
        if (server == null) {
            return;
        }
        byte yaw = angleByte(avatar.getYRot());
        byte pitch = angleByte(avatar.getXRot());
        byte headYaw = angleByte(avatar.getYHeadRot());
        server.getPlayerList().broadcastAll(new ClientboundMoveEntityPacket.Rot(avatar.getId(), yaw, pitch, avatar.onGround()));
        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(avatar, headYaw));
    }

    private byte angleByte(float degrees) {
        return (byte) Mth.floor(degrees * 256.0F / 360.0F);
    }

    private void broadcastEntityData(MinecraftServer server, ServerPlayer avatar) {
        if (server == null) {
            return;
        }
        var data = avatar.getEntityData().getNonDefaultValues();
        if (data != null && !data.isEmpty()) {
            server.getPlayerList().broadcastAll(new ClientboundSetEntityDataPacket(avatar.getId(), data));
        }
    }

    private void broadcastEquipment(MinecraftServer server, ServerPlayer avatar) {
        if (server == null) {
            return;
        }
        server.getPlayerList().broadcastAll(new ClientboundSetEquipmentPacket(avatar.getId(), List.of(
            Pair.of(EquipmentSlot.MAINHAND, avatar.getItemBySlot(EquipmentSlot.MAINHAND).copy()),
            Pair.of(EquipmentSlot.OFFHAND, avatar.getItemBySlot(EquipmentSlot.OFFHAND).copy()),
            Pair.of(EquipmentSlot.HEAD, avatar.getItemBySlot(EquipmentSlot.HEAD).copy()),
            Pair.of(EquipmentSlot.CHEST, avatar.getItemBySlot(EquipmentSlot.CHEST).copy()),
            Pair.of(EquipmentSlot.LEGS, avatar.getItemBySlot(EquipmentSlot.LEGS).copy()),
            Pair.of(EquipmentSlot.FEET, avatar.getItemBySlot(EquipmentSlot.FEET).copy())
        )));
    }

    private void openArenaMenu(ServerPlayer player, String page, String targetKey) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, menuPlayer) -> new ArenaControlMenu(containerId, inventory, new SimpleContainer(54), page, targetKey),
            Component.literal(switch (page) {
                case "round" -> "[Arena] Ronda";
                case "players" -> "[Arena] Jugadores";
                case "detail" -> "[Arena] " + targetKey;
                case "settings" -> "[Arena] Ajustes";
                case "podium" -> "[Arena] Podio";
                case "weather" -> "[Arena] Clima";
                case "game" -> "[Arena] Juego";
                case "scoreboard" -> "[Arena] Scoreboard";
                default -> "[Arena] Control";
            })
        ));
    }

    private void ensureControlShortcutItems(ServerPlayer player) {
        boolean changed = false;
        changed |= placeControlShortcut(player, 0, controlItem());
        changed |= placeControlShortcut(player, 1, roundControlItem());
        changed |= placeControlShortcut(player, 2, playersControlItem());
        changed |= placeControlShortcut(player, 3, arenaControlItem());
        changed |= placeControlShortcut(player, 4, podiumControlItem());
        changed |= placeControlShortcut(player, 5, weatherControlItem());
        changed |= placeControlShortcut(player, 6, gameControlItem());
        if (changed) {
            player.getInventory().setChanged();
            player.displayClientMessage(Component.literal("Items de control de arena agregados."), false);
        }
    }

    private boolean placeControlShortcut(ServerPlayer player, int slot, ItemStack desired) {
        Inventory inventory = player.getInventory();
        String desiredPage = controlShortcutPage(desired);
        if (desiredPage.isBlank()) {
            return false;
        }
        int existingSlot = findControlShortcutSlot(inventory, desiredPage);
        ItemStack current = inventory.items.get(slot);
        if (existingSlot == slot && isSameControlShortcut(current, desiredPage)) {
            return false;
        }
        if (current.isEmpty() || isArenaControlShortcut(current)) {
            if (existingSlot >= 0 && existingSlot != slot) {
                inventory.items.set(existingSlot, ItemStack.EMPTY);
            }
            inventory.items.set(slot, desired);
            return true;
        }
        if (existingSlot >= 0) {
            return false;
        }
        inventory.add(desired);
        return true;
    }

    private int findControlShortcutSlot(Inventory inventory, String page) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (isSameControlShortcut(inventory.items.get(slot), page)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isSameControlShortcut(ItemStack stack, String page) {
        return page.equals(controlShortcutPage(stack));
    }

    private boolean isArenaControlShortcut(ItemStack stack) {
        return !controlShortcutPage(stack).isBlank();
    }

    private String controlShortcutPage(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        if (stack.hasTag()) {
            String page = stack.getTag().getString(CONTROL_PAGE_TAG);
            if (!page.isBlank()) {
                return page;
            }
        }
        if (!stack.hasCustomHoverName()) {
            return "";
        }
        String name = stack.getHoverName().getString();
        if (stack.is(Items.NETHER_STAR) && CONTROL_ITEM_NAME.equals(name)) return "main";
        if (stack.is(Items.DIAMOND_SWORD) && ROUND_ITEM_NAME.equals(name)) return "round";
        if (stack.is(Items.PLAYER_HEAD) && PLAYERS_ITEM_NAME.equals(name)) return "players";
        if (stack.is(Items.BLACKSTONE) && ARENA_ITEM_NAME.equals(name)) return "settings";
        if (stack.is(Items.GOLD_BLOCK) && PODIUM_ITEM_NAME.equals(name)) return "podium";
        if (stack.is(Items.WATER_BUCKET) && WEATHER_ITEM_NAME.equals(name)) return "weather";
        if (stack.is(Items.CLOCK) && GAME_ITEM_NAME.equals(name)) return "game";
        return "";
    }

    private boolean isPodiumWand(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD)
            && stack.hasCustomHoverName()
            && PODIUM_WAND_NAME.equals(stack.getHoverName().getString());
    }

    private ItemStack controlItem() {
        return controlShortcutItem(Items.NETHER_STAR, CONTROL_ITEM_NAME, "main",
            "Click derecho para abrir el menu general.",
            "Acceso principal de arena.");
    }

    private ItemStack roundControlItem() {
        return controlShortcutItem(Items.DIAMOND_SWORD, ROUND_ITEM_NAME, "round",
            "Click derecho para abrir ronda.",
            "Inscripciones, pelea, reset y prueba.");
    }

    private ItemStack playersControlItem() {
        return controlShortcutItem(Items.PLAYER_HEAD, PLAYERS_ITEM_NAME, "players",
            "Click derecho para abrir jugadores.",
            "Lista y administracion de avatares.");
    }

    private ItemStack arenaControlItem() {
        return controlShortcutItem(supplementariesItem("wrench", Items.BLACKSTONE), ARENA_ITEM_NAME, "settings",
            "Click derecho para abrir ajustes de arena.",
            "Radio, cierre y plataforma.");
    }

    private ItemStack podiumControlItem() {
        return controlShortcutItem(supplementariesItem("statue", Items.GOLD_BLOCK), PODIUM_ITEM_NAME, "podium",
            "Click derecho para abrir podio.",
            "Puestos, vista y final.");
    }

    private ItemStack weatherControlItem() {
        return controlShortcutItem(supplementariesItem("globe", Items.WATER_BUCKET), WEATHER_ITEM_NAME, "weather",
            "Click derecho para abrir clima.",
            "Despejado, lluvia o tormenta.");
    }

    private ItemStack gameControlItem() {
        return controlShortcutItem(supplementariesItem("hourglass", Items.CLOCK), GAME_ITEM_NAME, "game",
            "Click derecho para abrir ajustes de juego.",
            "Contador, vida inicial y loadout.");
    }

    private ItemStack controlShortcutItem(net.minecraft.world.item.Item item, String name, String page, String... loreLines) {
        ItemStack stack = button(item, name, loreLines);
        stack.getOrCreateTag().putString(CONTROL_PAGE_TAG, page);
        return stack;
    }

    private ItemStack podiumWand(String mode) {
        ItemStack stack = button(Items.BLAZE_ROD.getDefaultInstance(), PODIUM_WAND_NAME,
            "Click derecho en un bloque para guardar.",
            "Modo: " + mode);
        stack.getOrCreateTag().putString(PODIUM_WAND_MODE_TAG, mode);
        return stack;
    }

    private net.minecraft.world.item.Item supplementariesItem(String name, net.minecraft.world.item.Item fallback) {
        return modItem("supplementaries", name, fallback);
    }

    private net.minecraft.world.item.Item modItem(String modId, String name, net.minecraft.world.item.Item fallback) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(modId, name));
        return item == null || item == Items.AIR ? fallback : item;
    }

    private void removePodiumWands(Inventory inventory) {
        removePodiumWandsFrom(inventory.items);
        removePodiumWandsFrom(inventory.offhand);
        inventory.setChanged();
    }

    private void removePodiumWandsFrom(List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            if (isPodiumWand(stacks.get(i))) {
                stacks.set(i, ItemStack.EMPTY);
            }
        }
    }

    private ItemStack button(ItemStack stack, String name, String... loreLines) {
        stack.setHoverName(Component.literal(name).withStyle(ChatFormatting.YELLOW));
        if (loreLines.length > 0) {
            ListTag lore = new ListTag();
            for (String line : loreLines) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(ChatFormatting.GRAY))));
            }
            stack.getOrCreateTagElement("display").put("Lore", lore);
        }
        return stack;
    }

    private ItemStack button(net.minecraft.world.item.Item item, String name, String... loreLines) {
        return button(new ItemStack(item), name, loreLines);
    }

    private ItemStack equipmentButton(net.minecraft.world.item.Item item, String name, boolean equipped) {
        if (!equipped) {
            return button(item, name);
        }
        ItemStack stack = new ItemStack(item);
        stack.enchant(Enchantments.UNBREAKING, 1);
        stack.setHoverName(Component.literal("EN USO: " + name).withStyle(ChatFormatting.GREEN));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Equipado actualmente").withStyle(ChatFormatting.GREEN))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    private String swordMaterialOf(LivingEntity player) {
        if (player == null) {
            return "";
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.is(Items.NETHERITE_SWORD)) return "netherite";
        if (stack.is(Items.DIAMOND_SWORD)) return "diamond";
        if (stack.is(Items.IRON_SWORD)) return "iron";
        if (stack.is(Items.WOODEN_SWORD)) return "wood";
        if (stack.is(Items.STONE_SWORD)) return "stone";
        return "";
    }

    private String armorMaterialOf(LivingEntity player) {
        if (player == null) {
            return "";
        }
        ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (stack.is(Items.DIAMOND_CHESTPLATE)) return "diamond";
        if (stack.is(Items.IRON_CHESTPLATE)) return "iron";
        if (stack.is(Items.CHAINMAIL_CHESTPLATE)) return "chainmail";
        if (stack.is(Items.LEATHER_CHESTPLATE)) return "leather";
        return "";
    }

    private ItemStack countedButton(net.minecraft.world.item.Item item, int count, String name, String... loreLines) {
        ItemStack stack = button(item, name, loreLines);
        stack.setCount(Mth.clamp(count, 1, 64));
        return stack;
    }

    private ItemStack playerHeadButton(String key, int count, String name, String... loreLines) {
        ItemStack stack = countedButton(Items.PLAYER_HEAD, count, name, loreLines);
        GameProfile profile = avatarProfiles.get(key);
        if (profile != null) {
            stack.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
        }
        return stack;
    }

    private void fillMenu(Container container) {
        ItemStack filler = button(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, filler.copy());
        }
    }

    private void touchMenus() {
        menuRevision++;
    }

    private void forgetAttackState(String key) {
        attackCooldowns.remove(key);
        fighterAttackCooldowns.remove(key);
        pendingAttacks.remove(key);
        pendingAttacks.entrySet().removeIf(entry -> key.equals(entry.getValue().targetKey()));
        strafeDirections.remove(key);
        strafeTicks.remove(key);
        hurtRecoveryTicks.remove(key);
        shieldBlockTicks.remove(key);
        lastCombatActionTicks.remove(key);
        lastCombatWarnTicks.remove(key);
    }

    private int countTaggedAvatars(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(AVATAR_TAG)) {
                count++;
            }
        }
        return count;
    }

    private boolean despawnAvatar(Entity entity) {
        if (entity == null) {
            return false;
        }
        entity.discard();
        return true;
    }

    private void removeAvatarByKey(MinecraftServer server, String key, boolean withParticles) {
        LivingEntity player = avatars.remove(key);
        LivingEntity fighter = arenaFighters.remove(key);
        avatarProfiles.remove(key);
        avatarSkinNames.remove(key);
        removeAvatarNameplate(server, key);
        forgetAttackState(key);
        hurtRecoveryTicks.remove(key);
        totemCounts.remove(key);
        verticalVelocities.remove(key);
        ServerLevel level = arenaLevel(server);
        if (fighter != null && fighter != player) {
            if (withParticles && level != null) {
                spawnDeathParticles(level, fighter);
            }
            despawnAvatar(fighter);
        }
        if (player == null) {
            return;
        }
        if (withParticles && level != null) {
            spawnDeathParticles(level, player);
        }
        if (server != null && player instanceof ServerPlayer serverPlayer) {
            removeHiddenNameTeam(server, serverPlayer);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(serverPlayer.getUUID())));
        }
        despawnAvatar(player);
        touchMenus();
    }

    private final class ArenaControlMenu extends ChestMenu {
        private String page;
        private String targetKey;
        private final Container menuContainer;
        private final UUID viewerId;
        private int seenMenuRevision = -1;
        private boolean resetWinsConfirm = false;

        private ArenaControlMenu(int containerId, Inventory inventory, Container container, String page, String targetKey) {
            super(MenuType.GENERIC_9x6, containerId, inventory, container, 6);
            this.page = page == null || page.isBlank() ? "main" : page;
            this.targetKey = targetKey == null ? "" : targetKey;
            this.menuContainer = container;
            this.viewerId = inventory.player.getUUID();
            build(container);
        }

        private void switchPage(String nextPage, String nextTargetKey) {
            this.page = nextPage == null || nextPage.isBlank() ? "main" : nextPage;
            this.targetKey = nextTargetKey == null ? "" : nextTargetKey;
            if (!"scoreboard".equals(this.page)) {
                resetWinsConfirm = false;
            }
            build(menuContainer);
            broadcastChanges();
        }

        @Override
        public void broadcastChanges() {
            if (seenMenuRevision != menuRevision) {
                build(menuContainer);
                seenMenuRevision = menuRevision;
            }
            super.broadcastChanges();
        }

        private void build(Container container) {
            fillMenu(container);
            switch (page) {
                case "round" -> buildRound(container);
                case "players" -> buildPlayers(container);
                case "detail" -> buildDetail(container);
                case "settings" -> buildSettings(container);
                case "podium" -> buildPodium(container);
                case "weather" -> buildWeather(container);
                case "game" -> buildGame(container);
                case "scoreboard" -> buildScoreboard(container);
                default -> buildMain(container);
            }
        }

        private void buildMain(Container container) {
            container.setItem(3, button(Items.NETHER_STAR, "Estado: " + roundState,
                "Jugadores: " + avatars.size(),
                "Radio: " + currentRadius,
                "Cierre: " + shrinkSeconds + "s / " + shrinkBlocks + " bloques",
                "Movimiento: " + (randomArenaMovementEnabled ? "activo" : "detenido")));
            container.setItem(10, button(supplementariesItem("notice_board", Items.LIME_WOOL), "Abrir inscripciones", "Permite unir viewers."));
            container.setItem(11, button(Items.DIAMOND_SWORD, "Iniciar pelea", "Cierra inscripciones y activa combate."));
            container.setItem(12, button(supplementariesItem("cracked_bell", Items.ORANGE_WOOL), "Terminar ronda", "Detiene la pelea actual."));
            container.setItem(13, button(Items.ENDER_PEARL, randomArenaMovementEnabled ? "Detener arena aleatoria" : "Mover arena aleatoria",
                "Activa/desactiva movimiento suave.",
                "Solo mueve la plataforma negra."));
            container.setItem(16, button(supplementariesItem("bomb", Items.TNT), "Reiniciar arena", "Limpia avatares y reconstruye arena."));
            container.setItem(19, button(Items.EMERALD, "Unir prueba", "Agrega un viewer de prueba."));
            container.setItem(20, button(Items.GRASS_BLOCK, "Ir a la arena", "Teletransporta al frente de la arena."));
            container.setItem(45, button(Items.PLAYER_HEAD, "Jugadores", "Ver y administrar avatares."));
            container.setItem(47, button(supplementariesItem("wrench", Items.COMPARATOR), "Ajustes de arena", "Editar cierre y radio en vivo."));
            container.setItem(49, button(supplementariesItem("statue", Items.GOLD_BLOCK), "Podio", "Configurar puestos y vista."));
            container.setItem(51, button(supplementariesItem("globe", Items.WATER_BUCKET), "Clima", "Control de lluvia y tormenta."));
            container.setItem(53, button(supplementariesItem("hourglass", Items.CLOCK), "Juego", "Contador, vida inicial y loadout."));
        }

        private void buildRound(Container container) {
            container.setItem(3, button(Items.DIAMOND_SWORD, "Ronda: " + roundState,
                "Jugadores: " + avatars.size(),
                "Radio: " + currentRadius,
                "Movimiento: " + (randomArenaMovementEnabled ? "activo" : "detenido")));
            container.setItem(10, button(supplementariesItem("notice_board", Items.LIME_WOOL), "Abrir inscripciones", "Permite unir viewers."));
            container.setItem(11, button(Items.DIAMOND_SWORD, "Iniciar pelea", "Cierra inscripciones y activa combate."));
            container.setItem(12, button(supplementariesItem("cracked_bell", Items.ORANGE_WOOL), "Terminar ronda", "Detiene la pelea actual."));
            container.setItem(13, button(Items.ENDER_PEARL, randomArenaMovementEnabled ? "Detener arena aleatoria" : "Mover arena aleatoria",
                "Activa/desactiva movimiento suave.",
                "Solo mueve la plataforma negra."));
            container.setItem(16, button(supplementariesItem("bomb", Items.TNT), "Reiniciar arena", "Limpia avatares y reconstruye arena."));
            container.setItem(19, button(Items.EMERALD, "Unir prueba", "Agrega un viewer de prueba."));
            container.setItem(20, button(Items.GRASS_BLOCK, "Ir a la arena", "Teletransporta al frente de la arena."));
        }

        private void buildPlayers(Container container) {
            List<String> keys = avatars.keySet().stream().sorted().toList();
            int maxPage = Math.max(0, (keys.size() - 1) / PLAYER_MENU_PAGE_SIZE);
            int pageIndex = Mth.clamp(playerMenuPages.getOrDefault(viewerId, 0), 0, maxPage);
            playerMenuPages.put(viewerId, pageIndex);
            int start = pageIndex * PLAYER_MENU_PAGE_SIZE;
            int visible = Math.min(PLAYER_MENU_PAGE_SIZE, Math.max(0, keys.size() - start));
            for (int index = 0; index < visible; index++) {
                String key = keys.get(start + index);
                int slot = playerListSlot(index);
                LivingEntity avatar = avatars.get(key);
                int hp = avatar == null ? 1 : Math.max(1, Math.round(avatar.getHealth()));
                int totems = totemCounts.getOrDefault(key, 0);
                container.setItem(slot, playerHeadButton(key, hp, key,
                    "HP: " + hp + "/" + (avatar == null ? startingHealth : Math.round(avatar.getMaxHealth())),
                    "Totems: " + totems,
                    "Click para administrar."));
            }
            container.setItem(17, button(Items.ARROW, "Pagina anterior"));
            container.setItem(26, button(Items.COMPASS, "Pagina " + (pageIndex + 1) + "/" + (maxPage + 1),
                "Jugadores: " + keys.size(),
                "Ordenado A-Z."));
            container.setItem(35, button(Items.ARROW, "Pagina siguiente"));
        }

        private void buildPodium(Container container) {
            PodiumConfig podium = podiums.computeIfAbsent(activePodium, ignored -> defaultPodium());
            container.setItem(3, button(supplementariesItem("statue", Items.GOLD_BLOCK), "Podio " + activePodium,
                randomPodium ? "Final: podio aleatorio" : "Final: podio activo",
                "Puesto 1: " + yesNo(podium.first),
                "Puesto 2: " + yesNo(podium.second),
                "Puesto 3: " + yesNo(podium.third),
                "Vista: " + yesNo(podium.view)));
            container.setItem(8, button(supplementariesItem("key", Items.LIME_DYE), "Guardar podio", "Guarda puestos, vista y modo."));
            container.setItem(10, button(Items.ARROW, "Podio anterior"));
            container.setItem(11, countedButton(supplementariesItem("statue", Items.CLOCK), activePodium, "Podio #" + activePodium, "Podio activo."));
            container.setItem(12, button(Items.ARROW, "Podio siguiente"));
            container.setItem(14, button(supplementariesItem("globe", Items.ENDER_EYE), randomPodium ? "Final aleatorio" : "Final podio activo"));
            container.setItem(19, button(Items.GOLD_INGOT, "Configurar puesto 1"));
            container.setItem(20, button(Items.IRON_INGOT, "Configurar puesto 2"));
            container.setItem(21, button(Items.COPPER_INGOT, "Configurar puesto 3"));
            container.setItem(23, button(Items.ENDER_PEARL, "Guardar vista"));
            container.setItem(28, button(Items.COMPASS, "Ir al podio"));
            container.setItem(29, button(Items.GRASS_BLOCK, "Ir a la arena"));
            container.setItem(34, button(Items.BARRIER, "Borrar podio activo"));
        }

        private void buildDetail(Container container) {
            LivingEntity avatar = avatars.get(targetKey);
            int hp = avatar == null ? 0 : Math.round(avatar.getHealth());
            String activeSword = swordMaterialOf(avatar);
            String activeArmor = armorMaterialOf(avatar);
            int activeTotems = totemCounts.getOrDefault(targetKey, 0);
            boolean hasShield = avatar != null && avatar.getOffhandItem().is(Items.SHIELD);
            container.setItem(3, playerHeadButton(targetKey, 1, targetKey,
                avatar == null ? "Avatar no encontrado." : "HP: " + hp + "/" + Math.round(avatar.getMaxHealth()),
                "Totems: " + activeTotems,
                "Victorias: " + playerWins.getOrDefault(targetKey, 0)));
            container.setItem(10, equipmentButton(Items.WOODEN_SWORD, "Espada de madera", "wood".equals(activeSword)));
            container.setItem(11, equipmentButton(Items.IRON_SWORD, "Espada de hierro", "iron".equals(activeSword)));
            container.setItem(12, equipmentButton(Items.DIAMOND_SWORD, "Espada de diamante", "diamond".equals(activeSword)));
            container.setItem(13, equipmentButton(Items.NETHERITE_SWORD, "Espada de netherite", "netherite".equals(activeSword)));
            container.setItem(19, equipmentButton(Items.LEATHER_CHESTPLATE, "Armadura de cuero", "leather".equals(activeArmor)));
            container.setItem(20, equipmentButton(Items.CHAINMAIL_CHESTPLATE, "Armadura de mallas", "chainmail".equals(activeArmor)));
            container.setItem(21, equipmentButton(Items.IRON_CHESTPLATE, "Armadura de hierro", "iron".equals(activeArmor)));
            container.setItem(22, equipmentButton(Items.DIAMOND_CHESTPLATE, "Armadura de diamante", "diamond".equals(activeArmor)));
            container.setItem(28, equipmentButton(Items.TOTEM_OF_UNDYING, "Dar totem", activeTotems > 0));
            container.setItem(29, button(Items.GOLDEN_APPLE, "Curar +4 HP"));
            container.setItem(30, button(Items.ENCHANTED_GOLDEN_APPLE, "Curar +10 HP"));
            container.setItem(31, equipmentButton(Items.SHIELD, hasShield ? "Quitar escudo" : "Dar escudo", hasShield));
            container.setItem(34, button(Items.BARRIER, "Eliminar avatar"));
        }

        private void buildSettings(Container container) {
            container.setItem(3, button(supplementariesItem("wrench", Items.COMPARATOR), "Ajustes actuales",
                "Cierre cada: " + shrinkSeconds + "s",
                "Bloques por cierre: " + shrinkBlocks,
                "Radio minimo: " + minimumRadius,
                "Radio inicial: " + initialRadius,
                "Radio actual: " + currentRadius));
            container.setItem(8, button(supplementariesItem("key", Items.LIME_DYE), "Guardar ajustes", "Guarda cierre y radios."));
            container.setItem(10, button(Items.REDSTONE, "-5s cierre"));
            container.setItem(11, countedButton(supplementariesItem("hourglass", Items.CLOCK), shrinkSeconds, "Cierre: " + shrinkSeconds + "s", "Cada cuantos segundos se achica."));
            container.setItem(12, countedButton(Items.EMERALD, shrinkSeconds, "+5s cierre", "Actual: " + shrinkSeconds + "s"));
            container.setItem(14, button(Items.REDSTONE_BLOCK, "-1 bloque por cierre"));
            container.setItem(15, countedButton(Items.CHAIN, shrinkBlocks, "Bloques cierre: " + shrinkBlocks, "Cuantos bloques baja por cierre."));
            container.setItem(16, countedButton(Items.EMERALD_BLOCK, shrinkBlocks, "+1 bloque por cierre", "Actual: " + shrinkBlocks));
            container.setItem(19, button(Items.IRON_BARS, "-1 radio minimo"));
            container.setItem(20, countedButton(Items.BEACON, minimumRadius, "Radio minimo: " + minimumRadius, "Limite final de la arena."));
            container.setItem(21, countedButton(Items.BEACON, minimumRadius, "+1 radio minimo", "Actual: " + minimumRadius));
            container.setItem(23, button(Items.BLACKSTONE, "-1 radio actual"));
            container.setItem(24, countedButton(supplementariesItem("globe", Items.POLISHED_BLACKSTONE), currentRadius, "Radio actual: " + currentRadius, "Reconstruye arena."));
            container.setItem(25, countedButton(Items.POLISHED_BLACKSTONE, currentRadius, "+1 radio actual", "Reconstruye arena."));
            container.setItem(28, button(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS, "-1 radio inicial"));
            container.setItem(29, countedButton(Items.COMPASS, initialRadius, "Radio inicial: " + initialRadius, "Reinicio usara este radio."));
            container.setItem(30, countedButton(Items.POLISHED_BLACKSTONE_BRICKS, initialRadius, "+1 radio inicial", "Reinicio usara este radio."));
            container.setItem(32, button(Items.GRASS_BLOCK, "Ir a la arena", "Teletransporta al frente de la arena."));
        }

        private void buildGame(Container container) {
            container.setItem(3, button(supplementariesItem("hourglass", Items.CLOCK), "Ajustes de juego",
                "Contador: " + fightCountdownSeconds + "s",
                "Vida inicial: " + startingHealth + " HP",
                "Loadout: base por ronda"));
            container.setItem(8, button(supplementariesItem("key", Items.LIME_DYE), "Guardar ajustes", "Guarda contador y vida inicial."));
            container.setItem(10, button(Items.REDSTONE, "Contador -1s", "Minimo: " + MIN_FIGHT_COUNTDOWN_SECONDS + "s"));
            container.setItem(11, countedButton(supplementariesItem("hourglass", Items.CLOCK), fightCountdownSeconds, "Contador: " + fightCountdownSeconds + "s",
                "Cuenta antes de iniciar pelea."));
            container.setItem(12, button(Items.EMERALD, "Contador +1s", "Maximo: " + MAX_FIGHT_COUNTDOWN_SECONDS + "s"));

            container.setItem(14, button(Items.RED_DYE, "Vida -10 HP", "Minimo: " + MIN_STARTING_HEALTH + " HP"));
            container.setItem(15, countedButton(Items.GOLDEN_APPLE, startingHealth, "Vida inicial: " + startingHealth + " HP",
                "Se aplica al entrar/reiniciar ronda."));
            container.setItem(16, button(Items.LIME_DYE, "Vida +10 HP", "Maximo: " + MAX_STARTING_HEALTH + " HP"));

            container.setItem(19, button(supplementariesItem("quiver", Items.CHEST), "Loadout inicial",
                "Preparado para separar por ronda.",
                "Hoy: espada madera.",
                "Escudo se da por jugador.",
                "Reiniciar vuelve a base."));
            container.setItem(23, button(supplementariesItem("notice_board", Items.GOLD_BLOCK), "Scoreboard / Victorias",
                "Top de ganadores del live.",
                "Mostrar, guardar o reiniciar.",
                "Abre menu dedicado."));
        }

        private void buildScoreboard(Container container) {
            List<Map.Entry<String, Integer>> top = topWinnerEntries(10);
            int totalWins = playerWins.values().stream().mapToInt(Integer::intValue).sum();
            container.setItem(3, button(supplementariesItem("notice_board", Items.GOLD_BLOCK), "Scoreboard victorias",
                "Jugadores con victoria: " + top.size(),
                "Victorias totales: " + totalWins,
                "HUD lateral: " + yesNo(scoreboardSidebarVisible),
                "Guardar: " + yesNo(scoreboardSaveWins),
                "Filas/ancho/Y: " + scoreboardHudRows + " / " + scoreboardHudWidth + " / " + scoreboardHudY,
                "Los ajustes visuales se autoguardan."));

            int[] slots = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28};
            int maxWins = top.isEmpty() ? 1 : top.get(0).getValue();
            for (int index = 0; index < top.size() && index < slots.length; index++) {
                Map.Entry<String, Integer> entry = top.get(index);
                container.setItem(slots[index], scoreboardEntryButton(index + 1, entry.getKey(), entry.getValue(), maxWins));
            }
            if (top.isEmpty()) {
                container.setItem(10, button(Items.PAPER, "Sin victorias aun",
                    "Cuando alguien gane una ronda",
                    "aparecera en este top."));
            }

            container.setItem(14, button(Items.REDSTONE, "-1 fila HUD",
                "Actual: " + scoreboardHudRows,
                "Minimo: " + MIN_SCOREBOARD_HUD_ROWS));
            container.setItem(15, countedButton(Items.PAPER, scoreboardHudRows, "Filas HUD: " + scoreboardHudRows,
                "Limite de jugadores visibles.",
                "Tambien alarga el panel."));
            container.setItem(16, button(Items.EMERALD, "+1 fila HUD",
                "Actual: " + scoreboardHudRows,
                "Maximo: " + MAX_SCOREBOARD_HUD_ROWS));
            container.setItem(23, button(Items.IRON_BARS, "-10 ancho HUD",
                "Actual: " + scoreboardHudWidth + "px",
                "Minimo: " + MIN_SCOREBOARD_HUD_WIDTH + "px"));
            container.setItem(24, button(Items.ITEM_FRAME, "Ancho HUD: " + scoreboardHudWidth + "px",
                "Agrandar si los nombres quedan apretados."));
            container.setItem(25, button(Items.GLASS_PANE, "+10 ancho HUD",
                "Actual: " + scoreboardHudWidth + "px",
                "Maximo: " + MAX_SCOREBOARD_HUD_WIDTH + "px"));
            container.setItem(32, button(Items.ARROW, "Subir HUD",
                "Y actual: " + scoreboardHudY + "px"));
            container.setItem(33, button(Items.MAP, "Altura HUD: " + scoreboardHudY + "px",
                "Distancia desde arriba."));
            container.setItem(34, button(Items.ARROW, "Bajar HUD",
                "Y actual: " + scoreboardHudY + "px"));
            container.setItem(37, button(scoreboardSidebarVisible ? Items.LIME_DYE : Items.GRAY_DYE,
                scoreboardSidebarVisible ? "Ocultar scoreboard lateral" : "Mostrar scoreboard lateral",
                scoreboardSidebarVisible ? "Ahora se ve a la derecha." : "Ahora solo se ve en este menu.",
                "Click para cambiar."));
            container.setItem(38, button(scoreboardSaveWins ? Items.WRITABLE_BOOK : Items.BOOK,
                scoreboardSaveWins ? "Guardar victorias: si" : "Guardar victorias: no",
                scoreboardSaveWins ? "Persiste en scoreboard_settings.json." : "No se recargaran tras reiniciar.",
                "Click para cambiar."));
            container.setItem(39, button(resetWinsConfirm ? Items.TNT : supplementariesItem("bomb", Items.BARRIER),
                resetWinsConfirm ? "CONFIRMAR reinicio de victorias" : "Reiniciar victorias",
                resetWinsConfirm ? "Segundo click borra el scoreboard." : "Primer click pide confirmacion.",
                "No toca jugadores ni arena."));
            container.setItem(41, button(Items.BRUSH, "Reset visual HUD",
                "Filas, ancho y altura base.",
                "No borra victorias."));
            container.setItem(43, button(Items.ARROW, "Volver a Arena Juego"));
        }

        private ItemStack scoreboardEntryButton(int rank, String key, int wins, int maxWins) {
            net.minecraft.world.item.Item item = switch (rank) {
                case 1 -> Items.GOLD_INGOT;
                case 2 -> Items.IRON_INGOT;
                case 3 -> Items.COPPER_INGOT;
                default -> Items.PLAYER_HEAD;
            };
            ItemStack stack = item == Items.PLAYER_HEAD
                ? playerHeadButton(key, wins, "#" + rank + " " + key,
                    "Victorias: " + wins,
                    scoreboardBar(wins, maxWins))
                : countedButton(item, wins, "#" + rank + " " + key,
                    "Victorias: " + wins,
                    scoreboardBar(wins, maxWins));
            if (item == Items.PLAYER_HEAD) {
                GameProfile profile = avatarProfiles.get(key);
                if (profile != null) {
                    stack.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
                }
            }
            return stack;
        }

        private String scoreboardBar(int wins, int maxWins) {
            int filled = Mth.clamp(Mth.ceil((wins / (float) Math.max(1, maxWins)) * 12.0F), 1, 12);
            return "[" + "|".repeat(filled) + ".".repeat(12 - filled) + "]";
        }

        private void buildWeather(Container container) {
            container.setItem(3, button(supplementariesItem("globe", Items.WATER_BUCKET), "Control de clima", "Cambia solo el mundo actual."));
            container.setItem(11, button(supplementariesItem("globe", Items.SUNFLOWER), "Despejado"));
            container.setItem(13, button(Items.WATER_BUCKET, "Lluvia"));
            container.setItem(15, button(Items.LIGHTNING_ROD, "Tormenta"));
        }

        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer) || slot < 0 || slot >= 54) {
                return;
            }
            MinecraftServer server = serverPlayer.getServer();
            if (server == null) {
                return;
            }
            if ("round".equals(page)) {
                handleRoundClick(serverPlayer, server, slot);
                return;
            }
            if ("players".equals(page)) {
                List<String> keys = avatars.keySet().stream().sorted().toList();
                int maxPage = Math.max(0, (keys.size() - 1) / PLAYER_MENU_PAGE_SIZE);
                int pageIndex = Mth.clamp(playerMenuPages.getOrDefault(viewerId, 0), 0, maxPage);
                int playerIndex = playerListIndex(slot);
                int absoluteIndex = pageIndex * PLAYER_MENU_PAGE_SIZE + playerIndex;
                if (playerIndex >= 0 && absoluteIndex >= 0 && absoluteIndex < keys.size()) {
                    switchPage("detail", keys.get(absoluteIndex));
                } else if (slot == 17) {
                    playerMenuPages.put(viewerId, Math.max(0, pageIndex - 1));
                    build(menuContainer);
                    broadcastChanges();
                } else if (slot == 35) {
                    playerMenuPages.put(viewerId, Math.min(maxPage, pageIndex + 1));
                    build(menuContainer);
                    broadcastChanges();
                }
                return;
            }
            if ("detail".equals(page)) {
                handleDetailClick(serverPlayer, server, slot);
                return;
            }
            if ("settings".equals(page)) {
                handleSettingsClick(serverPlayer, server, slot);
                return;
            }
            if ("podium".equals(page)) {
                handlePodiumClick(serverPlayer, server, slot);
                return;
            }
            if ("weather".equals(page)) {
                handleWeatherClick(serverPlayer, server, slot);
                return;
            }
            if ("game".equals(page)) {
                handleGameClick(serverPlayer, server, slot);
                return;
            }
            if ("scoreboard".equals(page)) {
                handleScoreboardClick(serverPlayer, server, slot);
                return;
            }
            handleMainClick(serverPlayer, server, slot);
        }

        private void handleMainClick(ServerPlayer player, MinecraftServer server, int slot) {
            if (handleRoundAction(player, server, slot)) {
                build(menuContainer);
                broadcastChanges();
                return;
            }
            if (slot == 45) {
                switchPage("players", "");
                return;
            } else if (slot == 47) {
                switchPage("settings", "");
                return;
            } else if (slot == 49) {
                switchPage("podium", "");
                return;
            } else if (slot == 51) {
                switchPage("weather", "");
                return;
            } else if (slot == 53) {
                switchPage("game", "");
                return;
            }
            build(menuContainer);
            broadcastChanges();
        }

        private void handleRoundClick(ServerPlayer player, MinecraftServer server, int slot) {
            if (handleRoundAction(player, server, slot)) {
                build(menuContainer);
                broadcastChanges();
            }
        }

        private boolean handleRoundAction(ServerPlayer player, MinecraftServer server, int slot) {
            ServerLevel level = arenaLevel(server);
            if (slot == 10) {
                openJoin(server);
            } else if (slot == 11) {
                startFight(server);
            } else if (slot == 12) {
                endRound(server);
            } else if (slot == 13 && level != null) {
                moveArenaRandom(level);
            } else if (slot == 16 && level != null) {
                resetArena(level);
            } else if (slot == 19 && level != null) {
                joinTestAvatar(level);
            } else if (slot == 20 && level != null) {
                teleportTo(level, player, new SavedPos(arenaCenterX, ARENA_Y + 2.0D, arenaCenterZ - currentRadius - 5.0D, 0.0F, 15.0F));
            } else {
                return false;
            }
            return true;
        }

        private void handleDetailClick(ServerPlayer player, MinecraftServer server, int slot) {
            if (!avatars.containsKey(targetKey)) {
                switchPage("players", "");
                return;
            }
            boolean changed = true;
            switch (slot) {
                case 10 -> giveSword(server, targetKey, "wood");
                case 11 -> giveSword(server, targetKey, "iron");
                case 12 -> giveSword(server, targetKey, "diamond");
                case 13 -> giveSword(server, targetKey, "netherite");
                case 19 -> giveArmor(server, targetKey, "leather");
                case 20 -> giveArmor(server, targetKey, "chainmail");
                case 21 -> giveArmor(server, targetKey, "iron");
                case 22 -> giveArmor(server, targetKey, "diamond");
                case 28 -> giveTotem(server, targetKey, 1);
                case 29 -> healAvatar(server, targetKey, 4);
                case 30 -> healAvatar(server, targetKey, 10);
                case 31 -> toggleShield(server, targetKey);
                case 34 -> {
                    removeAvatarByKey(server, targetKey, true);
                    message(server, targetKey + " eliminado manualmente");
                    switchPage("players", "");
                    return;
                }
                default -> {
                    changed = false;
                }
            }
            if (changed) {
                build(menuContainer);
                broadcastChanges();
            }
        }

        private void handleSettingsClick(ServerPlayer player, MinecraftServer server, int slot) {
            ServerLevel level = arenaLevel(server);
            boolean changed = true;
            if (slot == 10) {
                shrinkSeconds = Math.max(5, shrinkSeconds - 5);
            } else if (slot == 12) {
                shrinkSeconds = Math.min(300, shrinkSeconds + 5);
            } else if (slot == 14) {
                shrinkBlocks = Math.max(1, shrinkBlocks - 1);
            } else if (slot == 16) {
                shrinkBlocks = Math.min(16, shrinkBlocks + 1);
            } else if (slot == 19) {
                minimumRadius = Math.max(2, minimumRadius - 1);
            } else if (slot == 21) {
                minimumRadius = Math.min(INITIAL_RADIUS, minimumRadius + 1);
            } else if (slot == 23) {
                currentRadius = Math.max(minimumRadius, currentRadius - 1);
                if (level != null) {
                    buildCombatPlatform(level, currentRadius);
                }
            } else if (slot == 25) {
                currentRadius = Math.min(initialRadius, currentRadius + 1);
                if (level != null) {
                    buildCombatPlatform(level, currentRadius);
                }
            } else if (slot == 28) {
                initialRadius = Math.max(minimumRadius, initialRadius - 1);
                currentRadius = Math.min(currentRadius, initialRadius);
                if (level != null) {
                    buildCombatPlatform(level, currentRadius);
                }
            } else if (slot == 30) {
                initialRadius = Math.min(INITIAL_RADIUS, initialRadius + 1);
            } else if (slot == 32 && level != null) {
                teleportTo(level, player, new SavedPos(arenaCenterX, ARENA_Y + 2.0D, arenaCenterZ - currentRadius - 5.0D, 0.0F, 15.0F));
            } else if (slot == 8 || slot == 53) {
                normalizeArenaRadii();
                saveArenaSettings();
                message(server, "Ajustes de arena guardados");
                build(menuContainer);
                return;
            } else {
                changed = false;
            }
            if (!changed) {
                return;
            }
            normalizeArenaRadii();
            message(server, "Ajustes arena: cierre " + shrinkSeconds + "s, bloques " + shrinkBlocks + ", minimo " + minimumRadius + ", inicial " + initialRadius + ", actual " + currentRadius);
            build(menuContainer);
        }

        private void handleGameClick(ServerPlayer player, MinecraftServer server, int slot) {
            boolean changed = true;
            if (slot == 10) {
                fightCountdownSeconds = Math.max(MIN_FIGHT_COUNTDOWN_SECONDS, fightCountdownSeconds - 1);
            } else if (slot == 12) {
                fightCountdownSeconds = Math.min(MAX_FIGHT_COUNTDOWN_SECONDS, fightCountdownSeconds + 1);
            } else if (slot == 14) {
                startingHealth = Math.max(MIN_STARTING_HEALTH, startingHealth - 10);
            } else if (slot == 16) {
                startingHealth = Math.min(MAX_STARTING_HEALTH, startingHealth + 10);
            } else if (slot == 23) {
                switchPage("scoreboard", "");
                return;
            } else if (slot == 8 || slot == 53) {
                saveArenaSettings();
                message(server, "Ajustes de juego guardados");
                build(menuContainer);
                return;
            } else {
                changed = false;
            }
            if (!changed) {
                return;
            }
            message(server, "Ajustes juego: contador " + fightCountdownSeconds + "s, vida inicial " + startingHealth + " HP");
            build(menuContainer);
        }

        private void handleScoreboardClick(ServerPlayer player, MinecraftServer server, int slot) {
            boolean hudChanged = false;
            if (slot == 14) {
                scoreboardHudRows = Math.max(MIN_SCOREBOARD_HUD_ROWS, scoreboardHudRows - 1);
                hudChanged = true;
            } else if (slot == 16) {
                scoreboardHudRows = Math.min(MAX_SCOREBOARD_HUD_ROWS, scoreboardHudRows + 1);
                hudChanged = true;
            } else if (slot == 23) {
                scoreboardHudWidth = Math.max(MIN_SCOREBOARD_HUD_WIDTH, scoreboardHudWidth - 10);
                hudChanged = true;
            } else if (slot == 25) {
                scoreboardHudWidth = Math.min(MAX_SCOREBOARD_HUD_WIDTH, scoreboardHudWidth + 10);
                hudChanged = true;
            } else if (slot == 41) {
                scoreboardHudRows = DEFAULT_SCOREBOARD_HUD_ROWS;
                scoreboardHudWidth = DEFAULT_SCOREBOARD_HUD_WIDTH;
                scoreboardHudY = DEFAULT_SCOREBOARD_HUD_Y;
                hudChanged = true;
            } else if (slot == 32) {
                scoreboardHudY = Math.max(MIN_SCOREBOARD_HUD_Y, scoreboardHudY - 4);
                hudChanged = true;
            } else if (slot == 34) {
                scoreboardHudY = Math.min(MAX_SCOREBOARD_HUD_Y, scoreboardHudY + 4);
                hudChanged = true;
            }
            if (hudChanged) {
                saveScoreboardSettings();
                syncWinsDisplays(server);
                resetWinsConfirm = false;
                message(server, "HUD scoreboard: filas " + scoreboardHudRows + ", ancho " + scoreboardHudWidth + "px, Y " + scoreboardHudY + "px");
                build(menuContainer);
                return;
            }
            if (slot == 37) {
                scoreboardSidebarVisible = !scoreboardSidebarVisible;
                saveScoreboardSettings();
                syncWinsDisplays(server);
                resetWinsConfirm = false;
                message(server, "Scoreboard lateral: " + yesNo(scoreboardSidebarVisible));
                build(menuContainer);
                return;
            }
            if (slot == 38) {
                scoreboardSaveWins = !scoreboardSaveWins;
                saveScoreboardSettings();
                syncWinsDisplays(server);
                resetWinsConfirm = false;
                message(server, "Guardar victorias: " + yesNo(scoreboardSaveWins));
                build(menuContainer);
                return;
            }
            if (slot == 39) {
                if (!resetWinsConfirm) {
                    resetWinsConfirm = true;
                    message(server, "Confirma el reinicio de victorias con otro click");
                    build(menuContainer);
                    return;
                }
                resetWinsConfirm = false;
                resetScoreboardWins(server);
                build(menuContainer);
                return;
            }
            if (slot == 43) {
                switchPage("game", "");
                return;
            }
            resetWinsConfirm = false;
            build(menuContainer);
        }

        private void handlePodiumClick(ServerPlayer player, MinecraftServer server, int slot) {
            if (slot == 10) {
                activePodium = Math.max(1, activePodium - 1);
            } else if (slot == 12) {
                activePodium++;
            } else if (slot == 14) {
                randomPodium = !randomPodium;
            } else if (slot == 8) {
                message(server, "Podio guardado");
            } else if (slot == 19) {
                setPodiumWand(player, "first");
            } else if (slot == 20) {
                setPodiumWand(player, "second");
            } else if (slot == 21) {
                setPodiumWand(player, "third");
            } else if (slot == 23) {
                PodiumConfig podium = podiums.computeIfAbsent(activePodium, ignored -> defaultPodium());
                podium.view = new SavedPos(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                if (player.level() instanceof ServerLevel level) {
                    refreshPodiumNumbers(level, activePodium, podium);
                }
                message(server, "Vista guardada para podio " + activePodium);
            } else if (slot == 28) {
                PodiumConfig podium = podiums.computeIfAbsent(activePodium, ignored -> defaultPodium());
                if (podium.view != null && player.level() instanceof ServerLevel level) {
                    teleportTo(level, player, podium.view);
                }
            } else if (slot == 29) {
                if (player.level() instanceof ServerLevel level) {
                    teleportTo(level, player, new SavedPos(arenaCenterX, ARENA_Y + 2.0D, arenaCenterZ - currentRadius - 5.0D, 0.0F, 15.0F));
                }
            } else if (slot == 34) {
                podiums.remove(activePodium);
                removePodiumNumbersForPodium(activePodium);
                message(server, "Config del podio " + activePodium + " borrada");
            }
            podiums.putIfAbsent(activePodium, defaultPodium());
            savePodiumSettings();
            build(menuContainer);
        }

        private void handleWeatherClick(ServerPlayer player, MinecraftServer server, int slot) {
            if (slot == 11) {
                setArenaWeather(server, "clear");
            } else if (slot == 13) {
                setArenaWeather(server, "rain");
            } else if (slot == 15) {
                setArenaWeather(server, "thunder");
            }
            build(menuContainer);
        }

        private void setPodiumWand(ServerPlayer player, String mode) {
            podiumWandModes.put(player.getUUID(), mode);
            removePodiumWands(player.getInventory());
            player.getInventory().add(podiumWand(mode));
            player.getInventory().setChanged();
            player.displayClientMessage(Component.literal("Click derecho en el bloque del podio."), false);
        }

        private String yesNo(SavedPos pos) {
            return pos == null ? "no" : "si";
        }

        private String yesNo(boolean value) {
            return value ? "si" : "no";
        }

        private int playerListSlot(int index) {
            int slot = index;
            while (slot < 45 && slot % 9 == 8) {
                slot++;
            }
            while (slot < 45) {
                int skippedRightColumn = slot / 9;
                int candidate = index + skippedRightColumn;
                if (candidate < 45 && candidate % 9 != 8) {
                    return candidate;
                }
                slot++;
            }
            return 44;
        }

        private int playerListIndex(int slot) {
            if (slot < 0 || slot >= 45 || slot % 9 == 8) {
                return -1;
            }
            return slot - (slot / 9);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }
    }

    private void removeAvatars(ServerLevel level) {
        MinecraftServer server = level.getServer();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(AVATAR_TAG) || entity.getTags().contains(NAMEPLATE_TAG) || entity.getTags().contains(COMBAT_TEXT_TAG)) {
                if (entity instanceof ServerPlayer player && server != null) {
                    removeHiddenNameTeam(server, player);
                    server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
                }
                despawnAvatar(entity);
            }
        }
        avatarNameplates.clear();
        floatingCombatTexts.clear();
        podiumNumberMarkers.clear();
    }

    private void enqueueArenaNotice(Component title, Component subtitle, int stayTicks) {
        arenaNotices.add(new ArenaNotice(title, subtitle, stayTicks));
        while (arenaNotices.size() > NOTICE_QUEUE_LIMIT) {
            arenaNotices.poll();
        }
        if (arenaNotices.size() >= NOTICE_QUEUE_FAST_THRESHOLD && activeNoticeTicks > NOTICE_FAST_FORWARD_TICKS) {
            activeNoticeTicks = NOTICE_FAST_FORWARD_TICKS;
        }
    }

    private void updateArenaNotices(MinecraftServer server) {
        if (activeNoticeTicks > 0) {
            if (arenaNotices.size() >= NOTICE_QUEUE_FAST_THRESHOLD && activeNoticeTicks > NOTICE_FAST_FORWARD_TICKS) {
                activeNoticeTicks = NOTICE_FAST_FORWARD_TICKS;
            }
            activeNoticeTicks--;
            return;
        }
        ArenaNotice notice = arenaNotices.poll();
        if (notice == null) {
            return;
        }
        showArenaNotice(server, notice);
        activeNoticeTicks = notice.stayTicks();
    }

    private void showArenaNotice(MinecraftServer server, ArenaNotice notice) {
        if (server == null) {
            return;
        }
        int stayTicks = Math.max(1, notice.stayTicks());
        ClientboundSetTitlesAnimationPacket animation = new ClientboundSetTitlesAnimationPacket(4, stayTicks, 6);
        ClientboundSetTitleTextPacket title = new ClientboundSetTitleTextPacket(notice.title());
        ClientboundSetSubtitleTextPacket subtitle = new ClientboundSetSubtitleTextPacket(notice.subtitle());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.getTags().contains(AVATAR_TAG)) {
                continue;
            }
            viewer.connection.send(animation);
            viewer.connection.send(title);
            viewer.connection.send(subtitle);
        }
    }

    private int aliveFightersAfter(String removedKey) {
        int alive = 0;
        for (Map.Entry<String, LivingEntity> entry : arenaFighters.entrySet()) {
            LivingEntity fighter = entry.getValue();
            if (!entry.getKey().equals(removedKey) && fighter != null && fighter.isAlive() && !fighter.isRemoved()) {
                alive++;
            }
        }
        return alive;
    }

    private void message(MinecraftServer server, String text) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Arena] " + text), false);
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String commandUsername(JsonObject command) {
        String username = text(command, "username").trim();
        if (!username.isBlank()) {
            return username;
        }
        JsonElement source = command.get("source");
        if (source != null && source.isJsonObject()) {
            return text(source.getAsJsonObject(), "username").trim();
        }
        return "";
    }

    private static int number(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static UUID offlineUuid(String username) {
        String source = username == null || username.isBlank() ? "viewer" : username.trim().toLowerCase();
        return UUID.nameUUIDFromBytes(("ArenaPlayer:" + source).getBytes(StandardCharsets.UTF_8));
    }

    private static String profileName(String username, int index) {
        String cleaned = username == null ? "viewer" : username.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isBlank()) {
            cleaned = "viewer";
        }
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 12) + String.format("%04d", Math.floorMod(index, 10000));
        }
        return cleaned;
    }

    private static final class SavedPos {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private SavedPos(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class PodiumConfig {
        private SavedPos first;
        private SavedPos second;
        private SavedPos third;
        private SavedPos view;
    }

    private record PodiumSnapshot(
        String key,
        GameProfile profile,
        ItemStack mainHand,
        ItemStack offHand,
        ItemStack head,
        ItemStack chest,
        ItemStack legs,
        ItemStack feet
    ) {
        private PodiumSnapshot {
            mainHand = mainHand.copy();
            offHand = offHand.copy();
            head = head.copy();
            chest = chest.copy();
            legs = legs.copy();
            feet = feet.copy();
        }
    }

    private record PendingAttack(String targetKey, int windupTicks, int initialWindupTicks, int totalTicks, double rangeSqr) {
        private PendingAttack tick() {
            return new PendingAttack(targetKey, windupTicks - 1, initialWindupTicks, totalTicks, rangeSqr);
        }
    }

    private static final class FloatingCombatText {
        private final Display.TextDisplay display;
        private final double x;
        private final double y;
        private final double z;
        private int age;
        private final int maxAge;

        private FloatingCombatText(Display.TextDisplay display, double x, double y, double z, int age, int maxAge) {
            this.display = display;
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = age;
            this.maxAge = maxAge;
        }
    }

    private record PendingLiveReward(String swordMaterial, String armorMaterial, int healAmount, int totems, boolean shield) {
        private static PendingLiveReward empty() {
            return new PendingLiveReward("", "", 0, 0, false);
        }

        private boolean isEmpty() {
            return swordMaterial.isBlank() && armorMaterial.isBlank() && healAmount <= 0 && totems <= 0 && !shield;
        }

        private PendingLiveReward withSword(String nextSwordMaterial) {
            return new PendingLiveReward(nextSwordMaterial, armorMaterial, healAmount, totems, shield);
        }

        private PendingLiveReward withArmor(String nextArmorMaterial) {
            return new PendingLiveReward(swordMaterial, nextArmorMaterial, healAmount, totems, shield);
        }

        private PendingLiveReward withHealAmount(int nextHealAmount) {
            return new PendingLiveReward(swordMaterial, armorMaterial, Math.max(0, nextHealAmount), totems, shield);
        }

        private PendingLiveReward withTotems(int nextTotems) {
            return new PendingLiveReward(swordMaterial, armorMaterial, healAmount, Math.max(0, nextTotems), shield);
        }

        private PendingLiveReward withShield(boolean nextShield) {
            return new PendingLiveReward(swordMaterial, armorMaterial, healAmount, totems, nextShield);
        }
    }

    private record ArenaNotice(Component title, Component subtitle, int stayTicks) {
    }

    private record WinsHudEntry(String name, int wins) {
        private static WinsHudEntry decode(FriendlyByteBuf buffer) {
            return new WinsHudEntry(buffer.readUtf(64), Math.max(0, buffer.readVarInt()));
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(name == null ? "" : name, 64);
            buffer.writeVarInt(Math.max(0, wins));
        }
    }

    private record WinsHudPacket(boolean visible, int rows, int width, int y, List<WinsHudEntry> entries) {
        private static final int MAX_ENTRIES = MAX_SCOREBOARD_HUD_ROWS;

        private WinsHudPacket {
            rows = Mth.clamp(rows, MIN_SCOREBOARD_HUD_ROWS, MAX_SCOREBOARD_HUD_ROWS);
            width = Mth.clamp(width, MIN_SCOREBOARD_HUD_WIDTH, MAX_SCOREBOARD_HUD_WIDTH);
            y = Mth.clamp(y, MIN_SCOREBOARD_HUD_Y, MAX_SCOREBOARD_HUD_Y);
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        private static WinsHudPacket decode(FriendlyByteBuf buffer) {
            boolean visible = buffer.readBoolean();
            int rows = Mth.clamp(buffer.readVarInt(), MIN_SCOREBOARD_HUD_ROWS, MAX_SCOREBOARD_HUD_ROWS);
            int width = Mth.clamp(buffer.readVarInt(), MIN_SCOREBOARD_HUD_WIDTH, MAX_SCOREBOARD_HUD_WIDTH);
            int y = Mth.clamp(buffer.readVarInt(), MIN_SCOREBOARD_HUD_Y, MAX_SCOREBOARD_HUD_Y);
            int count = Mth.clamp(buffer.readVarInt(), 0, MAX_ENTRIES);
            List<WinsHudEntry> entries = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                entries.add(WinsHudEntry.decode(buffer));
            }
            return new WinsHudPacket(visible, rows, width, y, entries);
        }

        private static void encode(WinsHudPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.visible);
            buffer.writeVarInt(packet.rows);
            buffer.writeVarInt(packet.width);
            buffer.writeVarInt(packet.y);
            List<WinsHudEntry> entries = packet.entries.stream().limit(MAX_ENTRIES).toList();
            buffer.writeVarInt(entries.size());
            for (WinsHudEntry entry : entries) {
                entry.encode(buffer);
            }
        }

        private static void handle(WinsHudPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    ClientWinsHud.accept(packet);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private static final class ClientWinsHud {
        private static boolean visible = false;
        private static int hudRows = DEFAULT_SCOREBOARD_HUD_ROWS;
        private static int hudWidth = DEFAULT_SCOREBOARD_HUD_WIDTH;
        private static int hudY = DEFAULT_SCOREBOARD_HUD_Y;
        private static List<WinsHudEntry> entries = List.of();

        private ClientWinsHud() {
        }

        private static void registerClientEvents() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientWinsHud::registerOverlay);
        }

        @SubscribeEvent
        public static void registerOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("arena_wins_hud", ClientWinsHud::render);
        }

        private static void accept(WinsHudPacket packet) {
            visible = packet.visible();
            hudRows = packet.rows();
            hudWidth = packet.width();
            hudY = packet.y();
            entries = List.copyOf(packet.entries());
        }

        private static void render(
            net.minecraftforge.client.gui.overlay.ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!visible || minecraft == null || minecraft.player == null || minecraft.options.hideGui) {
                return;
            }

            Font font = minecraft.font;
            List<WinsHudEntry> snapshot = entries.stream().limit(hudRows).toList();
            int maxWins = snapshot.stream().mapToInt(WinsHudEntry::wins).max().orElse(1);
            int rows = Math.max(1, hudRows);
            int panelWidth = Mth.clamp(hudWidth, MIN_SCOREBOARD_HUD_WIDTH, Math.max(MIN_SCOREBOARD_HUD_WIDTH, screenWidth - 18));
            int titleHeight = 18;
            int headerHeight = 13;
            int rowHeight = 15;
            int panelHeight = titleHeight + headerHeight + rows * rowHeight + 8;
            int x = Math.max(4, screenWidth - panelWidth - 10);
            int y = Mth.clamp(hudY, MIN_SCOREBOARD_HUD_Y, Math.max(MIN_SCOREBOARD_HUD_Y, screenHeight - panelHeight - 4));

            drawPanel(graphics, x, y, panelWidth, panelHeight);
            graphics.fill(x + 2, y + 2, x + panelWidth - 2, y + titleHeight, 0xDD130F08);
            graphics.fillGradient(x + 2, y + 2, x + panelWidth - 2, y + titleHeight, 0xDD3A2B08, 0xDD120F08);
            graphics.drawCenteredString(font, "VICTORIAS", x + panelWidth / 2, y + 6, 0xFFFFD86B);

            int headerY = y + titleHeight + 3;
            int left = x + 8;
            int winsX = x + panelWidth - 9;
            graphics.drawString(font, "#", left, headerY, 0xFF8D8D8D, false);
            graphics.drawString(font, "JUGADOR", left + 22, headerY, 0xFF8D8D8D, false);
            graphics.drawString(font, "W", winsX - font.width("W"), headerY, 0xFF8D8D8D, false);
            graphics.fill(x + 6, headerY + 10, x + panelWidth - 6, headerY + 11, 0x663E3E3E);

            int firstRowY = y + titleHeight + headerHeight + 4;
            if (snapshot.isEmpty()) {
                int emptyY = firstRowY + Math.max(0, (rows * rowHeight) / 2 - 8);
                graphics.drawCenteredString(font, "Sin victorias aun", x + panelWidth / 2, emptyY, 0xFFE6E6E6);
                graphics.fill(x + 12, emptyY + 13, x + panelWidth - 12, emptyY + 15, 0x553A2B08);
                return;
            }

            for (int index = 0; index < rows; index++) {
                int rowY = firstRowY + index * rowHeight;
                boolean even = index % 2 == 0;
                graphics.fill(x + 5, rowY - 2, x + panelWidth - 5, rowY + rowHeight - 2, even ? 0x33000000 : 0x221F1F1F);
                if (index >= snapshot.size()) {
                    continue;
                }

                WinsHudEntry entry = snapshot.get(index);
                int rank = index + 1;
                boolean localPlayer = minecraft.player.getGameProfile().getName().equalsIgnoreCase(entry.name());
                if (localPlayer) {
                    graphics.fill(x + 5, rowY - 2, x + panelWidth - 5, rowY + rowHeight - 2, 0x24FFFFFF);
                    graphics.renderOutline(x + 5, rowY - 2, panelWidth - 10, rowHeight, 0x55FFFFFF);
                }
                int rankColor = rankColor(rank, entry.name());
                int nameColor = rank <= 3 ? rankColor : playerColor(entry.name());
                String rankText = "#" + rank;
                String wins = String.valueOf(entry.wins());
                int nameMaxWidth = Math.max(20, winsX - (left + 22) - font.width(wins) - 14);
                String name = fitText(font, entry.name(), nameMaxWidth);

                graphics.drawString(font, rankText, left, rowY + 1, rankColor, false);
                graphics.drawString(font, name, left + 22, rowY + 1, nameColor, false);
                graphics.drawString(font, wins, winsX - font.width(wins), rowY + 1, 0xFF8AFF8A, false);

                int barLeft = left + 22;
                int barRight = winsX - font.width(wins) - 8;
                int barWidth = Math.max(0, barRight - barLeft);
                int fillWidth = Mth.clamp(Math.round(barWidth * (entry.wins() / (float) Math.max(1, maxWins))), 2, Math.max(2, barWidth));
                graphics.fill(barLeft, rowY + 11, barLeft + barWidth, rowY + 13, 0x44101010);
                graphics.fill(barLeft, rowY + 11, barLeft + fillWidth, rowY + 13, 0xDD000000 | (playerColor(entry.name()) & 0x00FFFFFF));
            }
        }

        private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.fill(x, y, x + width, y + height, 0x99000000);
            graphics.fill(x, y, x + width, y + 1, 0xCCB8B8B8);
            graphics.fill(x, y, x + 1, y + height, 0xCCB8B8B8);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xCC202020);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xCC202020);
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xAA050505);
        }

        private static int rankColor(int rank, String key) {
            return switch (rank) {
                case 1 -> 0xFFFFD700;
                case 2 -> 0xFFC0C0C0;
                case 3 -> 0xFFCD7F32;
                default -> playerColor(key);
            };
        }

        private static int playerColor(String key) {
            int[] colors = {
                0xFF7AD7FF, 0xFFFF8ACF, 0xFF9DFF8A, 0xFFFFB86B,
                0xFFB18AFF, 0xFFFFF27A, 0xFF8AFFF1, 0xFFFF8A8A
            };
            return colors[Math.floorMod(key == null ? 0 : key.hashCode(), colors.length)];
        }

        private static String fitText(Font font, String text, int maxWidth) {
            String value = text == null || text.isBlank() ? "viewer" : text;
            if (font.width(value) <= maxWidth) {
                return value;
            }
            String suffix = "...";
            int suffixWidth = font.width(suffix);
            String result = value;
            while (!result.isEmpty() && font.width(result) + suffixWidth > maxWidth) {
                result = result.substring(0, result.length() - 1);
            }
            return result.isEmpty() ? suffix : result + suffix;
        }
    }

}
