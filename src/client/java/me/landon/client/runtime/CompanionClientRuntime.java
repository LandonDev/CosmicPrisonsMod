package me.landon.client.runtime;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.landon.CosmicPrisonsMod;
import me.landon.client.feature.ClientFeatureDefinition;
import me.landon.client.feature.ClientFeatures;
import me.landon.client.screen.FeatureSettingsScreen;
import me.landon.companion.attestation.BuildAttestation;
import me.landon.companion.attestation.BuildAttestationLoader;
import me.landon.companion.attestation.SignatureVerifier;
import me.landon.companion.config.CompanionConfig;
import me.landon.companion.config.CompanionConfigManager;
import me.landon.companion.network.CompanionRawPayload;
import me.landon.companion.protocol.BinaryDecodingException;
import me.landon.companion.protocol.ProtocolCodec;
import me.landon.companion.protocol.ProtocolConstants;
import me.landon.companion.protocol.ProtocolMessage;
import me.landon.companion.session.ConnectionGateState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton client runtime that owns companion protocol handling, feature gating, and render state.
 *
 * <p>Contributors should treat this class as the main integration point for new server-driven
 * client features. Register behavior through existing lifecycle hooks, gate by {@link
 * ClientFeatures}, and persist user settings through {@link CompanionConfigManager}.
 */
public final class CompanionClientRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionClientRuntime.class);
    private static final CompanionClientRuntime INSTANCE = new CompanionClientRuntime();
    private static final int CLIENT_CAPABILITIES_BITSET = 127;
    private static final int PLAYER_STORAGE_MIN_SLOT = 0;
    private static final int HOTBAR_MAX_SLOT = 8;
    private static final int PLAYER_STORAGE_MAX_SLOT = 35;
    private static final int KNOWN_OVERLAY_STACK_LIMIT = 64;
    private static final int PENDING_CURSOR_OVERLAY_FRAMES = 4;
    private static final int HUD_PANEL_MIN_WIDTH = 112;
    private static final int HUD_PANEL_MAX_WIDTH = 206;
    private static final int HUD_PANEL_HEADER_HEIGHT = 11;
    private static final int HUD_PANEL_HORIZONTAL_PADDING = 4;
    private static final int HUD_PANEL_VERTICAL_PADDING = 3;
    private static final int HUD_PANEL_EDGE_INSET = 3;
    private static final long LEADERBOARD_CYCLE_INTERVAL_MILLIS = 5500L;
    private static final Pattern LEADERBOARD_ENTRY_PATTERN =
            Pattern.compile("^#(\\d+)\\s+(.+?)\\s+-\\s+(.+)$");
    private static final Pattern LEADERBOARD_VALUE_PATTERN =
            Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([kKmMbBtT]?)");
    private static final Pattern PROGRESS_RATIO_HINT_PATTERN =
            Pattern.compile(
                    "(?i)(?:^|\\|)\\s*progress\\s*=\\s*(-?\\d+(?:\\.\\d+)?)\\s*/\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:\\||$)");
    private static final Pattern PROGRESS_PERCENT_HINT_PATTERN =
            Pattern.compile(
                    "(?i)(?:^|\\|)\\s*(?:p|progress)\\s*=\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?\\s*(?:\\||$)");
    private static final Pattern PROGRESS_ELAPSED_HINT_PATTERN =
            Pattern.compile("(?i)(?:^|\\|)\\s*elapsed\\s*=\\s*([^|]+)");
    private static final Pattern PROGRESS_TOTAL_HINT_PATTERN =
            Pattern.compile("(?i)(?:^|\\|)\\s*total\\s*=\\s*([^|]+)");
    private static final Pattern GANG_MEMBER_LINE_PATTERN =
            Pattern.compile("^(\\[[^\\]]+\\])\\s+(?:(\\[[^\\]]+\\])\\s+)?(.+)$");
    private static final Pattern GANG_TRAILING_TIME_PATTERN =
            Pattern.compile("^(.*)\\(([^()]*)\\)\\s*$");
    private static final Map<String, String> PET_HEAD_TEXTURES =
            Map.of(
                    "bandit_king",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2NkZTkwMTA5OTBiN2NmMDBkNzViYmExMjE0OTZhYjc2ODRjNmNjNGI2MmNmOTI4OTBmMmRiMzlmYmFmZWUwZiJ9fX0=",
                    "signal_jammer",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzAwZTJmMjVlMDcyOGU4OTgxZjZiZDNlNDE2NTUzODBlMDBlN2U1NGI3NTU5ZDVmNDczNWU3MzkxY2RmMDJkMiJ9fX0=",
                    "cleanse",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2E1MjgwMjY1NzNmM2E0ZDJmMjNjZjM2M2ZkNmU2NTI3NzdiNmVjMTkzYTg0MTRiMWU2NDRkNzhiZjllN2MzIn19fQ==",
                    "shockwave",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZiYjllOGNlMTg5MzBiZDhiNmI5MDM4NzViYjM0YzFjNjkyMTQ2NjJiYWYwMzI0ZDYzMmE3Y2M2MzVhZmM5OSJ9fX0=",
                    "blacksmith",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTg3ZmNjY2FlNGM1NmIxOTY5ZTZkY2ZkZDdhOTkyMTY2N2ZmYjNjMjU5NjhjZDE4N2UwZDdiYTQ1YmFlMzdmYiJ9fX0=",
                    "lucky",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTE5ZDI4YTg2MzJmYTRkODdjYTE5OWJiYzJlODhjZjM2OGRlZGQ1NTc0NzAxN2FlMzQ4NDM1NjlmN2E2MzRjNSJ9fX0=");
    private static final KeyBinding.Category PING_KEYBIND_CATEGORY =
            KeyBinding.Category.create(Identifier.of(CosmicPrisonsMod.MOD_ID, "pings"));
    private static final int PING_PARTICLE_COLUMN_SEGMENTS = 6;
    private static final int PING_BEAM_HEIGHT = 2048;
    private static final int PING_BEAM_COLOR_GANG = 0x45A8FF;
    private static final int PING_BEAM_COLOR_TRUCE = 0xFFAF4D;
    private static final int PING_BEAM_COLOR_COORDS = 0xFFF945FF;
    private static final float PING_BEAM_INNER_RADIUS = 0.2F;
    private static final float PING_BEAM_OUTER_RADIUS = 0.25F;
    private static final double PING_BEAM_HORIZONTAL_OFFSET = 1.35D;
    private static final int PING_LABEL_TEXT_COLOR_GANG = 0xFFE4F4FF;
    private static final int PING_LABEL_TEXT_COLOR_TRUCE = 0xFFFFEEDB;
    private static final int PING_LABEL_TEXT_COLOR_COORDS = 0xFFA8A8A8;
    private static final int PING_LABEL_DISTANCE_COLOR_GANG = 0xFFB7DFFF;
    private static final int PING_LABEL_DISTANCE_COLOR_TRUCE = 0xFFFFD4AA;
    private static final int PING_LABEL_DISTANCE_COLOR_COORDS = 0xFF858585;
    private static final int PING_LABEL_FRAME_COLOR_GANG = 0xFF3E90CE;
    private static final int PING_LABEL_FRAME_COLOR_TRUCE = 0xFFD77A2A;
    private static final int PING_LABEL_FRAME_COLOR_COORDS = 0xFFF377F7;
    private static final int PING_LABEL_BACKGROUND_COLOR_GANG = 0x132435;
    private static final int PING_LABEL_BACKGROUND_COLOR_TRUCE = 0x2F1C0D;
    private static final int PING_LABEL_BACKGROUND_COLOR_COORDS = 0x341036;
    private static final double PING_LABEL_VERTICAL_OFFSET = 1.02D;
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long COORDS_PING_LIFETIME_MILLIS = 5L * 60L * 1000L; // 5 mins
    private static final long PING_FEEDBACK_COOLDOWN_MILLIS = 1500L;
    private static final long UPDATE_CHECK_INTERVAL_MILLIS = 60_000L;
    private static final int CLIENT_HELLO_MAX_ATTEMPTS = 4;
    private static final int CLIENT_HELLO_RETRY_INTERVAL_TICKS = 20;
    private static final int CLIENT_HELLO_CHANNEL_UNAVAILABLE_RETRY_TICKS = 10;
    private static final int COMPANION_C2S_MAX_PER_SECOND = 8;
    private static final long COMPANION_C2S_WINDOW_MILLIS = 1000L;
    private static final long COMPANION_C2S_RATE_LIMIT_WARN_COOLDOWN_MILLIS = 5000L;
    private static final long COMPANION_DIAGNOSTICS_LOG_INTERVAL_MILLIS = 15_000L;
    private static final String PACKET_DIAGNOSTICS_PROPERTY = "cosmicprisons.debug.packets";
    private static final String PACKET_DIAGNOSTICS_ENV = "COSMICPRISONS_DEBUG_PACKETS";
    private static final boolean PACKET_DIAGNOSTICS_ENABLED =
            isTruthy(System.getProperty(PACKET_DIAGNOSTICS_PROPERTY))
                    || isTruthy(System.getenv(PACKET_DIAGNOSTICS_ENV));
    private static final String MOD_RELEASE_MANIFEST_URL =
            "https://github.com/LandonDev/CosmicPrisonsMod/releases/latest/download/cosmic-launcher-manifest.json";
    private static final String LAUNCHER_PROOF_PROPERTY = "cosmicprisons.launcher.proof";
    private static final String LAUNCHER_PROOF_ENV = "COSMICPRISONS_LAUNCHER_PROOF";
    private static final String LAUNCHER_PROOF_FILE_PROPERTY = "cosmicprisons.launcher.proofFile";
    private static final int MAX_LAUNCHER_PROOF_BYTES = 1536;
    private static final Pattern MANIFEST_MOD_VERSION_PATTERN =
            Pattern.compile(
                    "\"mod\"\\s*:\\s*\\{[^{}]*\"version\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RUNTIME_ARTIFACT_VERSION_PATTERN =
            Pattern.compile(
                    "(?i)^cosmicprisonsmod[-_]?([vV]?\\d+(?:\\.\\d+){1,3}(?:[-+][A-Za-z0-9._-]+)?)\\.jar$");
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*$");

    private final ProtocolCodec protocolCodec = new ProtocolCodec();
    private final SignatureVerifier signatureVerifier = new SignatureVerifier();
    private final ConnectionSessionState session = new ConnectionSessionState();
    private final CompanionConfigManager configManager = new CompanionConfigManager();
    private final BuildAttestationLoader buildAttestationLoader = new BuildAttestationLoader();
    private final LauncherProofProvider launcherProofProvider = new LauncherProofProvider();
    private final ModUpdateChecker modUpdateChecker =
            new ModUpdateChecker(this::resolveInstalledVersionForUpdateCheck);
    private final List<KnownOverlayStack> knownOverlayStacks = new ArrayList<>();
    private final Map<String, Long> eventProgressBaselineSeconds = new LinkedHashMap<>();
    private final Map<String, Long> cooldownProgressBaselineSeconds = new LinkedHashMap<>();
    private final Map<String, Long> petProgressBaselineSeconds = new LinkedHashMap<>();
    private final Map<String, ItemStack> petHeadIconCache = new LinkedHashMap<>();
    private final Int2LongMap gangPingVisualExpiryAtMillis = new Int2LongOpenHashMap();
    private final Int2LongMap trucePingVisualExpiryAtMillis = new Int2LongOpenHashMap();
    private final IntSet gangPingVisualSeededIds = new IntOpenHashSet();
    private final IntSet trucePingVisualSeededIds = new IntOpenHashSet();
    private final Int2ObjectMap<PingVisualAnchorSnapshot> gangPingStaticAnchors =
            new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<PingVisualAnchorSnapshot> trucePingStaticAnchors =
            new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<PingLabelSnapshot> gangPingLabelSnapshots =
            new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<PingLabelSnapshot> trucePingLabelSnapshots =
            new Int2ObjectOpenHashMap<>();
    private KeyBinding gangPingKeyBinding;
    private KeyBinding trucePingKeyBinding;

    private CoordsPingInfo coordsPing = null;
    private static final Vec3d COORDS_PING_BEAM_OFFSET = new Vec3d(0.5D, 0D, 0.5D);

    private CompanionConfig config;
    private BuildAttestation buildAttestation;
    private int helloRetriesRemaining;
    private int helloRetryCooldownTicks;
    private boolean helloUnavailableLogged;
    private ConnectionSessionState.ItemOverlayEntry activeCursorOverlayEntry;
    private ItemStack activeCursorOverlayStack = ItemStack.EMPTY;
    private int activeCursorOverlaySlot = -1;
    private int pendingCursorOverlayFrames;
    private volatile int activePeacefulMiningTargetEntityId = -1;
    private long lastPingFeedbackAtMillis;
    private long companionSendWindowStartAtMillis;
    private int companionSendWindowCount;
    private long companionSendAttempts;
    private long companionSendSuccess;
    private long companionSendBlockedNoPlayer;
    private long companionSendBlockedTargetServer;
    private long companionSendBlockedChannelUnavailable;
    private long companionSendBlockedRateLimit;
    private long companionSendBlockedEncoding;
    private long lastCompanionRateLimitWarnAtMillis;
    private long lastCompanionDiagnosticsLogAtMillis;
    private boolean gangPingKeyWasDown;
    private boolean trucePingKeyWasDown;
    private boolean outdatedNoticeLeftMouseDown;
    private String dismissedOutdatedNoticeLatestVersion = "";
    private boolean initialized;

    private record PingVisualAnchorSnapshot(Vec3d beamBasePos, Vec3d labelAnchorPos) {}

    private record PingLabelSnapshot(Vec3d anchorPos, String entityName, float healthValue) {}

    /** Returns the global runtime instance used by all client integration points. */
    public static CompanionClientRuntime getInstance() {
        return INSTANCE;
    }

    private CompanionClientRuntime() {}

    /**
     * Initializes the runtime once and registers all client event listeners.
     *
     * <p>This must be called from the Fabric client entrypoint only.
     */
    public synchronized void initializeClient() {
        if (initialized) {
            return;
        }

        config = configManager.load();
        buildAttestation = buildAttestationLoader.load(resolveModVersion());
        ensureFeatureDefaultsPersisted();
        initializePingKeybinds();

        ClientPlayNetworking.registerGlobalReceiver(
                CompanionRawPayload.ID, this::onPayloadReceived);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldChange);
        ScreenEvents.AFTER_INIT.register(this::onScreenAfterInit);
        WorldRenderEvents.END_MAIN.register(this::onWorldRenderEndMain);

        HudRenderCallback.EVENT.register(this::renderHud);

        initialized = true;
    }

    public synchronized boolean isPayloadFallbackEnabled() {
        return getConfig().enablePayloadCodecFallback;
    }

    /** Returns the feature catalog used by settings and runtime gating logic. */
    public synchronized List<ClientFeatureDefinition> getAvailableFeatures() {
        return ClientFeatures.all();
    }

    /** Returns whether a feature toggle is currently enabled in client config. */
    public synchronized boolean isFeatureEnabled(String featureId) {
        return getFeatureToggleState(featureId);
    }

    /**
     * Persists a feature toggle state.
     *
     * <p>Unknown ids are ignored.
     */
    public synchronized void setFeatureEnabled(String featureId, boolean enabled) {
        if (ClientFeatures.findById(featureId).isEmpty()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.featureToggles.put(featureId, enabled);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Returns whether the connected server currently advertises support for the given feature. */
    public synchronized boolean isFeatureSupportedByServer(String featureId) {
        return ClientFeatures.findById(featureId)
                .map(this::isFeatureSupportedByServer)
                .orElse(false);
    }

    /** Returns a snapshot of event visibility overrides used by the HUD event widget. */
    public synchronized Map<String, Boolean> getHudEventVisibilitySnapshot() {
        return new LinkedHashMap<>(getConfig().hudEventVisibility);
    }

    /** Sets event visibility override for one event key. */
    public synchronized void setHudEventVisibility(String eventKey, boolean visible) {
        if (eventKey == null || eventKey.isBlank()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudEventVisibility.put(
                eventKey.trim().toLowerCase(java.util.Locale.ROOT), visible);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized boolean isHudEventsCompactMode() {
        return getConfig().hudEventsCompactMode;
    }

    /** Enables/disables compact rendering for the events widget. */
    public synchronized void setHudEventsCompactMode(boolean compactMode) {
        CompanionConfig currentConfig = getConfig();
        currentConfig.hudEventsCompactMode = compactMode;
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized boolean isHudSatchelsCompactMode() {
        return getConfig().hudSatchelsCompactMode;
    }

    /** Enables/disables compact rendering for the satchels widget. */
    public synchronized void setHudSatchelsCompactMode(boolean compactMode) {
        CompanionConfig currentConfig = getConfig();
        currentConfig.hudSatchelsCompactMode = compactMode;
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized Map<String, Boolean> getHudLeaderboardVisibilitySnapshot() {
        return new LinkedHashMap<>(getConfig().hudLeaderboardVisibility);
    }

    /** Sets visibility override for one leaderboard widget id. */
    public synchronized void setHudLeaderboardVisibility(String widgetId, boolean visible) {
        if (widgetId == null || widgetId.isBlank()) {
            return;
        }

        String normalized = widgetId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!CompanionConfig.HUD_LEADERBOARD_WIDGET_IDS.contains(normalized)) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudLeaderboardVisibility.put(normalized, visible);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized boolean isHudLeaderboardsCompactMode() {
        return getConfig().hudLeaderboardsCompactMode;
    }

    /** Enables/disables compact rendering for leaderboard widgets. */
    public synchronized void setHudLeaderboardsCompactMode(boolean compactMode) {
        CompanionConfig currentConfig = getConfig();
        currentConfig.hudLeaderboardsCompactMode = compactMode;
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized boolean isHudLeaderboardsCycleMode() {
        return getConfig().hudLeaderboardsCycleMode;
    }

    /** Enables/disables rotating leaderboard cycle mode. */
    public synchronized void setHudLeaderboardsCycleMode(boolean cycleMode) {
        CompanionConfig currentConfig = getConfig();
        currentConfig.hudLeaderboardsCycleMode = cycleMode;
        configManager.save(currentConfig);
        config = currentConfig;
    }

    public synchronized Map<String, CompanionConfig.HudWidgetPosition> getHudWidgetPositions() {
        Map<String, CompanionConfig.HudWidgetPosition> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, CompanionConfig.HudWidgetPosition> entry :
                getConfig().hudWidgetPositions.entrySet()) {
            CompanionConfig.HudWidgetPosition position = entry.getValue();
            snapshot.put(
                    entry.getKey(), new CompanionConfig.HudWidgetPosition(position.x, position.y));
        }
        return snapshot;
    }

    /** Returns persisted per-widget scale overrides (clamped to valid config bounds). */
    public synchronized Map<String, Double> getHudWidgetScales() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : getConfig().hudWidgetScales.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            snapshot.put(entry.getKey(), CompanionConfig.clampHudWidgetScale(entry.getValue()));
        }
        return snapshot;
    }

    /** Returns persisted per-widget width multiplier overrides (clamped). */
    public synchronized Map<String, Double> getHudWidgetWidthMultipliers() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : getConfig().hudWidgetWidthMultipliers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            snapshot.put(
                    entry.getKey(),
                    CompanionConfig.clampHudWidgetWidthMultiplier(entry.getValue()));
        }
        return snapshot;
    }

    /**
     * Returns one widget position.
     *
     * <p>When missing from config, default layout values are used.
     */
    public synchronized CompanionConfig.HudWidgetPosition getHudWidgetPosition(String widgetId) {
        CompanionConfig.HudWidgetPosition configured =
                getConfig().hudWidgetPositions.get(normalizeWidgetId(widgetId));
        if (configured == null) {
            CompanionConfig fallback = CompanionConfig.defaults();
            CompanionConfig.HudWidgetPosition fallbackPosition =
                    fallback.hudWidgetPositions.get(normalizeWidgetId(widgetId));
            if (fallbackPosition != null) {
                return new CompanionConfig.HudWidgetPosition(
                        fallbackPosition.x, fallbackPosition.y);
            }
            return new CompanionConfig.HudWidgetPosition(0.5D, 0.5D);
        }
        return new CompanionConfig.HudWidgetPosition(configured.x, configured.y);
    }

    /** Returns one widget scale using config value or default fallback. */
    public synchronized double getHudWidgetScale(String widgetId) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        Double configured = getConfig().hudWidgetScales.get(normalizedWidgetId);
        if (configured != null) {
            return CompanionConfig.clampHudWidgetScale(configured);
        }

        Double fallback = CompanionConfig.defaults().hudWidgetScales.get(normalizedWidgetId);
        if (fallback != null) {
            return CompanionConfig.clampHudWidgetScale(fallback);
        }

        return 1.0D;
    }

    /** Returns one widget width multiplier using config value or default fallback. */
    public synchronized double getHudWidgetWidthMultiplier(String widgetId) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        Double configured = getConfig().hudWidgetWidthMultipliers.get(normalizedWidgetId);
        if (configured != null) {
            return CompanionConfig.clampHudWidgetWidthMultiplier(configured);
        }

        Double fallback =
                CompanionConfig.defaults().hudWidgetWidthMultipliers.get(normalizedWidgetId);
        if (fallback != null) {
            return CompanionConfig.clampHudWidgetWidthMultiplier(fallback);
        }

        return 1.0D;
    }

    /** Persists one widget position override using normalized (0-1) coordinates. */
    public synchronized void setHudWidgetPosition(
            String widgetId, double normalizedX, double normalizedY) {
        if (widgetId == null || widgetId.isBlank()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudWidgetPositions.put(
                normalizeWidgetId(widgetId),
                new CompanionConfig.HudWidgetPosition(normalizedX, normalizedY));
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Persists one widget scale override. */
    public synchronized void setHudWidgetScale(String widgetId, double scale) {
        if (widgetId == null || widgetId.isBlank()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudWidgetScales.put(
                normalizeWidgetId(widgetId), CompanionConfig.clampHudWidgetScale(scale));
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Persists one widget width multiplier override. */
    public synchronized void setHudWidgetWidthMultiplier(String widgetId, double widthMultiplier) {
        if (widgetId == null || widgetId.isBlank()) {
            return;
        }

        CompanionConfig currentConfig = getConfig();
        currentConfig.hudWidgetWidthMultipliers.put(
                normalizeWidgetId(widgetId),
                CompanionConfig.clampHudWidgetWidthMultiplier(widthMultiplier));
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Restores widget layout/scale/width values to defaults. */
    public synchronized void resetHudLayout() {
        CompanionConfig defaults = CompanionConfig.defaults();
        CompanionConfig currentConfig = getConfig();
        currentConfig.hudWidgetPositions = new LinkedHashMap<>(defaults.hudWidgetPositions);
        currentConfig.hudWidgetScales = new LinkedHashMap<>(defaults.hudWidgetScales);
        currentConfig.hudWidgetWidthMultipliers =
                new LinkedHashMap<>(defaults.hudWidgetWidthMultipliers);
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Returns the localized bind label for the gang ping keybind. */
    public synchronized Text gangPingKeybindLabel() {
        if (gangPingKeyBinding == null) {
            initializePingKeybinds();
        }

        return gangPingKeyBinding.getBoundKeyLocalizedText();
    }

    /** Returns the localized bind label for the truce ping keybind. */
    public synchronized Text trucePingKeybindLabel() {
        if (trucePingKeyBinding == null) {
            initializePingKeybinds();
        }

        return trucePingKeyBinding.getBoundKeyLocalizedText();
    }

    /** Binds a new key for gang pings and persists game options. */
    public synchronized void bindGangPingKey(KeyInput keyInput) {
        if (gangPingKeyBinding == null) {
            initializePingKeybinds();
        }

        bindKey(gangPingKeyBinding, keyInput);
    }

    /** Binds a new key for truce pings and persists game options. */
    public synchronized void bindTrucePingKey(KeyInput keyInput) {
        if (trucePingKeyBinding == null) {
            initializePingKeybinds();
        }

        bindKey(trucePingKeyBinding, keyInput);
    }

    /** Restores ping keybinds to their default keys and persists game options. */
    public synchronized void resetPingKeybindsToDefault() {
        if (gangPingKeyBinding == null || trucePingKeyBinding == null) {
            initializePingKeybinds();
        }

        gangPingKeyBinding.setBoundKey(gangPingKeyBinding.getDefaultKey());
        trucePingKeyBinding.setBoundKey(trucePingKeyBinding.getDefaultKey());
        KeyBinding.updateKeysByCode();
        persistGameOptions();
    }

    /** Returns configured local ping visual duration in seconds (2..15). */
    public synchronized int pingVisualDurationSeconds() {
        return CompanionConfig.clampPingVisualDurationSeconds(
                getConfig().pingVisualDurationSeconds);
    }

    /** Persists local ping visual duration in seconds (2..15). */
    public synchronized void setPingVisualDurationSeconds(int durationSeconds) {
        CompanionConfig currentConfig = getConfig();
        int clamped = CompanionConfig.clampPingVisualDurationSeconds(durationSeconds);
        if (currentConfig.pingVisualDurationSeconds == clamped) {
            return;
        }

        currentConfig.pingVisualDurationSeconds = clamped;
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Returns whether ping visuals stay fixed at the first observed ping location. */
    public synchronized boolean pingVisualModeStatic() {
        return CompanionConfig.PING_VISUAL_MODE_STATIC.equals(
                CompanionConfig.normalizePingVisualMode(getConfig().pingVisualMode));
    }

    /** Persists whether ping visuals follow the target or remain fixed. */
    public synchronized void setPingVisualMode(String mode) {
        CompanionConfig currentConfig = getConfig();
        String normalizedMode = CompanionConfig.normalizePingVisualMode(mode);
        if (normalizedMode.equals(
                CompanionConfig.normalizePingVisualMode(currentConfig.pingVisualMode))) {
            return;
        }

        currentConfig.pingVisualMode = normalizedMode;
        configManager.save(currentConfig);
        config = currentConfig;
        clearPingAnchorSnapshots();
    }

    /** Returns whether ping particles are enabled in addition to beacon beams. */
    public synchronized boolean pingParticlesEnabled() {
        return getConfig().pingParticlesEnabled;
    }

    /** Persists whether ping particles are enabled in addition to beacon beams. */
    public synchronized void setPingParticlesEnabled(boolean enabled) {
        CompanionConfig currentConfig = getConfig();
        if (currentConfig.pingParticlesEnabled == enabled) {
            return;
        }

        currentConfig.pingParticlesEnabled = enabled;
        configManager.save(currentConfig);
        config = currentConfig;
    }

    /** Restores ping visual settings to defaults. */
    public synchronized void resetPingVisualSettingsToDefault() {
        CompanionConfig currentConfig = getConfig();
        currentConfig.pingVisualDurationSeconds =
                CompanionConfig.PING_VISUAL_DURATION_SECONDS_DEFAULT;
        currentConfig.pingVisualMode = CompanionConfig.PING_VISUAL_MODE_DEFAULT;
        currentConfig.pingParticlesEnabled = true;
        configManager.save(currentConfig);
        config = currentConfig;
        clearPingAnchorSnapshots();
    }

    /**
     * Rewrites crosshair targeting when peaceful-mining pass-through applies to an entity.
     *
     * <p>This ensures mining interactions still target the underlying block instead of marker
     * entities.
     */
    public void onCrosshairTargetUpdated(MinecraftClient client, float tickDelta) {
        synchronized (this) {
            if (!shouldApplyPeacefulMiningPassThrough(client)) {
                clearActivePeacefulMiningTarget();
                return;
            }

            HitResult currentTarget = client.crosshairTarget;

            if (!(currentTarget instanceof EntityHitResult entityHitResult)) {
                clearActivePeacefulMiningTarget();
                return;
            }

            int entityId = entityHitResult.getEntity().getId();

            if (!session.isPeacefulMiningPassThroughEntity(entityId)) {
                clearActivePeacefulMiningTarget();
                return;
            }

            Entity cameraEntity = client.getCameraEntity();

            if (cameraEntity == null || client.player == null) {
                clearActivePeacefulMiningTarget();
                return;
            }

            HitResult blockOnlyHit =
                    cameraEntity.raycast(
                            client.player.getBlockInteractionRange(), tickDelta, false);

            if (!(blockOnlyHit instanceof BlockHitResult blockHitResult)
                    || blockHitResult.getType() != HitResult.Type.BLOCK) {
                clearActivePeacefulMiningTarget();
                return;
            }

            client.crosshairTarget = blockHitResult;
            client.targetedEntity = null;
            activePeacefulMiningTargetEntityId = entityId;
        }
    }

    /** Returns whether an entity should be rendered with peaceful-mining ghost visuals. */
    public boolean isPeacefulMiningGhostedEntity(int entityId) {
        return entityId >= 0 && entityId == activePeacefulMiningTargetEntityId;
    }

    /** Renders a stack overlay for one handled-screen slot when overlay data is available. */
    public void renderHandledScreenSlotOverlay(DrawContext drawContext, Slot slot) {
        if (slot == null || !slot.hasStack()) {
            return;
        }

        ConnectionSessionState.ItemOverlayEntry overlayEntry;

        synchronized (this) {
            if (!shouldRenderAnyInventoryItemOverlays()) {
                return;
            }

            seedKnownOverlayStacksFromPlayerInventory(
                    MinecraftClient.getInstance(), session.inventoryItemOverlaysSnapshot());
            overlayEntry = resolveSlotOverlayEntry(slot);
        }

        if (overlayEntry == null || !shouldRenderOverlayType(overlayEntry.overlayType())) {
            return;
        }

        drawOverlayText(drawContext, slot.x, slot.y, overlayEntry);
    }

    /** Renders cursor-stack overlays in handled screens, with slot-click fallback support. */
    public void renderHandledScreenCursorOverlay(
            DrawContext drawContext,
            ScreenHandler handler,
            Slot lastClickedSlot,
            int mouseX,
            int mouseY) {
        if (drawContext == null || handler == null) {
            return;
        }

        ItemStack cursorStack = handler.getCursorStack();

        ConnectionSessionState.ItemOverlayEntry overlayEntry;

        synchronized (this) {
            if (!shouldRenderAnyInventoryItemOverlays()) {
                clearOverlayRenderCaches();
                return;
            }

            seedKnownOverlayStacksFromPlayerInventory(
                    MinecraftClient.getInstance(), session.inventoryItemOverlaysSnapshot());

            if (cursorStack.isEmpty()) {
                if (activeCursorOverlayEntry != null
                        && isMouseButtonHeld(MinecraftClient.getInstance())) {
                    overlayEntry = activeCursorOverlayEntry;
                } else if (pendingCursorOverlayFrames > 0 && activeCursorOverlayEntry != null) {
                    pendingCursorOverlayFrames--;
                    overlayEntry = activeCursorOverlayEntry;
                } else {
                    clearActiveCursorOverlay();
                    return;
                }
            } else {
                overlayEntry = resolveCursorOverlayEntry(handler, lastClickedSlot, cursorStack);
                boolean usedPendingFallback = false;

                if (overlayEntry == null) {
                    if (pendingCursorOverlayFrames > 0 && activeCursorOverlayEntry != null) {
                        pendingCursorOverlayFrames--;
                        overlayEntry = activeCursorOverlayEntry;
                        usedPendingFallback = true;
                    } else {
                        return;
                    }
                }

                if (!usedPendingFallback) {
                    pendingCursorOverlayFrames = 0;
                }
            }
        }

        if (overlayEntry == null || !shouldRenderOverlayType(overlayEntry.overlayType())) {
            clearActiveCursorOverlay();
            return;
        }

        int slotX = mouseX - 8;
        int slotY = mouseY - 8;
        drawOverlayText(drawContext, slotX, slotY, overlayEntry);
    }

    /**
     * Captures the most recent clicked-slot overlay to support cursor-drag rendering across frames.
     */
    public synchronized void rememberHandledScreenSlotClickOverlay(Slot slot) {
        if (slot == null || !shouldRenderAnyInventoryItemOverlays() || !slot.hasStack()) {
            return;
        }

        int slotIndex = playerStorageSlotIndex(slot);

        if (slotIndex < PLAYER_STORAGE_MIN_SLOT) {
            ConnectionSessionState.ItemOverlayEntry knownOverlayEntry =
                    findKnownOverlayForStack(slot.getStack());

            if (knownOverlayEntry == null
                    || !shouldRenderOverlayType(knownOverlayEntry.overlayType())) {
                clearActiveCursorOverlay();
                return;
            }

            cacheActiveCursorOverlay(-1, knownOverlayEntry, slot.getStack());
            pendingCursorOverlayFrames = PENDING_CURSOR_OVERLAY_FRAMES;
            return;
        }

        ConnectionSessionState.ItemOverlayEntry overlayEntry =
                session.getInventoryItemOverlay(slotIndex);

        if (overlayEntry == null || !shouldRenderOverlayType(overlayEntry.overlayType())) {
            clearActiveCursorOverlay();
            return;
        }

        cacheActiveCursorOverlay(slotIndex, overlayEntry, slot.getStack());
        pendingCursorOverlayFrames = PENDING_CURSOR_OVERLAY_FRAMES;
    }

    private synchronized void onJoin(MinecraftClient client) {
        session.reset();
        clearPingVisualTracking();
        clearOverlayRenderCaches();
        clearActivePeacefulMiningTarget();
        resetCompanionSendDiagnostics();
        gangPingKeyWasDown = false;
        trucePingKeyWasDown = false;
        initializeHelloRetryState();
        attemptSendClientHello(client);
    }

    private synchronized void onDisconnect() {
        session.reset();
        clearPingVisualTracking();
        clearOverlayRenderCaches();
        clearActivePeacefulMiningTarget();
        gangPingKeyWasDown = false;
        trucePingKeyWasDown = false;
        disableHelloRetryState();
    }

    private synchronized void onEndTick(MinecraftClient client) {
        modUpdateChecker.tick();

        if (client.player == null) {
            return;
        }

        processPingKeybinds(client);
        emitPingBeaconParticles(client);

        if (helloRetryCooldownTicks > 0) {
            helloRetryCooldownTicks--;
        }

        if (!session.helloSent()
                && helloRetriesRemaining > 0
                && helloRetryCooldownTicks <= 0
                && isCompanionTargetServer(client)) {
            attemptSendClientHello(client);
        }

        maybeLogCompanionPacketDiagnostics(client);
    }

    private synchronized void onWorldChange(MinecraftClient client, ClientWorld world) {
        session.clearInventoryItemOverlays();
        session.clearHudWidgets();
        session.clearPeacefulMiningPassThroughIds();
        session.clearGangPingBeaconIds();
        session.clearTrucePingBeaconIds();
        clearPingVisualTracking();
        clearOverlayRenderCaches();
        clearActivePeacefulMiningTarget();

        if (world == null || client.player == null) {
            return;
        }

        if (session.gateState().isEnabled() || session.helloSent()) {
            session.reset();
        }

        initializeHelloRetryState();
        attemptSendClientHello(client);
    }

    private synchronized void onScreenAfterInit(
            MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof GameMenuScreen)) {
            return;
        }

        int buttonWidth = 98;
        int buttonHeight = 20;
        int buttonX = screen.width - buttonWidth - 8;
        int buttonY = 8;
        Screens.getButtons(screen)
                .add(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.settings.button.open"),
                                        button -> client.setScreen(new FeatureSettingsScreen(this)))
                                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                                .build());
    }

    private void onPayloadReceived(
            CompanionRawPayload payload, ClientPlayNetworking.Context context) {
        byte[] payloadBytes = payload.payloadBytes();
        MinecraftClient client = context.client();
        client.execute(() -> onPayloadReceivedOnClient(payloadBytes, client));
    }

    private synchronized void onPayloadReceivedOnClient(
            byte[] payloadBytes, MinecraftClient client) {
        ProtocolCodec.DecodedFrame frame;

        try {
            frame = protocolCodec.decode(payloadBytes);
        } catch (BinaryDecodingException ex) {
            logMalformedOncePerConnection(ex);
            return;
        }

        ProtocolMessage message = frame.message();

        if (!session.gateState().shouldProcessIncoming(message)) {
            return;
        }

        switch (message) {
            case ProtocolMessage.ServerHelloS2C serverHello ->
                    handleServerHello(frame.protocolVersion(), serverHello, client);
            case ProtocolMessage.HudWidgetStateS2C hudWidgetState ->
                    handleHudWidgetState(hudWidgetState);
            case ProtocolMessage.EntityMarkerDeltaS2C markerDelta ->
                    handleEntityMarkerDelta(markerDelta);
            case ProtocolMessage.InventoryItemOverlaysS2C overlays ->
                    handleInventoryItemOverlays(overlays);
            default -> {}
        }
    }

    private void handleServerHello(
            int protocolVersion,
            ProtocolMessage.ServerHelloS2C serverHello,
            MinecraftClient client) {
        CompanionConfig currentConfig = getConfig();
        ConnectionGateState.EnableResult result =
                session.gateState()
                        .tryEnable(
                                protocolVersion,
                                serverHello,
                                currentConfig,
                                signatureVerifier::verifyServerHello);

        if (!result.enabled()) {
            LOGGER.debug("Ignored ServerHello due to {}", result);

            if (result == ConnectionGateState.EnableResult.SERVER_NOT_ALLOWED) {
                sendClientMessage(
                        client,
                        Text.translatable(
                                "text.cosmicprisonsmod.server_not_allowed",
                                serverHello.serverId()));
            }

            return;
        }

        session.setServerHello(serverHello);

        boolean supportsHudWidgets =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS)
                        != 0;
        boolean supportsInventoryItemOverlays =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS)
                        != 0;
        boolean supportsEntityMarkers =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.SERVER_FEATURE_ENTITY_MARKERS)
                        != 0;
        boolean supportsGangTrucePings =
                (serverHello.serverFeatureFlagsBitset()
                                & ProtocolConstants.FEATURE_GANG_TRUCE_PINGS)
                        != 0;
        session.setHudWidgetsSupported(supportsHudWidgets);
        session.setInventoryItemOverlaysSupported(supportsInventoryItemOverlays);

        if (!supportsHudWidgets) {
            session.clearHudWidgets();
        }

        if (!supportsInventoryItemOverlays) {
            session.clearInventoryItemOverlays();
            clearOverlayRenderCaches();
        }

        if (!supportsEntityMarkers) {
            session.clearPeacefulMiningPassThroughIds();
            clearActivePeacefulMiningTarget();
        }

        if (!supportsGangTrucePings) {
            session.clearGangPingBeaconIds();
            session.clearTrucePingBeaconIds();
            clearPingVisualTracking();
        }
    }

    private void handleInventoryItemOverlays(ProtocolMessage.InventoryItemOverlaysS2C overlays) {
        if (!session.inventoryItemOverlaysSupported()) {
            session.setInventoryItemOverlaysSupported(true);
        }

        session.replaceInventoryItemOverlays(overlays.overlays());
        cacheKnownOverlayStacksFromCurrentSnapshot();
    }

    private void handleHudWidgetState(ProtocolMessage.HudWidgetStateS2C hudWidgetState) {
        if (!session.hudWidgetsSupported()) {
            session.setHudWidgetsSupported(true);
        }

        session.replaceHudWidgets(hudWidgetState.widgets());
    }

    private void handleEntityMarkerDelta(ProtocolMessage.EntityMarkerDeltaS2C markerDelta) {
        switch (markerDelta.markerType()) {
            case ProtocolConstants.MARKER_TYPE_PEACEFUL_MINING_PASS_THROUGH ->
                    session.applyPeacefulMiningPassThroughDelta(
                            markerDelta.addEntityIds(), markerDelta.removeEntityIds());
            case ProtocolConstants.MARKER_TYPE_GANG_PING_BEACON ->
                    applyGangPingBeaconDelta(
                            markerDelta.addEntityIds(), markerDelta.removeEntityIds());
            case ProtocolConstants.MARKER_TYPE_TRUCE_PING_BEACON ->
                    applyTrucePingBeaconDelta(
                            markerDelta.addEntityIds(), markerDelta.removeEntityIds());
            default -> {}
        }
    }

    private void applyGangPingBeaconDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        session.applyGangPingBeaconDelta(addEntityIds, removeEntityIds);
        trackPingVisualDelta(
                gangPingVisualExpiryAtMillis,
                gangPingVisualSeededIds,
                addEntityIds,
                removeEntityIds);
    }

    private void applyTrucePingBeaconDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        session.applyTrucePingBeaconDelta(addEntityIds, removeEntityIds);
        trackPingVisualDelta(
                trucePingVisualExpiryAtMillis,
                trucePingVisualSeededIds,
                addEntityIds,
                removeEntityIds);
    }

    private void trackPingVisualDelta(
            Int2LongMap visualExpiryMap,
            IntSet visualSeededIds,
            List<Integer> addEntityIds,
            List<Integer> removeEntityIds) {
        long now = System.currentTimeMillis();
        long expiryAt = safeAddMillis(now, pingVisualDurationMillis());
        for (int entityId : addEntityIds) {
            if (entityId < 0) {
                continue;
            }

            visualExpiryMap.put(entityId, expiryAt);
            visualSeededIds.add(entityId);
        }

        for (int entityId : removeEntityIds) {
            if (entityId < 0) {
                continue;
            }

            visualSeededIds.remove(entityId);
        }
    }

    private ConnectionSessionState.ItemOverlayEntry resolveSlotOverlayEntry(Slot slot) {
        int slotIndex = playerStorageSlotIndex(slot);

        if (slotIndex >= PLAYER_STORAGE_MIN_SLOT) {
            ConnectionSessionState.ItemOverlayEntry directOverlayEntry =
                    session.getInventoryItemOverlay(slotIndex);

            if (directOverlayEntry != null) {
                rememberKnownOverlayStack(slot.getStack(), directOverlayEntry);
            }

            return directOverlayEntry;
        }

        return findKnownOverlayForStack(slot.getStack());
    }

    private ConnectionSessionState.ItemOverlayEntry resolveCursorOverlayEntry(
            ScreenHandler handler, Slot lastClickedSlot, ItemStack cursorStack) {
        if (activeCursorOverlayEntry != null && !activeCursorOverlayStack.isEmpty()) {
            if (activeCursorOverlaySlot >= PLAYER_STORAGE_MIN_SLOT) {
                ConnectionSessionState.ItemOverlayEntry refreshedOverlayEntry =
                        session.getInventoryItemOverlay(activeCursorOverlaySlot);

                if (refreshedOverlayEntry != null) {
                    activeCursorOverlayEntry = refreshedOverlayEntry;
                }
            }

            activeCursorOverlayStack = cursorStack.copy();
            rememberKnownOverlayStack(cursorStack, activeCursorOverlayEntry);
            return activeCursorOverlayEntry;
        }

        int clickedSlotIndex = playerStorageSlotIndex(lastClickedSlot);

        if (clickedSlotIndex >= PLAYER_STORAGE_MIN_SLOT) {
            ConnectionSessionState.ItemOverlayEntry clickedSlotOverlayEntry =
                    session.getInventoryItemOverlay(clickedSlotIndex);

            if (clickedSlotOverlayEntry != null) {
                cacheActiveCursorOverlay(clickedSlotIndex, clickedSlotOverlayEntry, cursorStack);
                return clickedSlotOverlayEntry;
            }
        }

        for (Slot slot : handler.slots) {
            int slotIndex = playerStorageSlotIndex(slot);

            if (slotIndex < PLAYER_STORAGE_MIN_SLOT || !slot.hasStack()) {
                continue;
            }

            if (!ItemStack.areItemsAndComponentsEqual(slot.getStack(), cursorStack)) {
                continue;
            }

            ConnectionSessionState.ItemOverlayEntry matchedOverlayEntry =
                    session.getInventoryItemOverlay(slotIndex);

            if (matchedOverlayEntry == null) {
                continue;
            }

            cacheActiveCursorOverlay(slotIndex, matchedOverlayEntry, cursorStack);
            return matchedOverlayEntry;
        }

        ConnectionSessionState.ItemOverlayEntry knownOverlayEntry =
                findKnownOverlayForStack(cursorStack);

        if (knownOverlayEntry != null) {
            cacheActiveCursorOverlay(-1, knownOverlayEntry, cursorStack);
            return knownOverlayEntry;
        }

        return null;
    }

    private static int playerStorageSlotIndex(Slot slot) {
        if (slot == null || !(slot.inventory instanceof PlayerInventory)) {
            return -1;
        }

        int slotIndex = slot.getIndex();

        if (slotIndex < PLAYER_STORAGE_MIN_SLOT || slotIndex > PLAYER_STORAGE_MAX_SLOT) {
            return -1;
        }

        return slotIndex;
    }

    private void cacheKnownOverlayStacksFromCurrentSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) {
            return;
        }

        seedKnownOverlayStacksFromPlayerInventory(client, session.inventoryItemOverlaysSnapshot());
    }

    private void seedKnownOverlayStacksFromPlayerInventory(
            MinecraftClient client,
            Map<Integer, ConnectionSessionState.ItemOverlayEntry> overlaySnapshot) {
        if (client.player == null || overlaySnapshot.isEmpty()) {
            return;
        }

        PlayerInventory playerInventory = client.player.getInventory();

        for (Map.Entry<Integer, ConnectionSessionState.ItemOverlayEntry> entry :
                overlaySnapshot.entrySet()) {
            int slotIndex = entry.getKey();

            if (slotIndex < PLAYER_STORAGE_MIN_SLOT || slotIndex > PLAYER_STORAGE_MAX_SLOT) {
                continue;
            }

            ItemStack stack = playerInventory.getStack(slotIndex);

            if (stack.isEmpty()) {
                continue;
            }

            rememberKnownOverlayStack(stack, entry.getValue());
        }
    }

    private void cacheActiveCursorOverlay(
            int slotIndex,
            ConnectionSessionState.ItemOverlayEntry overlayEntry,
            ItemStack cursorStack) {
        activeCursorOverlayEntry = overlayEntry;
        activeCursorOverlayStack = cursorStack.copy();
        activeCursorOverlaySlot = slotIndex;
        rememberKnownOverlayStack(cursorStack, overlayEntry);
    }

    private void clearActiveCursorOverlay() {
        activeCursorOverlayEntry = null;
        activeCursorOverlayStack = ItemStack.EMPTY;
        activeCursorOverlaySlot = -1;
        pendingCursorOverlayFrames = 0;
    }

    private void clearActivePeacefulMiningTarget() {
        activePeacefulMiningTargetEntityId = -1;
    }

    private void clearOverlayRenderCaches() {
        clearActiveCursorOverlay();
        knownOverlayStacks.clear();
    }

    private void rememberKnownOverlayStack(
            ItemStack stack, ConnectionSessionState.ItemOverlayEntry overlayEntry) {
        if (stack.isEmpty()) {
            return;
        }

        for (int i = 0; i < knownOverlayStacks.size(); i++) {
            KnownOverlayStack knownOverlayStack = knownOverlayStacks.get(i);

            if (!ItemStack.areItemsAndComponentsEqual(knownOverlayStack.stack(), stack)) {
                continue;
            }

            knownOverlayStacks.set(i, new KnownOverlayStack(stack.copy(), overlayEntry));
            return;
        }

        knownOverlayStacks.add(new KnownOverlayStack(stack.copy(), overlayEntry));

        if (knownOverlayStacks.size() > KNOWN_OVERLAY_STACK_LIMIT) {
            knownOverlayStacks.remove(0);
        }
    }

    private ConnectionSessionState.ItemOverlayEntry findKnownOverlayForStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ConnectionSessionState.ItemOverlayEntry itemAndComponentMatch = null;
        String stackName = stack.getName().getString();

        for (int i = knownOverlayStacks.size() - 1; i >= 0; i--) {
            KnownOverlayStack knownOverlayStack = knownOverlayStacks.get(i);

            if (ItemStack.areItemsAndComponentsEqual(knownOverlayStack.stack(), stack)) {
                return knownOverlayStack.overlayEntry();
            }

            if (itemAndComponentMatch != null) {
                continue;
            }

            ItemStack knownStack = knownOverlayStack.stack();

            if (knownStack.getItem() != stack.getItem()) {
                continue;
            }

            if (!knownStack.getName().getString().equals(stackName)) {
                continue;
            }

            itemAndComponentMatch = knownOverlayStack.overlayEntry();
        }

        return itemAndComponentMatch;
    }

    private static boolean isMouseButtonHeld(MinecraftClient client) {
        long windowHandle = client.getWindow().getHandle();
        return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                        == GLFW.GLFW_PRESS;
    }

    private void renderHud(DrawContext drawContext, RenderTickCounter tickCounter) {
        renderHotbarOverlays(drawContext);
        renderHudWidgetPanels(drawContext, false, Map.of(), Map.of(), Map.of());
        renderPingBeaconLabels(drawContext, tickCounter);
        renderOutdatedVersionNotice(drawContext);
    }

    private void renderOutdatedVersionNotice(DrawContext drawContext) {
        ModUpdateChecker.UpdateNotice notice = modUpdateChecker.notice();
        if (!notice.outdated()) {
            dismissedOutdatedNoticeLatestVersion = "";
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) {
            return;
        }

        String latestVersionToken = sanitizeVersionToken(notice.latestVersion());
        if (latestVersionToken.equalsIgnoreCase(dismissedOutdatedNoticeLatestVersion)) {
            return;
        }

        String lineOne = "CosmicPrisons Mod update available";
        String lineTwo =
                "Installed "
                        + notice.currentVersion()
                        + " -> Latest "
                        + notice.latestVersion()
                        + " (update with launcher)";

        int x = 8;
        int y = 8;
        int horizontalPadding = 6;
        int verticalPadding = 5;
        int lineGap = 2;
        int closeSize = 9;
        int closeInset = 4;
        int closeGap = 6;

        int textWidth = Math.max(textRenderer.getWidth(lineOne), textRenderer.getWidth(lineTwo));
        int textHeight = (textRenderer.fontHeight * 2) + lineGap;
        int boxWidth = textWidth + (horizontalPadding * 2) + closeSize + closeGap;
        int boxHeight = textHeight + (verticalPadding * 2);
        int closeMaxX = x + boxWidth - closeInset;
        int closeMinX = closeMaxX - closeSize;
        int closeMinY = y + closeInset;
        int closeMaxY = closeMinY + closeSize;
        boolean closeHovered =
                isOutdatedNoticeCloseHovered(client, closeMinX, closeMinY, closeMaxX, closeMaxY);

        if (closeHovered && consumeOutdatedNoticeCloseClick(client)) {
            dismissedOutdatedNoticeLatestVersion = latestVersionToken;
            return;
        }

        drawContext.fill(x, y, x + boxWidth, y + boxHeight, withAlpha(0x2D0E12, 224));
        drawContext.fill(x, y, x + boxWidth, y + 1, 0xFFFF5D5D);
        drawContext.fill(x, y, x + 1, y + boxHeight, 0xFFFF5D5D);
        drawContext.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF6F2D37);
        drawContext.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF6F2D37);

        if (closeHovered) {
            drawContext.fill(
                    closeMinX - 1,
                    closeMinY - 1,
                    closeMaxX + 1,
                    closeMaxY + 1,
                    withAlpha(0x8A2732, 172));
        }

        drawContext.drawHorizontalLine(closeMinX, closeMaxX, closeMinY, 0xFFFFD0D0);
        drawContext.drawHorizontalLine(closeMinX, closeMaxX, closeMaxY, 0xFFFFD0D0);
        drawContext.drawVerticalLine(closeMinX, closeMinY, closeMaxY, 0xFFFFD0D0);
        drawContext.drawVerticalLine(closeMaxX, closeMinY, closeMaxY, 0xFFFFD0D0);
        drawContext.drawTextWithShadow(textRenderer, "x", closeMinX + 2, closeMinY, 0xFFFFDCDC);

        int textX = x + horizontalPadding;
        int lineOneY = y + verticalPadding;
        int lineTwoY = lineOneY + textRenderer.fontHeight + lineGap;
        drawContext.drawTextWithShadow(textRenderer, lineOne, textX, lineOneY, 0xFFFFDCDC);
        drawContext.drawTextWithShadow(textRenderer, lineTwo, textX, lineTwoY, 0xFFFFB6C1);
    }

    private boolean consumeOutdatedNoticeCloseClick(MinecraftClient client) {
        boolean leftDown = isLeftMouseButtonDown(client);
        boolean clicked = leftDown && !outdatedNoticeLeftMouseDown;
        outdatedNoticeLeftMouseDown = leftDown;
        return clicked;
    }

    private boolean isOutdatedNoticeCloseHovered(
            MinecraftClient client, int minX, int minY, int maxX, int maxY) {
        if (client.currentScreen == null) {
            return false;
        }

        double scaledMouseX =
                client.mouse.getX()
                        * client.getWindow().getScaledWidth()
                        / client.getWindow().getWidth();
        double scaledMouseY =
                client.mouse.getY()
                        * client.getWindow().getScaledHeight()
                        / client.getWindow().getHeight();
        return scaledMouseX >= minX
                && scaledMouseX <= maxX
                && scaledMouseY >= minY
                && scaledMouseY <= maxY;
    }

    private static boolean isLeftMouseButtonDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
    }

    /**
     * Renders HUD panels in editor mode using optional transient overrides.
     *
     * <p>Overrides are not persisted; they are intended for live drag/scale preview workflows.
     */
    public void renderHudWidgetPanelsForEditor(
            DrawContext drawContext,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides,
            Map<String, Double> widthMultiplierOverrides) {
        renderHudWidgetPanels(
                drawContext, true, positionOverrides, scaleOverrides, widthMultiplierOverrides);
    }

    /**
     * Convenience overload of {@link #renderHudWidgetPanelsForEditor(DrawContext, Map, Map, Map)}.
     */
    public void renderHudWidgetPanelsForEditor(
            DrawContext drawContext,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides) {
        renderHudWidgetPanelsForEditor(drawContext, positionOverrides, scaleOverrides, Map.of());
    }

    /**
     * Convenience overload of {@link #renderHudWidgetPanelsForEditor(DrawContext, Map, Map, Map)}.
     */
    public void renderHudWidgetPanelsForEditor(
            DrawContext drawContext,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        renderHudWidgetPanelsForEditor(drawContext, positionOverrides, Map.of());
    }

    /**
     * Collects computed HUD panel geometry for editor interactions.
     *
     * <p>Useful for hit-testing and drag constraints in custom editor UIs.
     */
    public synchronized List<HudWidgetPanel> collectHudWidgetPanelsForEditor(
            int screenWidth,
            int screenHeight,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides,
            Map<String, Double> widthMultiplierOverrides) {
        return collectHudWidgetPanels(
                screenWidth,
                screenHeight,
                true,
                positionOverrides,
                scaleOverrides,
                widthMultiplierOverrides);
    }

    /**
     * Convenience overload of {@link #collectHudWidgetPanelsForEditor(int, int, Map, Map, Map)}.
     */
    public synchronized List<HudWidgetPanel> collectHudWidgetPanelsForEditor(
            int screenWidth,
            int screenHeight,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides) {
        return collectHudWidgetPanelsForEditor(
                screenWidth, screenHeight, positionOverrides, scaleOverrides, Map.of());
    }

    /**
     * Convenience overload of {@link #collectHudWidgetPanelsForEditor(int, int, Map, Map, Map)}.
     */
    public synchronized List<HudWidgetPanel> collectHudWidgetPanelsForEditor(
            int screenWidth,
            int screenHeight,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        return collectHudWidgetPanelsForEditor(
                screenWidth, screenHeight, positionOverrides, Map.of());
    }

    private void renderHotbarOverlays(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        if (client.player == null || textRenderer == null) {
            return;
        }

        Map<Integer, ConnectionSessionState.ItemOverlayEntry> overlaySnapshot;

        synchronized (this) {
            if (!shouldRenderAnyInventoryItemOverlays()) {
                return;
            }

            overlaySnapshot = session.inventoryItemOverlaysSnapshot();
            seedKnownOverlayStacksFromPlayerInventory(client, overlaySnapshot);
        }

        int centerX = drawContext.getScaledWindowWidth() / 2;
        int slotY = drawContext.getScaledWindowHeight() - 19;

        for (int slot = PLAYER_STORAGE_MIN_SLOT; slot <= HOTBAR_MAX_SLOT; slot++) {
            ConnectionSessionState.ItemOverlayEntry overlayEntry = overlaySnapshot.get(slot);

            if (overlayEntry == null || !shouldRenderOverlayType(overlayEntry.overlayType())) {
                continue;
            }

            int slotX = centerX - 90 + (slot * 20) + 2;
            drawOverlayText(drawContext, slotX, slotY, overlayEntry);
        }
    }

    private void renderHudWidgetPanels(
            DrawContext drawContext,
            boolean editorMode,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides,
            Map<String, Double> widthMultiplierOverrides) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer == null) {
            return;
        }

        List<HudWidgetPanel> panels =
                collectHudWidgetPanels(
                        drawContext.getScaledWindowWidth(),
                        drawContext.getScaledWindowHeight(),
                        editorMode,
                        positionOverrides,
                        scaleOverrides,
                        widthMultiplierOverrides);

        boolean hasEventsPanel = false;
        boolean hasCooldownPanel = false;
        boolean hasPetsPanel = false;
        for (HudWidgetPanel panel : panels) {
            if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(panel.widgetId())) {
                hasEventsPanel = true;
            } else if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(panel.widgetId())) {
                hasCooldownPanel = true;
            } else if (CompanionConfig.HUD_WIDGET_PETS_ID.equals(panel.widgetId())) {
                hasPetsPanel = true;
            }
            if (hasEventsPanel && hasCooldownPanel && hasPetsPanel) {
                break;
            }
        }
        if (!hasEventsPanel) {
            eventProgressBaselineSeconds.clear();
        }
        if (!hasCooldownPanel) {
            cooldownProgressBaselineSeconds.clear();
        }
        if (!hasPetsPanel) {
            petProgressBaselineSeconds.clear();
        }

        for (HudWidgetPanel panel : panels) {
            drawHudWidgetPanel(drawContext, textRenderer, panel, editorMode);
        }
    }

    private synchronized List<HudWidgetPanel> collectHudWidgetPanels(
            int screenWidth,
            int screenHeight,
            boolean editorMode,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides,
            Map<String, Double> widthMultiplierOverrides) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer == null || screenWidth <= 0 || screenHeight <= 0) {
            return List.of();
        }

        CompanionConfig currentConfig = getConfig();
        Map<String, ConnectionSessionState.HudWidgetEntry> widgetSnapshot =
                session.hudWidgetsSnapshot();
        long now = System.currentTimeMillis();
        List<HudWidgetPanel> panels = new ArrayList<>();
        boolean leaderboardCycleMode = currentConfig.hudLeaderboardsCycleMode;

        for (HudWidgetCatalog.WidgetDescriptor widget : HudWidgetCatalog.widgets()) {
            if (HudWidgetCatalog.isLeaderboardCycleWidget(widget.widgetId())) {
                continue;
            }

            if (leaderboardCycleMode && HudWidgetCatalog.isLeaderboardWidget(widget.widgetId())) {
                continue;
            }

            if (!editorMode && !shouldRenderWidget(widget.featureId())) {
                continue;
            }

            boolean compactMode = isCompactModeEnabled(widget.widgetId(), currentConfig);

            List<String> lines =
                    resolveWidgetLines(
                            widget.widgetId(),
                            widget.previewLines(),
                            widgetSnapshot,
                            currentConfig,
                            now,
                            editorMode);

            if (lines.isEmpty()) {
                continue;
            }

            int lineHeight = widgetLineHeight(widget.widgetId(), compactMode);
            int titleWidth =
                    textRenderer.getWidth(Text.translatable(widget.titleTranslationKey()))
                            + (HUD_PANEL_HORIZONTAL_PADDING * 2);
            int contentWidth =
                    measureWidgetLineWidth(widget.widgetId(), lines, textRenderer, compactMode);
            int desiredWidth =
                    Math.max(
                            basePanelWidth(widget.widgetId(), compactMode),
                            Math.max(titleWidth, contentWidth));
            int minPanelWidth = minPanelWidth(widget.widgetId(), compactMode);
            int clampedPanelWidth =
                    clamp(
                            desiredWidth,
                            minPanelWidth,
                            Math.max(
                                    minPanelWidth, Math.min(screenWidth - 4, HUD_PANEL_MAX_WIDTH)));
            float widthMultiplier =
                    (float)
                            resolveWidgetWidthMultiplier(
                                    currentConfig, widget.widgetId(), widthMultiplierOverrides);
            int adjustedPanelWidth =
                    clamp(
                            Math.round(clampedPanelWidth * widthMultiplier),
                            minPanelWidth,
                            Math.max(
                                    minPanelWidth, Math.min(screenWidth - 4, HUD_PANEL_MAX_WIDTH)));
            int panelHeight =
                    HUD_PANEL_HEADER_HEIGHT
                            + (HUD_PANEL_VERTICAL_PADDING * 2)
                            + (lineHeight * lines.size());
            float scale =
                    (float) resolveWidgetScale(currentConfig, widget.widgetId(), scaleOverrides);
            int physicalPanelWidth = Math.max(24, Math.round(adjustedPanelWidth * scale));
            int physicalPanelHeight = Math.max(20, Math.round(panelHeight * scale));

            CompanionConfig.HudWidgetPosition position =
                    resolveWidgetPosition(currentConfig, widget.widgetId(), positionOverrides);
            int maxX = Math.max(0, screenWidth - physicalPanelWidth);
            int maxY = Math.max(0, screenHeight - physicalPanelHeight);
            int panelX = clamp((int) Math.round(position.x * maxX), 0, maxX);
            int panelY = clamp((int) Math.round(position.y * maxY), 0, maxY);

            panels.add(
                    new HudWidgetPanel(
                            widget.widgetId(),
                            widget.titleTranslationKey(),
                            widget.accentColor(),
                            panelX,
                            panelY,
                            adjustedPanelWidth,
                            panelHeight,
                            lineHeight,
                            scale,
                            compactMode,
                            lines));
        }

        if (leaderboardCycleMode
                && (editorMode || shouldRenderWidget(ClientFeatures.HUD_LEADERBOARDS_ID))) {
            addLeaderboardCyclePanel(
                    panels,
                    screenWidth,
                    screenHeight,
                    editorMode,
                    currentConfig,
                    widgetSnapshot,
                    now,
                    positionOverrides,
                    scaleOverrides,
                    widthMultiplierOverrides,
                    textRenderer);
        }

        return panels;
    }

    private List<String> resolveWidgetLines(
            String widgetId,
            List<String> previewLines,
            Map<String, ConnectionSessionState.HudWidgetEntry> widgetSnapshot,
            CompanionConfig currentConfig,
            long now,
            boolean editorMode) {
        if (HudWidgetCatalog.isLeaderboardWidget(widgetId)
                && !editorMode
                && !isLeaderboardVisible(widgetId, currentConfig)) {
            return List.of();
        }

        if (!shouldRenderHudWidgets()) {
            if (!editorMode) {
                return List.of();
            }
            List<String> preview = List.copyOf(previewLines);
            return HudWidgetCatalog.isLeaderboardWidget(widgetId)
                    ? stripLeaderboardHeader(new ArrayList<>(preview))
                    : preview;
        }

        ConnectionSessionState.HudWidgetEntry entry =
                widgetSnapshot.get(normalizeWidgetId(widgetId));
        if (entry == null || isHudWidgetExpired(entry, now)) {
            if (!editorMode) {
                return List.of();
            }
            List<String> preview = List.copyOf(previewLines);
            return HudWidgetCatalog.isLeaderboardWidget(widgetId)
                    ? stripLeaderboardHeader(new ArrayList<>(preview))
                    : preview;
        }

        List<String> lines = new ArrayList<>(entry.lines().size());
        for (String line : entry.lines()) {
            if (line != null && !line.isBlank()) {
                lines.add(line.trim());
            }
        }

        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            lines = resolveEventLines(lines, currentConfig.hudEventVisibility);
        } else if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
            lines.removeIf(line -> !HudWidgetCatalog.isCooldownLineActive(line));
            lines.sort(
                    Comparator.comparingLong(
                            line ->
                                    HudWidgetCatalog.parseDurationSeconds(
                                                    HudWidgetCatalog.splitLine(line).value())
                                            .orElse(Long.MAX_VALUE)));
        } else if (HudWidgetCatalog.isLeaderboardWidget(widgetId)) {
            lines = stripLeaderboardHeader(lines);
        }

        if (lines.size() > ProtocolConstants.MAX_WIDGET_LINES) {
            lines = new ArrayList<>(lines.subList(0, ProtocolConstants.MAX_WIDGET_LINES));
        }

        if (lines.isEmpty()) {
            if (!editorMode) {
                return List.of();
            }
            List<String> preview = List.copyOf(previewLines);
            return HudWidgetCatalog.isLeaderboardWidget(widgetId)
                    ? stripLeaderboardHeader(new ArrayList<>(preview))
                    : preview;
        }

        return lines;
    }

    private static List<String> stripLeaderboardHeader(List<String> lines) {
        if (lines.size() <= 1) {
            return lines;
        }
        String first = lines.get(0);
        if (first == null || first.isBlank()) {
            return new ArrayList<>(lines.subList(1, lines.size()));
        }
        if (parseLeaderboardEntry(first.trim()) == null) {
            return new ArrayList<>(lines.subList(1, lines.size()));
        }
        return lines;
    }

    private static List<String> resolveEventLines(
            List<String> rawLines, Map<String, Boolean> eventVisibilityConfig) {
        List<HudWidgetCatalog.ParsedLine> sorted =
                HudWidgetCatalog.sortEventsClosestFirst(rawLines, eventVisibilityConfig);
        List<String> lines = new ArrayList<>(sorted.size());

        for (HudWidgetCatalog.ParsedLine parsedLine : sorted) {
            if (parsedLine.label().isEmpty()) {
                continue;
            }

            lines.add(parsedLine.asText());
            if (lines.size() >= ProtocolConstants.MAX_WIDGET_LINES) {
                break;
            }
        }

        return lines;
    }

    private void addLeaderboardCyclePanel(
            List<HudWidgetPanel> panels,
            int screenWidth,
            int screenHeight,
            boolean editorMode,
            CompanionConfig currentConfig,
            Map<String, ConnectionSessionState.HudWidgetEntry> widgetSnapshot,
            long now,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides,
            Map<String, Double> scaleOverrides,
            Map<String, Double> widthMultiplierOverrides,
            TextRenderer textRenderer) {
        List<LeaderboardCandidate> candidates = new ArrayList<>();

        for (HudWidgetCatalog.WidgetDescriptor descriptor : HudWidgetCatalog.leaderboardWidgets()) {
            if (!isLeaderboardVisible(descriptor.widgetId(), currentConfig)) {
                continue;
            }

            List<String> lines =
                    resolveWidgetLines(
                            descriptor.widgetId(),
                            descriptor.previewLines(),
                            widgetSnapshot,
                            currentConfig,
                            now,
                            editorMode);
            if (lines.isEmpty()) {
                continue;
            }

            candidates.add(new LeaderboardCandidate(descriptor, lines));
        }

        if (candidates.isEmpty()) {
            if (!editorMode) {
                return;
            }

            HudWidgetCatalog.WidgetDescriptor fallbackDescriptor =
                    HudWidgetCatalog.leaderboardWidgets().stream()
                            .filter(
                                    descriptor ->
                                            isLeaderboardVisible(
                                                    descriptor.widgetId(), currentConfig))
                            .findFirst()
                            .orElse(HudWidgetCatalog.leaderboardWidgets().get(0));
            candidates.add(
                    new LeaderboardCandidate(
                            fallbackDescriptor,
                            stripLeaderboardHeader(
                                    new ArrayList<>(fallbackDescriptor.previewLines()))));
        }

        int index = 0;
        if (candidates.size() > 1) {
            index = (int) ((now / LEADERBOARD_CYCLE_INTERVAL_MILLIS) % candidates.size());
        }

        LeaderboardCandidate selected = candidates.get(index);
        String cycleWidgetId = CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID;
        boolean compactMode = isCompactModeEnabled(cycleWidgetId, currentConfig);
        List<String> lines = selected.lines();

        int lineHeight = widgetLineHeight(cycleWidgetId, compactMode);
        int titleWidth =
                textRenderer.getWidth(
                                Text.translatable(selected.descriptor().titleTranslationKey()))
                        + (HUD_PANEL_HORIZONTAL_PADDING * 2);
        int contentWidth = measureWidgetLineWidth(cycleWidgetId, lines, textRenderer, compactMode);
        int desiredWidth =
                Math.max(
                        basePanelWidth(cycleWidgetId, compactMode),
                        Math.max(titleWidth, contentWidth));
        int minPanelWidth = minPanelWidth(cycleWidgetId, compactMode);
        int clampedPanelWidth =
                clamp(
                        desiredWidth,
                        minPanelWidth,
                        Math.max(minPanelWidth, Math.min(screenWidth - 4, HUD_PANEL_MAX_WIDTH)));
        float widthMultiplier =
                (float)
                        resolveWidgetWidthMultiplier(
                                currentConfig, cycleWidgetId, widthMultiplierOverrides);
        int adjustedPanelWidth =
                clamp(
                        Math.round(clampedPanelWidth * widthMultiplier),
                        minPanelWidth,
                        Math.max(minPanelWidth, Math.min(screenWidth - 4, HUD_PANEL_MAX_WIDTH)));
        int panelHeight =
                HUD_PANEL_HEADER_HEIGHT
                        + (HUD_PANEL_VERTICAL_PADDING * 2)
                        + (lineHeight * lines.size());
        float scale = (float) resolveWidgetScale(currentConfig, cycleWidgetId, scaleOverrides);
        int physicalPanelWidth = Math.max(24, Math.round(adjustedPanelWidth * scale));
        int physicalPanelHeight = Math.max(20, Math.round(panelHeight * scale));

        CompanionConfig.HudWidgetPosition position =
                resolveWidgetPosition(currentConfig, cycleWidgetId, positionOverrides);
        int maxX = Math.max(0, screenWidth - physicalPanelWidth);
        int maxY = Math.max(0, screenHeight - physicalPanelHeight);
        int panelX = clamp((int) Math.round(position.x * maxX), 0, maxX);
        int panelY = clamp((int) Math.round(position.y * maxY), 0, maxY);

        panels.add(
                new HudWidgetPanel(
                        cycleWidgetId,
                        selected.descriptor().titleTranslationKey(),
                        selected.descriptor().accentColor(),
                        panelX,
                        panelY,
                        adjustedPanelWidth,
                        panelHeight,
                        lineHeight,
                        scale,
                        compactMode,
                        lines));
    }

    private static boolean isLeaderboardVisible(String widgetId, CompanionConfig config) {
        if (widgetId == null || config == null) {
            return false;
        }
        return !Boolean.FALSE.equals(
                config.hudLeaderboardVisibility.get(normalizeWidgetId(widgetId)));
    }

    private static boolean isCompactModeEnabled(String widgetId, CompanionConfig config) {
        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            return config.hudEventsCompactMode;
        }
        if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId)) {
            return config.hudSatchelsCompactMode;
        }
        if (CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(widgetId)
                || HudWidgetCatalog.isLeaderboardWidget(widgetId)) {
            return config.hudLeaderboardsCompactMode;
        }
        return false;
    }

    private static CompanionConfig.HudWidgetPosition resolveWidgetPosition(
            CompanionConfig config,
            String widgetId,
            Map<String, CompanionConfig.HudWidgetPosition> positionOverrides) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        CompanionConfig.HudWidgetPosition override = positionOverrides.get(normalizedWidgetId);
        if (override != null) {
            return new CompanionConfig.HudWidgetPosition(override.x, override.y);
        }

        CompanionConfig.HudWidgetPosition configured =
                config.hudWidgetPositions.get(normalizedWidgetId);
        if (configured != null) {
            return new CompanionConfig.HudWidgetPosition(configured.x, configured.y);
        }

        CompanionConfig.HudWidgetPosition fallback =
                CompanionConfig.defaults().hudWidgetPositions.get(normalizedWidgetId);
        if (fallback != null) {
            return fallback;
        }

        return new CompanionConfig.HudWidgetPosition(0.5D, 0.5D);
    }

    private static double resolveWidgetScale(
            CompanionConfig config, String widgetId, Map<String, Double> scaleOverrides) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        Double override = scaleOverrides.get(normalizedWidgetId);
        if (override != null) {
            return CompanionConfig.clampHudWidgetScale(override);
        }

        Double configured = config.hudWidgetScales.get(normalizedWidgetId);
        if (configured != null) {
            return CompanionConfig.clampHudWidgetScale(configured);
        }

        Double fallback = CompanionConfig.defaults().hudWidgetScales.get(normalizedWidgetId);
        if (fallback != null) {
            return CompanionConfig.clampHudWidgetScale(fallback);
        }

        return 1.0D;
    }

    private static double resolveWidgetWidthMultiplier(
            CompanionConfig config, String widgetId, Map<String, Double> widthMultiplierOverrides) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        Double override = widthMultiplierOverrides.get(normalizedWidgetId);
        if (override != null) {
            return CompanionConfig.clampHudWidgetWidthMultiplier(override);
        }

        Double configured = config.hudWidgetWidthMultipliers.get(normalizedWidgetId);
        if (configured != null) {
            return CompanionConfig.clampHudWidgetWidthMultiplier(configured);
        }

        Double fallback =
                CompanionConfig.defaults().hudWidgetWidthMultipliers.get(normalizedWidgetId);
        if (fallback != null) {
            return CompanionConfig.clampHudWidgetWidthMultiplier(fallback);
        }

        return 1.0D;
    }

    private static boolean isHudWidgetExpired(
            ConnectionSessionState.HudWidgetEntry widget, long now) {
        int ttlSeconds = widget.ttlSeconds();
        if (ttlSeconds <= 0) {
            return false;
        }

        long expiresAt = widget.receivedAtEpochMillis() + (ttlSeconds * 1000L);
        return now > expiresAt;
    }

    private void drawHudWidgetPanel(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            boolean editorMode) {
        int x = panel.x();
        int y = panel.y();
        int right = x + panel.physicalWidth();
        int bottom = y + panel.physicalHeight();
        int accent = panel.accentColor();

        drawContext.fill(
                x - 1 - HUD_PANEL_EDGE_INSET,
                y - 1 - HUD_PANEL_EDGE_INSET,
                right + 1 + HUD_PANEL_EDGE_INSET,
                bottom + 1 + HUD_PANEL_EDGE_INSET,
                withAlpha(0x000000, 80));
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(x, y);
        drawContext.getMatrices().scale(panel.scale(), panel.scale());

        int localRight = panel.width();
        int localBottom = panel.height();
        int contentTop = HUD_PANEL_HEADER_HEIGHT + HUD_PANEL_VERTICAL_PADDING;
        int contentLeft = HUD_PANEL_HORIZONTAL_PADDING;
        int contentRight = localRight - HUD_PANEL_HORIZONTAL_PADDING;

        drawContext.fill(
                0, 0, localRight, localBottom, withAlpha(0x0B111A, editorMode ? 188 : 210));
        drawContext.fill(0, 0, localRight, HUD_PANEL_HEADER_HEIGHT, withAlpha(0x111A2A, 228));
        drawContext.fill(1, 1, localRight - 1, HUD_PANEL_HEADER_HEIGHT - 1, withAlpha(accent, 48));
        drawContext.fill(0, 0, localRight, 1, withAlpha(accent, 255));
        drawContext.fill(0, localBottom - 1, localRight, localBottom, withAlpha(0x2D3A4F, 255));
        drawContext.fill(0, 0, 1, localBottom, withAlpha(0x2D3A4F, 255));
        drawContext.fill(localRight - 1, 0, localRight, localBottom, withAlpha(0x2D3A4F, 255));

        Text panelTitle = Text.translatable(panel.titleTranslationKey());
        if (CompanionConfig.HUD_WIDGET_GANG_ID.equals(panel.widgetId())
                && !panel.lines().isEmpty()) {
            String gangTitle = panel.lines().get(0);
            if (gangTitle != null && !gangTitle.isBlank()) {
                panelTitle = Text.literal(gangTitle.trim());
            }
        }
        int titleX =
                CompanionConfig.HUD_WIDGET_GANG_ID.equals(panel.widgetId())
                        ? Math.max(
                                HUD_PANEL_HORIZONTAL_PADDING,
                                (localRight - textRenderer.getWidth(panelTitle)) / 2)
                        : HUD_PANEL_HORIZONTAL_PADDING;
        drawContext.drawTextWithShadow(textRenderer, panelTitle, titleX, 2, 0xFFFFFFFF);

        if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(panel.widgetId())) {
            drawCooldownRows(
                    drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
        } else if (CompanionConfig.HUD_WIDGET_PETS_ID.equals(panel.widgetId())) {
            drawPetRows(drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
        } else if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(panel.widgetId())) {
            if (panel.compactMode()) {
                drawEventRowsCompact(
                        drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
            } else {
                drawEventRows(
                        drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
            }
        } else if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(panel.widgetId())) {
            if (panel.compactMode()) {
                drawSatchelRowsCompact(
                        drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
            } else {
                drawSatchelRows(
                        drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
            }
        } else if (CompanionConfig.HUD_WIDGET_GANG_ID.equals(panel.widgetId())) {
            drawGangRows(drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
        } else if (CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(panel.widgetId())
                || HudWidgetCatalog.isLeaderboardWidget(panel.widgetId())) {
            if (panel.compactMode()) {
                drawLeaderboardRowsCompact(
                        drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
            } else {
                drawLeaderboardRows(
                        drawContext, textRenderer, panel, contentLeft, contentTop, contentRight);
            }
        } else {
            int lineY = contentTop;
            for (String line : panel.lines()) {
                drawContext.drawTextWithShadow(textRenderer, line, contentLeft, lineY, 0xFFE8EDF8);
                lineY += panel.lineHeight();
            }
        }

        if (editorMode) {
            drawContext.drawTextWithShadow(
                    textRenderer,
                    Text.translatable("text.cosmicprisonsmod.hud.editor.drag_hint"),
                    localRight - 30,
                    2,
                    withAlpha(0xFFFFFF, 225));
        }

        drawContext.getMatrices().popMatrix();
    }

    private void drawCooldownRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        Set<String> seenKeys = new HashSet<>();
        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            String progressKey = normalizeCooldownProgressKey(parsedLine.label());
            if (!progressKey.isBlank()) {
                seenKeys.add(progressKey);
            }
            float progress = cooldownProgressForLine(progressKey, parsedLine.value(), remaining);
            int statusColor = statusColor(parsedLine.value(), remaining);
            int iconLeft = contentLeft;
            int iconTop = centeredItemTop(rowY, panel.lineHeight());
            ItemStack iconStack = cooldownIcon(parsedLine.label()).getDefaultStack();
            drawContext.drawItem(iconStack, iconLeft, iconTop);

            String labelText = compactCooldownLabel(parsedLine.label());
            String valueText = compactStatusText(parsedLine.value(), true);
            int valueSlotWidth = Math.max(36, Math.min(52, contentRight - contentLeft - 62));
            int valueAreaLeft = Math.max(contentLeft + 66, contentRight - valueSlotWidth);
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = valueAreaLeft + Math.max(0, valueSlotWidth - valueWidth);
            int labelX = iconLeft + 22;
            int labelMaxWidth = Math.max(12, valueAreaLeft - labelX - 4);
            labelText = textRenderer.trimToWidth(labelText, labelMaxWidth);

            int textY = rowY + 2;
            drawContext.drawTextWithShadow(textRenderer, labelText, labelX, textY, 0xFFE6EEFC);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, textY, statusColor);

            int barX = labelX;
            int barY = textY + textRenderer.fontHeight + 1;
            int barWidth = Math.max(8, valueAreaLeft - barX - 4);
            drawContext.fill(barX, barY, barX + barWidth, barY + 2, withAlpha(0x31415C, 190));
            int filled = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
            if (filled > 0) {
                drawContext.fill(barX, barY, barX + filled, barY + 2, withAlpha(statusColor, 240));
            }

            rowY += panel.lineHeight();
        }

        if (!seenKeys.isEmpty()) {
            cooldownProgressBaselineSeconds.keySet().retainAll(seenKeys);
        }
    }

    private void drawPetRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        Set<String> seenKeys = new HashSet<>();
        int rowY = contentTop;
        for (String line : panel.lines()) {
            PetWidgetRow petRow = parsePetWidgetRow(line);
            if (petRow == null) {
                rowY += panel.lineHeight();
                continue;
            }

            if (petRow.overflow()) {
                String overflowText =
                        textRenderer.trimToWidth(
                                petRow.displayName(), Math.max(8, contentRight - contentLeft - 2));
                drawContext.drawTextWithShadow(
                        textRenderer, overflowText, contentLeft + 1, rowY + 7, 0xFF9CB0C8);
                rowY += panel.lineHeight();
                continue;
            }

            int iconLeft = contentLeft;
            int iconTop = centeredItemTop(rowY, panel.lineHeight());
            int labelX = iconLeft + 20;
            String levelText = petLevelText(petRow);
            String xpText = petLevelProgressText(petRow);
            int rightColumnWidth =
                    Math.max(textRenderer.getWidth(levelText), textRenderer.getWidth(xpText));
            int rightColumnX = Math.max(contentLeft + 92, contentRight - rightColumnWidth);
            int labelMaxWidth = Math.max(10, rightColumnX - labelX - 6);

            drawContext.drawItem(
                    resolvePetHudHeadStack(petRow.petKey(), petRow.displayName()),
                    iconLeft,
                    iconTop);

            String primaryLabel = textRenderer.trimToWidth(petPrimaryLabel(petRow), labelMaxWidth);
            drawContext.drawTextWithShadow(
                    textRenderer, primaryLabel, labelX, rowY + 1, 0xFFEAF2FF);
            drawContext.drawTextWithShadow(
                    textRenderer, levelText, rightColumnX, rowY + 1, 0xFFDCE7F8);
            drawContext.drawTextWithShadow(
                    textRenderer, xpText, rightColumnX, rowY + 11, petLevelProgressColor(petRow));

            if (petRow.statusSeconds() <= 0L) {
                drawContext.drawTextWithShadow(
                        textRenderer, "READY", labelX, rowY + 11, 0xFF7CF2A0);
            } else {
                String progressKey = normalizePetProgressKey(petRow);
                if (!progressKey.isBlank()) {
                    seenKeys.add(progressKey);
                }
                float progress = petProgressForRow(progressKey, petRow.statusSeconds());
                String statusText = petStatusText(petRow);
                int statusColor = petStatusColor(petRow);
                int barX = labelX;
                int barY = rowY + panel.lineHeight() - 4;
                int barWidth = Math.max(12, rightColumnX - barX - 6);
                drawContext.drawTextWithShadow(
                        textRenderer, statusText, barX, rowY + 11, statusColor);
                drawContext.fill(barX, barY, barX + barWidth, barY + 2, withAlpha(0x31415C, 190));
                int filled = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
                if (filled > 0) {
                    drawContext.fill(
                            barX, barY, barX + filled, barY + 2, withAlpha(statusColor, 240));
                }
            }

            rowY += panel.lineHeight();
        }

        if (seenKeys.isEmpty()) {
            petProgressBaselineSeconds.clear();
        } else {
            petProgressBaselineSeconds.keySet().retainAll(seenKeys);
        }
    }

    private void drawEventRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        Set<String> seenKeys = new HashSet<>();
        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            var descriptor = HudWidgetCatalog.findEventByLabel(parsedLine.label());
            String eventKey = descriptor.map(HudWidgetCatalog.EventDescriptor::key).orElse("");
            int rowAccent = eventAccentColor(eventKey, panel.accentColor());
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            int statusColor = statusColor(parsedLine.value(), remaining);
            String progressKey =
                    eventKey.isBlank()
                            ? normalizeEventProgressKey(parsedLine.label())
                            : normalizeEventProgressKey(eventKey);
            if (!progressKey.isBlank()) {
                seenKeys.add(progressKey);
            }
            float progress = eventProgressForLine(progressKey, parsedLine.value(), remaining);

            int iconLeft = contentLeft;
            int iconTop = centeredItemTop(rowY, panel.lineHeight());
            drawContext.drawItem(eventIcon(eventKey).getDefaultStack(), iconLeft, iconTop);

            String labelText = compactEventLabel(parsedLine.label());
            String valueText = compactStatusText(parsedLine.value(), false);
            int valueSlotWidth = Math.max(36, Math.min(52, contentRight - contentLeft - 62));
            int valueAreaLeft = Math.max(contentLeft + 58, contentRight - valueSlotWidth);
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = valueAreaLeft + Math.max(0, valueSlotWidth - valueWidth);
            int labelX = iconLeft + 18;
            int labelMaxWidth = Math.max(12, valueAreaLeft - labelX - 4);
            labelText = textRenderer.trimToWidth(labelText, labelMaxWidth);

            int textY = rowY + 1;
            drawContext.drawTextWithShadow(textRenderer, labelText, labelX, textY, 0xFFE4EDF8);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, textY, statusColor);

            int barX = labelX;
            int barY = rowY + panel.lineHeight() - 3;
            int barWidth = Math.max(8, valueAreaLeft - barX - 4);
            drawContext.fill(barX, barY, barX + barWidth, barY + 2, withAlpha(0x31415C, 190));
            int filled = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
            if (filled > 0) {
                drawContext.fill(barX, barY, barX + filled, barY + 2, withAlpha(rowAccent, 240));
            }

            rowY += panel.lineHeight();
        }

        if (!seenKeys.isEmpty()) {
            eventProgressBaselineSeconds.keySet().retainAll(seenKeys);
        }
    }

    private static void drawEventRowsCompact(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            var descriptor = HudWidgetCatalog.findEventByLabel(parsedLine.label());
            String eventKey = descriptor.map(HudWidgetCatalog.EventDescriptor::key).orElse("");
            String tag =
                    descriptor
                            .map(HudWidgetCatalog.EventDescriptor::iconTag)
                            .orElse(compactEventTag(parsedLine.label()));
            String tagText = "[" + tag + "]";
            String valueText = compactStatusText(parsedLine.value(), false);
            OptionalLong remaining = HudWidgetCatalog.parseDurationSeconds(parsedLine.value());
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = Math.max(contentLeft + 48, contentRight - valueWidth);
            int tagColor = eventAccentColor(eventKey, panel.accentColor());
            int tagMaxWidth = Math.max(12, valueX - contentLeft - 3);
            String clampedTag = textRenderer.trimToWidth(tagText, tagMaxWidth);

            drawContext.drawTextWithShadow(textRenderer, clampedTag, contentLeft, rowY, tagColor);
            drawContext.drawTextWithShadow(
                    textRenderer,
                    valueText,
                    valueX,
                    rowY,
                    statusColor(parsedLine.value(), remaining));
            rowY += panel.lineHeight();
        }
    }

    private static void drawSatchelRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            float progress = parseSatchelProgress(parsedLine.value());
            boolean satchelFull = isSatchelFull(parsedLine.value(), progress);
            String valueText = satchelFull ? "FULL!" : compactSatchelValue(parsedLine.value());
            String label = parsedLine.label();
            int iconLeft = contentLeft;
            int iconTop = centeredItemTop(rowY, panel.lineHeight());
            int valueSlotWidth = Math.max(40, Math.min(58, contentRight - contentLeft - 62));
            int valueAreaLeft = Math.max(contentLeft + 58, contentRight - valueSlotWidth);
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = valueAreaLeft + Math.max(0, valueSlotWidth - valueWidth);
            if (satchelFull) {
                int pulseAlpha = satchelFullPulseAlpha();
                drawContext.fill(
                        iconLeft + 17,
                        rowY - 1,
                        contentRight,
                        rowY + panel.lineHeight() - 2,
                        withAlpha(0x8F1A1A, pulseAlpha));
            }
            drawContext.drawItem(satchelIcon(label).getDefaultStack(), iconLeft, iconTop);

            int labelX = iconLeft + 18;
            int labelMaxWidth = Math.max(12, valueAreaLeft - labelX - 4);
            String labelText =
                    textRenderer.trimToWidth(satchelFull ? label + " !" : label, labelMaxWidth);
            int labelColor = satchelFull ? 0xFFFFC1C1 : 0xFFE5F4EA;
            int valueColor = satchelFull ? 0xFFFF5D5D : 0xFF9EF0BC;
            int textY = rowY + 1;
            drawContext.drawTextWithShadow(textRenderer, labelText, labelX, textY, labelColor);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, textY, valueColor);

            int barX = labelX;
            int barY = rowY + panel.lineHeight() - 3;
            int barWidth = Math.max(8, valueAreaLeft - barX - 4);
            int trackColor = satchelFull ? withAlpha(0x5D2020, 212) : withAlpha(0x33503F, 185);
            int fillColor = satchelFull ? withAlpha(0xFF5555, 245) : withAlpha(0x66D89A, 235);
            drawContext.fill(barX, barY, barX + barWidth, barY + 2, trackColor);
            int filled = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
            if (filled > 0) {
                drawContext.fill(barX, barY, barX + filled, barY + 2, fillColor);
            }

            rowY += panel.lineHeight();
        }
    }

    private static void drawSatchelRowsCompact(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        int rowY = contentTop;
        for (String line : panel.lines()) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            float progress = parseSatchelProgress(parsedLine.value());
            boolean satchelFull = isSatchelFull(parsedLine.value(), progress);
            String tag =
                    satchelFull
                            ? "[" + compactSatchelTag(parsedLine.label()) + "!]"
                            : "[" + compactSatchelTag(parsedLine.label()) + "]";
            String valueText = satchelFull ? "FULL!" : compactSatchelValue(parsedLine.value());
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = Math.max(contentLeft + 44, contentRight - valueWidth);
            int tagMaxWidth = Math.max(12, valueX - contentLeft - 3);
            String clampedTag = textRenderer.trimToWidth(tag, tagMaxWidth);

            if (satchelFull) {
                drawContext.fill(
                        contentLeft,
                        rowY - 1,
                        contentRight,
                        rowY + panel.lineHeight() - 1,
                        withAlpha(0x8F1A1A, satchelFullPulseAlpha()));
            }

            int tagColor = satchelFull ? 0xFFFFC1C1 : 0xFFAAF3CC;
            int valueColor = satchelFull ? 0xFFFF5D5D : 0xFF9EF0BC;
            drawContext.drawTextWithShadow(textRenderer, clampedTag, contentLeft, rowY, tagColor);
            drawContext.drawTextWithShadow(textRenderer, valueText, valueX, rowY, valueColor);
            rowY += panel.lineHeight();
        }
    }

    private static void drawGangRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        if (panel.lines().isEmpty()) {
            return;
        }

        int totalRows = Math.max(1, panel.lines().size() - 1);
        int lineHeight = panel.lineHeight();
        GangLayout gangLayout = buildGangLayout(panel.lines());
        int metadataRows = Math.min(totalRows, gangLayout.metadataLines().size());
        int primaryRowsAvailable = Math.max(0, totalRows - metadataRows);

        List<GangPrimaryLine> primaryLines = gangLayout.primaryLines();
        int visiblePrimaryCount = Math.min(primaryRowsAvailable, primaryLines.size());
        boolean needsOverflow =
                primaryLines.size() > primaryRowsAvailable && primaryRowsAvailable > 0;
        if (needsOverflow) {
            visiblePrimaryCount = Math.max(0, visiblePrimaryCount - 1);
        }

        for (int index = 0; index < visiblePrimaryCount; index++) {
            GangPrimaryLine line = primaryLines.get(index);
            int rowY = contentTop + (index * lineHeight);
            renderGangPrimaryLine(
                    drawContext, textRenderer, line, rowY, lineHeight, contentLeft, contentRight);
        }

        if (needsOverflow) {
            int hidden = Math.max(1, primaryLines.size() - primaryRowsAvailable + 1);
            int rowY = contentTop + (visiblePrimaryCount * lineHeight);
            String overflowText =
                    textRenderer.trimToWidth(
                            "... +" + hidden + " more",
                            Math.max(8, contentRight - contentLeft - 6));
            drawContext.drawTextWithShadow(
                    textRenderer, overflowText, contentLeft + 4, rowY, 0xFF9CB0C8);
        }

        int metadataStartRow = primaryRowsAvailable;
        if (metadataRows > 0) {
            int metadataTopY = contentTop + (metadataStartRow * lineHeight);
            drawContext.fill(
                    contentLeft,
                    metadataTopY - 1,
                    contentRight,
                    metadataTopY,
                    withAlpha(0x31425A, 194));
        }

        for (int index = 0; index < metadataRows; index++) {
            String metadataText = gangLayout.metadataLines().get(index);
            int rowY = contentTop + ((metadataStartRow + index) * lineHeight);
            renderGangMetadataLine(
                    drawContext,
                    textRenderer,
                    metadataText,
                    rowY,
                    lineHeight,
                    contentLeft,
                    contentRight);
        }
    }

    private static void drawLeaderboardRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        boolean allowBillionSuffix = !isBlocksLeaderboardPanel(panel);
        double topValue = 0.0D;
        for (int index = 0; index < panel.lines().size(); index++) {
            String line = panel.lines().get(index);
            if (line == null || line.isBlank()) {
                continue;
            }
            LeaderboardEntry entry = parseLeaderboardEntry(line.trim());
            if (entry == null) {
                continue;
            }
            OptionalDouble parsedValue = parseLeaderboardValue(entry.value(), allowBillionSuffix);
            if (parsedValue.isPresent()) {
                topValue = Math.max(topValue, parsedValue.orElse(0.0D));
            }
        }

        int rowY = contentTop;
        for (int index = 0; index < panel.lines().size(); index++) {
            String line = panel.lines().get(index);
            if (line == null || line.isBlank()) {
                rowY += panel.lineHeight();
                continue;
            }

            String trimmed = line.trim();
            LeaderboardEntry entry = parseLeaderboardEntry(trimmed);
            int lineColor = entry == null ? 0xFFD6E2F3 : leaderboardRankColor(entry.rank());
            String displayLine =
                    textRenderer.trimToWidth(trimmed, Math.max(8, contentRight - contentLeft - 1));
            int textY = rowY + 2;
            if (entry != null) {
                boolean selfEntry = isLocalPlayerEntry(entry.name());
                if (selfEntry) {
                    drawContext.fill(
                            contentLeft,
                            rowY + 1,
                            contentRight,
                            rowY + panel.lineHeight() - 1,
                            withAlpha(0x1E5AA8, 120));
                }
                int valueSlotWidth = Math.max(34, Math.min(58, contentRight - contentLeft - 68));
                int rankBadgeWidth = 18;
                int rankX = contentLeft;
                int valueWidth = textRenderer.getWidth(entry.value());
                int valueAreaLeft =
                        Math.max(contentLeft + rankBadgeWidth + 14, contentRight - valueSlotWidth);
                int valueX = valueAreaLeft + Math.max(0, valueSlotWidth - valueWidth);
                int nameX = contentLeft + rankBadgeWidth;
                int nameMaxWidth = Math.max(10, valueX - nameX - 3);
                String rankText = "#" + entry.rank();
                String nameText = textRenderer.trimToWidth(entry.name(), nameMaxWidth);
                drawContext.fill(
                        rankX,
                        rowY + 2,
                        rankX + rankBadgeWidth - 3,
                        rowY + panel.lineHeight() - 5,
                        withAlpha(lineColor, 38));
                drawContext.drawTextWithShadow(textRenderer, rankText, rankX + 1, textY, lineColor);
                drawContext.drawTextWithShadow(
                        textRenderer, nameText, nameX, textY, selfEntry ? 0xFFFFFFFF : 0xFFE5EDF9);
                drawContext.drawTextWithShadow(
                        textRenderer,
                        entry.value(),
                        valueX,
                        textY,
                        selfEntry ? 0xFFE9F2FF : 0xFFD2E6FF);

                int barLeft = nameX;
                int barRight = Math.max(barLeft + 8, valueAreaLeft - 3);
                int barY = rowY + panel.lineHeight() - 3;
                drawContext.fill(barLeft, barY, barRight, barY + 2, withAlpha(0x2E3F57, 188));
                if (topValue > 0.0D) {
                    OptionalDouble parsedValue =
                            parseLeaderboardValue(entry.value(), allowBillionSuffix);
                    if (parsedValue.isPresent()) {
                        double ratio = parsedValue.orElse(0.0D) / topValue;
                        float clampedRatio = (float) Math.max(0.0D, Math.min(1.0D, ratio));
                        int filledWidth =
                                Math.max(
                                        clampedRatio > 0.0F ? 1 : 0,
                                        Math.round((barRight - barLeft) * clampedRatio));
                        if (filledWidth > 0) {
                            drawContext.fill(
                                    barLeft,
                                    barY,
                                    barLeft + filledWidth,
                                    barY + 2,
                                    withAlpha(lineColor, 228));
                        }
                    }
                }
            } else {
                drawContext.drawTextWithShadow(
                        textRenderer, displayLine, contentLeft, textY, lineColor);
            }
            rowY += panel.lineHeight();
        }
    }

    private static void drawLeaderboardRowsCompact(
            DrawContext drawContext,
            TextRenderer textRenderer,
            HudWidgetPanel panel,
            int contentLeft,
            int contentTop,
            int contentRight) {
        int rowY = contentTop;
        for (int index = 0; index < panel.lines().size(); index++) {
            String line = panel.lines().get(index);
            if (line == null || line.isBlank()) {
                rowY += panel.lineHeight();
                continue;
            }

            String trimmed = line.trim();
            LeaderboardEntry entry = parseLeaderboardEntry(trimmed);
            if (entry == null) {
                drawContext.drawTextWithShadow(
                        textRenderer,
                        textRenderer.trimToWidth(
                                trimmed, Math.max(8, contentRight - contentLeft - 1)),
                        contentLeft,
                        rowY,
                        0xFFD6E2F3);
                rowY += panel.lineHeight();
                continue;
            }

            int rankColor = leaderboardRankColor(entry.rank());
            String rankText = "#" + entry.rank();
            String tagText = "[" + compactLeaderboardTag(entry.name()) + "]";
            String valueText = entry.value();
            int valueWidth = textRenderer.getWidth(valueText);
            int valueX = Math.max(contentLeft + 45, contentRight - valueWidth);
            int rankX = contentLeft;
            int tagX = rankX + Math.max(13, textRenderer.getWidth(rankText) + 3);
            int tagMaxWidth = Math.max(8, valueX - tagX - 2);
            String clampedTag = textRenderer.trimToWidth(tagText, tagMaxWidth);
            boolean selfEntry = isLocalPlayerEntry(entry.name());

            if (selfEntry) {
                drawContext.fill(
                        contentLeft,
                        rowY - 1,
                        contentRight,
                        rowY + panel.lineHeight() - 1,
                        withAlpha(0x1E5AA8, 120));
            }

            drawContext.drawTextWithShadow(textRenderer, rankText, rankX, rowY, rankColor);
            drawContext.drawTextWithShadow(
                    textRenderer, clampedTag, tagX, rowY, selfEntry ? 0xFFFFFFFF : 0xFFCEE1FF);
            drawContext.drawTextWithShadow(
                    textRenderer, valueText, valueX, rowY, selfEntry ? 0xFFE9F2FF : 0xFFD2E6FF);
            rowY += panel.lineHeight();
        }
    }

    private static int widgetLineHeight(String widgetId, boolean compactEvents) {
        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            if (compactEvents) {
                return 10;
            }
            return 16;
        }

        if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
            return 22;
        }

        if (CompanionConfig.HUD_WIDGET_PETS_ID.equals(widgetId)) {
            return 24;
        }

        if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId)) {
            if (compactEvents) {
                return 10;
            }
            return 16;
        }

        if (CompanionConfig.HUD_WIDGET_GANG_ID.equals(widgetId)) {
            return 13;
        }

        if (CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(widgetId)
                || HudWidgetCatalog.isLeaderboardWidget(widgetId)) {
            if (compactEvents) {
                return 10;
            }
            return 16;
        }

        return 11;
    }

    private static int basePanelWidth(String widgetId, boolean compactEvents) {
        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            if (compactEvents) {
                return 104;
            }
            return 156;
        }

        if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
            return 148;
        }

        if (CompanionConfig.HUD_WIDGET_PETS_ID.equals(widgetId)) {
            return 170;
        }

        if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId)) {
            if (compactEvents) {
                return 94;
            }
            return 142;
        }

        if (CompanionConfig.HUD_WIDGET_GANG_ID.equals(widgetId)) {
            return 156;
        }

        if (CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(widgetId)
                || HudWidgetCatalog.isLeaderboardWidget(widgetId)) {
            if (compactEvents) {
                return 108;
            }
            return 152;
        }

        return 126;
    }

    private static int minPanelWidth(String widgetId, boolean compactEvents) {
        if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
            if (compactEvents) {
                return 68;
            }
            return 88;
        }
        if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId) && compactEvents) {
            return 62;
        }
        if (CompanionConfig.HUD_WIDGET_PETS_ID.equals(widgetId)) {
            return 140;
        }
        if (CompanionConfig.HUD_WIDGET_GANG_ID.equals(widgetId)) {
            return 112;
        }
        if ((CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(widgetId)
                        || HudWidgetCatalog.isLeaderboardWidget(widgetId))
                && compactEvents) {
            return 76;
        }
        return HUD_PANEL_MIN_WIDTH;
    }

    private static int measureWidgetLineWidth(
            String widgetId, List<String> lines, TextRenderer textRenderer, boolean compactEvents) {
        int max = 0;
        for (String line : lines) {
            HudWidgetCatalog.ParsedLine parsedLine = HudWidgetCatalog.splitLine(line);
            if (CompanionConfig.HUD_WIDGET_EVENTS_ID.equals(widgetId)) {
                int width;
                if (compactEvents) {
                    String tag =
                            HudWidgetCatalog.findEventByLabel(parsedLine.label())
                                    .map(HudWidgetCatalog.EventDescriptor::iconTag)
                                    .orElse(compactEventTag(parsedLine.label()));
                    width =
                            textRenderer.getWidth("[" + tag + "]")
                                    + textRenderer.getWidth(
                                            compactStatusText(parsedLine.value(), false))
                                    + 18;
                } else {
                    width =
                            textRenderer.getWidth(compactEventLabel(parsedLine.label()))
                                    + textRenderer.getWidth(
                                            compactStatusText(parsedLine.value(), false))
                                    + 36;
                }
                max = Math.max(max, width);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_COOLDOWNS_ID.equals(widgetId)) {
                int labelWidth = textRenderer.getWidth(compactCooldownLabel(parsedLine.label()));
                int valueWidth = textRenderer.getWidth(compactStatusText(parsedLine.value(), true));
                int width = Math.max(labelWidth, valueWidth) + 30;
                max = Math.max(max, width);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_PETS_ID.equals(widgetId)) {
                PetWidgetRow petRow = parsePetWidgetRow(line);
                if (petRow == null) {
                    max = Math.max(max, textRenderer.getWidth(line) + 18);
                    continue;
                }

                if (petRow.overflow()) {
                    max = Math.max(max, textRenderer.getWidth(petRow.displayName()) + 18);
                    continue;
                }

                int primaryWidth = textRenderer.getWidth(petPrimaryLabel(petRow));
                int detailWidth =
                        Math.max(
                                textRenderer.getWidth(petLevelText(petRow)),
                                textRenderer.getWidth(petLevelProgressText(petRow)));
                int statusWidth = textRenderer.getWidth(petStatusText(petRow));
                int width = Math.max(primaryWidth, statusWidth) + detailWidth + 34;
                max = Math.max(max, width);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_SATCHELS_ID.equals(widgetId)) {
                int width;
                if (compactEvents) {
                    width =
                            textRenderer.getWidth("[" + compactSatchelTag(parsedLine.label()) + "]")
                                    + textRenderer.getWidth(compactSatchelValue(parsedLine.value()))
                                    + 14;
                } else {
                    width =
                            textRenderer.getWidth(parsedLine.label())
                                    + textRenderer.getWidth(compactSatchelValue(parsedLine.value()))
                                    + 36;
                }
                max = Math.max(max, width);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_GANG_ID.equals(widgetId)) {
                int lineWidth = Math.min(128, textRenderer.getWidth(line));
                max = Math.max(max, lineWidth + 16);
                continue;
            }

            if (CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(widgetId)
                    || HudWidgetCatalog.isLeaderboardWidget(widgetId)) {
                int width;
                if (compactEvents) {
                    LeaderboardEntry entry = parseLeaderboardEntry(line);
                    if (entry == null) {
                        width = textRenderer.getWidth(line);
                    } else {
                        width =
                                textRenderer.getWidth("#" + entry.rank())
                                        + textRenderer.getWidth(
                                                "[" + compactLeaderboardTag(entry.name()) + "]")
                                        + textRenderer.getWidth(entry.value())
                                        + 18;
                    }
                } else {
                    LeaderboardEntry entry = parseLeaderboardEntry(line);
                    if (entry == null) {
                        width = textRenderer.getWidth(line) + 18;
                    } else {
                        width =
                                textRenderer.getWidth("#" + entry.rank())
                                        + textRenderer.getWidth(entry.name())
                                        + textRenderer.getWidth(entry.value())
                                        + 24;
                    }
                }
                max = Math.max(max, width);
                continue;
            }

            max = Math.max(max, textRenderer.getWidth(line));
        }

        return max + (HUD_PANEL_HORIZONTAL_PADDING * 2);
    }

    private static int statusColor(String statusText, OptionalLong remaining) {
        String normalized = normalizeStatusToken(primaryStatusSegment(statusText));

        if ("now".equals(normalized) || "live".equals(normalized) || "active".equals(normalized)) {
            return 0xFF7CF2A0;
        }

        if (isUnavailableStatus(normalized)) {
            return 0xFF8A95AB;
        }

        if (remaining.isPresent() && remaining.orElse(0L) > 0L) {
            long seconds = remaining.orElse(0L);
            if (seconds <= 60L) {
                return 0xFFFF7F80;
            }
            if (seconds <= 300L) {
                return 0xFFFFB473;
            }
            if (seconds <= 1800L) {
                return 0xFFF2D67A;
            }
            return 0xFF7CC6FF;
        }

        return 0xFFD8E3F4;
    }

    private float eventProgressForLine(
            String progressKey, String rawValue, OptionalLong remaining) {
        if (progressKey == null || progressKey.isBlank()) {
            return 0.0F;
        }

        OptionalDouble explicitProgress = parseExplicitProgressHint(rawValue, remaining);
        if (explicitProgress.isPresent()) {
            eventProgressBaselineSeconds.remove(progressKey);
            return clamp01((float) explicitProgress.orElse(0.0D));
        }

        if (remaining.isEmpty()) {
            eventProgressBaselineSeconds.remove(progressKey);
            return 0.0F;
        }

        long remainingSeconds = Math.max(0L, remaining.orElse(0L));
        if (remainingSeconds <= 0L) {
            eventProgressBaselineSeconds.remove(progressKey);
            return 1.0F;
        }

        long baseline = eventProgressBaselineSeconds.getOrDefault(progressKey, remainingSeconds);
        if (remainingSeconds > baseline) {
            baseline = remainingSeconds;
        }
        eventProgressBaselineSeconds.put(progressKey, baseline);

        if (baseline <= 0L) {
            return 0.0F;
        }

        return clamp01(1.0F - (remainingSeconds / (float) baseline));
    }

    private float cooldownProgressForLine(
            String progressKey, String rawValue, OptionalLong remaining) {
        if (progressKey == null || progressKey.isBlank()) {
            return 0.0F;
        }

        OptionalDouble explicitProgress = parseExplicitProgressHint(rawValue, remaining);
        if (explicitProgress.isPresent()) {
            cooldownProgressBaselineSeconds.remove(progressKey);
            return clamp01((float) explicitProgress.orElse(0.0D));
        }

        if (remaining.isEmpty()) {
            cooldownProgressBaselineSeconds.remove(progressKey);
            return 0.0F;
        }

        long remainingSeconds = Math.max(0L, remaining.orElse(0L));
        if (remainingSeconds <= 0L) {
            cooldownProgressBaselineSeconds.remove(progressKey);
            return 1.0F;
        }

        long baseline = cooldownProgressBaselineSeconds.getOrDefault(progressKey, remainingSeconds);
        if (remainingSeconds > baseline) {
            baseline = remainingSeconds;
        }
        cooldownProgressBaselineSeconds.put(progressKey, baseline);

        if (baseline <= 0L) {
            return 0.0F;
        }

        return clamp01(1.0F - (remainingSeconds / (float) baseline));
    }

    private static OptionalDouble parseExplicitProgressHint(
            String rawValue, OptionalLong remaining) {
        if (rawValue == null || rawValue.isBlank()) {
            return OptionalDouble.empty();
        }

        Matcher ratioMatcher = PROGRESS_RATIO_HINT_PATTERN.matcher(rawValue);
        if (ratioMatcher.find()) {
            try {
                double numerator = Double.parseDouble(ratioMatcher.group(1));
                double denominator = Double.parseDouble(ratioMatcher.group(2));
                if (denominator > 0.0D) {
                    return OptionalDouble.of(clamp01((float) (numerator / denominator)));
                }
            } catch (NumberFormatException ignored) {
                // fall through to other hint types
            }
        }

        Matcher percentMatcher = PROGRESS_PERCENT_HINT_PATTERN.matcher(rawValue);
        if (percentMatcher.find()) {
            try {
                double rawPercent = Double.parseDouble(percentMatcher.group(1));
                String matchedToken = percentMatcher.group(0);
                boolean markedAsPercent = matchedToken != null && matchedToken.contains("%");
                double normalized =
                        (markedAsPercent || rawPercent > 1.0D) ? (rawPercent / 100.0D) : rawPercent;
                return OptionalDouble.of(clamp01((float) normalized));
            } catch (NumberFormatException ignored) {
                // fall through to duration hints
            }
        }

        OptionalLong totalSeconds =
                parseProgressDurationHint(rawValue, PROGRESS_TOTAL_HINT_PATTERN);
        if (totalSeconds.isPresent() && totalSeconds.orElse(0L) > 0L) {
            OptionalLong elapsedSeconds =
                    parseProgressDurationHint(rawValue, PROGRESS_ELAPSED_HINT_PATTERN);
            if (elapsedSeconds.isPresent()) {
                return OptionalDouble.of(
                        clamp01(
                                (float)
                                        (elapsedSeconds.orElse(0L)
                                                / (double) totalSeconds.orElse(1L))));
            }
            if (remaining.isPresent()) {
                double ratio = 1.0D - (remaining.orElse(0L) / (double) totalSeconds.orElse(1L));
                return OptionalDouble.of(clamp01((float) ratio));
            }
        }

        return OptionalDouble.empty();
    }

    private static OptionalLong parseProgressDurationHint(String rawValue, Pattern hintPattern) {
        Matcher matcher = hintPattern.matcher(rawValue);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }
        return HudWidgetCatalog.parseDurationSeconds(matcher.group(1));
    }

    private static String normalizeEventProgressKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeCooldownProgressKey(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        return compactCooldownLabel(label).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int eventAccentColor(String eventKey, int defaultAccent) {
        return switch (eventKey) {
            case CompanionConfig.HUD_EVENT_METEORITE -> 0xFFF4AB56;
            case CompanionConfig.HUD_EVENT_METEOR -> 0xFFFF866A;
            case CompanionConfig.HUD_EVENT_ALTAR_SPAWN -> 0xFFB689FF;
            case CompanionConfig.HUD_EVENT_KOTH -> 0xFF7D99FF;
            case CompanionConfig.HUD_EVENT_CREDIT_SHOP_RESET -> 0xFF5FD3CC;
            case CompanionConfig.HUD_EVENT_JACKPOT -> 0xFFFFD56A;
            case CompanionConfig.HUD_EVENT_FLASH_SALE -> 0xFFF48AB1;
            case CompanionConfig.HUD_EVENT_MERCHANT -> 0xFF86D87A;
            case CompanionConfig.HUD_EVENT_NEXT_REBOOT -> 0xFF62B5FF;
            case CompanionConfig.HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK -> 0xFFEFA86A;
            default -> defaultAccent;
        };
    }

    private static Item eventIcon(String eventKey) {
        return switch (eventKey) {
            case CompanionConfig.HUD_EVENT_METEORITE -> Items.MAGMA_BLOCK;
            case CompanionConfig.HUD_EVENT_METEOR -> Items.FIRE_CHARGE;
            case CompanionConfig.HUD_EVENT_ALTAR_SPAWN -> Items.ENCHANTING_TABLE;
            case CompanionConfig.HUD_EVENT_KOTH -> Items.NETHER_STAR;
            case CompanionConfig.HUD_EVENT_CREDIT_SHOP_RESET -> Items.EMERALD;
            case CompanionConfig.HUD_EVENT_JACKPOT -> Items.GOLD_INGOT;
            case CompanionConfig.HUD_EVENT_FLASH_SALE -> Items.PAPER;
            case CompanionConfig.HUD_EVENT_MERCHANT -> Items.VILLAGER_SPAWN_EGG;
            case CompanionConfig.HUD_EVENT_NEXT_REBOOT -> Items.REDSTONE;
            case CompanionConfig.HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK -> Items.EXPERIENCE_BOTTLE;
            default -> Items.CLOCK;
        };
    }

    private static Item cooldownIcon(String label) {
        String normalized = normalizeStatusToken(label);
        if (normalized.contains(" pet")) {
            return Items.BONE;
        }
        if (normalized.contains("ping")) {
            return Items.BEACON;
        }
        if (normalized.contains("rank kit") || normalized.contains("kit ")) {
            return Items.CHEST;
        }
        if (normalized.contains("gkit") || normalized.contains("god kit")) {
            return Items.NETHERITE_CHESTPLATE;
        }
        if (normalized.contains("gang")) {
            return Items.IRON_SWORD;
        }
        if (normalized.contains("truce")) {
            return Items.SHIELD;
        }
        if (normalized.contains("warp")
                || normalized.contains("home")
                || normalized.contains("tp")) {
            return Items.COMPASS;
        }
        if (normalized.contains("pv") || normalized.contains("vault")) {
            return Items.ENDER_CHEST;
        }
        if (normalized.contains("shop")) {
            return Items.EMERALD;
        }
        if (normalized.contains("crystal")) {
            return Items.AMETHYST_SHARD;
        }
        return Items.CLOCK;
    }

    private static Item petIcon(String petKey, String displayName) {
        String normalized =
                normalizeStatusToken(petKey == null || petKey.isBlank() ? displayName : petKey);
        return switch (normalized) {
            case "bandit_king", "bandit king", "bandit_king pet", "bandit king pet" ->
                    Items.IRON_SWORD;
            case "signal_jammer", "signal jammer", "signal_jammer pet", "signal jammer pet" ->
                    Items.REDSTONE;
            case "cleanse", "cleanse pet" -> Items.MILK_BUCKET;
            case "shockwave", "shockwave pet" -> Items.WIND_CHARGE;
            case "blacksmith", "blacksmith pet" -> Items.ANVIL;
            case "lucky", "lucky pet" -> Items.GOLD_INGOT;
            case "overflow" -> Items.PAPER;
            default -> Items.BONE;
        };
    }

    private ItemStack resolvePetHudHeadStack(String petKey, String displayName) {
        String normalized = normalizePetTextureKey(petKey, displayName);
        ItemStack cached = petHeadIconCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        String textureValue = PET_HEAD_TEXTURES.get(normalized);
        ItemStack resolved =
                textureValue == null
                        ? new ItemStack(petIcon(petKey, displayName))
                        : buildPetHeadStack(
                                normalized,
                                displayName == null || displayName.isBlank()
                                        ? humanizePetKey(normalized)
                                        : displayName,
                                textureValue);
        petHeadIconCache.put(normalized, resolved);
        return resolved;
    }

    private static String normalizePetTextureKey(String petKey, String displayName) {
        String base = petKey;
        if (base == null || base.isBlank()) {
            base = displayName;
        }
        if (base == null || base.isBlank()) {
            return "";
        }

        String normalized = base.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith(" pet")) {
            normalized = normalized.substring(0, normalized.length() - 4).trim();
        }
        return normalized.replace(' ', '_');
    }

    private static ItemStack buildPetHeadStack(
            String petKey, String displayName, String textureValue) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        var properties = LinkedHashMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", textureValue));
        GameProfile profile =
                new GameProfile(
                        UUID.nameUUIDFromBytes(
                                ("cosmicprisons.pethud." + petKey)
                                        .getBytes(StandardCharsets.UTF_8)),
                        displayName,
                        new PropertyMap(properties));
        stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(profile));
        return stack;
    }

    private static Item satchelIcon(String label) {
        String normalized = normalizeStatusToken(label);
        if (normalized.contains("coal")) {
            return Items.COAL;
        }
        if (normalized.contains("lapis")) {
            return Items.LAPIS_LAZULI;
        }
        if (normalized.contains("redstone")) {
            return Items.REDSTONE;
        }
        if (normalized.contains("iron")) {
            return Items.RAW_IRON;
        }
        if (normalized.contains("gold")) {
            return Items.RAW_GOLD;
        }
        if (normalized.contains("diamond")) {
            return Items.DIAMOND;
        }
        if (normalized.contains("emerald")) {
            return Items.EMERALD;
        }
        if (normalized.contains("quartz")) {
            return Items.QUARTZ;
        }
        if (normalized.contains("obsidian")) {
            return Items.OBSIDIAN;
        }
        if (normalized.contains("amethyst")) {
            return Items.AMETHYST_SHARD;
        }
        return Items.BUNDLE;
    }

    private static int centeredItemTop(int rowY, int rowHeight) {
        return rowY + Math.max(0, (rowHeight - 16) / 2) + 1;
    }

    private static PetWidgetRow parsePetWidgetRow(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return null;
        }

        String[] parts = rawLine.split("\\|", -1);
        if (parts.length < 7) {
            return new PetWidgetRow("", rawLine.trim(), 0, 0, 0, 0L, 0L, true);
        }

        String petKey = parts[0].trim();
        if ("overflow".equalsIgnoreCase(petKey)) {
            String displayName = parts[1] == null ? "" : parts[1].trim();
            return new PetWidgetRow("overflow", displayName, 0, 0, 0, 0L, 0L, true);
        }

        String displayName = parts[1] == null ? "" : parts[1].trim();
        if (displayName.isBlank()) {
            displayName = humanizePetKey(petKey);
        }

        return new PetWidgetRow(
                petKey,
                displayName,
                parsePositiveInt(parts[2], 1),
                parsePositiveInt(parts[3], 1),
                parsePositiveInt(parts[4], 0),
                parseNonNegativeLong(parts[5]),
                parseNonNegativeLong(parts[6]),
                false);
    }

    private static String petPrimaryLabel(PetWidgetRow petRow) {
        String label = petRow.displayName();
        if (petRow.count() > 1) {
            label += " x" + petRow.count();
        }
        return label;
    }

    private static String petLevelText(PetWidgetRow petRow) {
        return "Lv " + Math.max(1, petRow.level());
    }

    private static String petLevelProgressText(PetWidgetRow petRow) {
        if (petRow.levelProgressPercent() >= 100) {
            return "MAX";
        }
        return Math.max(0, Math.min(100, petRow.levelProgressPercent())) + "%";
    }

    private static int petLevelProgressColor(PetWidgetRow petRow) {
        if (petRow.levelProgressPercent() >= 100) {
            return 0xFFBFE7FF;
        }
        return 0xFFF1D37A;
    }

    private static String petStatusText(PetWidgetRow petRow) {
        if (petRow.activeRemainingSeconds() > 0L) {
            return "UP " + formatWidgetDurationSeconds(petRow.activeRemainingSeconds());
        }
        if (petRow.cooldownRemainingSeconds() > 0L) {
            return "CD " + formatWidgetDurationSeconds(petRow.cooldownRemainingSeconds());
        }
        return "READY";
    }

    private static int petStatusColor(PetWidgetRow petRow) {
        if (petRow.activeRemainingSeconds() > 0L) {
            return 0xFF89F1B3;
        }
        if (petRow.cooldownRemainingSeconds() > 0L) {
            return 0xFFFFC17B;
        }
        return 0xFF7CF2A0;
    }

    private static String normalizePetProgressKey(PetWidgetRow petRow) {
        if (petRow == null || petRow.overflow()) {
            return "";
        }
        return (petRow.petKey() + ":" + petRow.level() + ":" + petRow.levelProgressPercent())
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private float petProgressForRow(String progressKey, long remainingSeconds) {
        if (progressKey == null || progressKey.isBlank()) {
            return 0.0F;
        }

        if (remainingSeconds <= 0L) {
            petProgressBaselineSeconds.remove(progressKey);
            return 1.0F;
        }

        long baseline = petProgressBaselineSeconds.getOrDefault(progressKey, remainingSeconds);
        if (remainingSeconds > baseline) {
            baseline = remainingSeconds;
        }
        petProgressBaselineSeconds.put(progressKey, baseline);

        if (baseline <= 0L) {
            return 0.0F;
        }

        return clamp01(1.0F - (remainingSeconds / (float) baseline));
    }

    private static String formatWidgetDurationSeconds(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long days = safeSeconds / 86_400L;
        long hours = (safeSeconds % 86_400L) / 3_600L;
        long minutes = (safeSeconds % 3_600L) / 60L;
        long seconds = safeSeconds % 60L;

        if (days > 0L) {
            return hours > 0L ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0L) {
            return minutes > 0L ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0L) {
            return seconds > 0L ? minutes + "m " + seconds + "s" : minutes + "m";
        }
        return seconds + "s";
    }

    private static int parsePositiveInt(String rawValue, int fallback) {
        try {
            return Math.max(fallback, Integer.parseInt(rawValue == null ? "" : rawValue.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseNonNegativeLong(String rawValue) {
        try {
            return Math.max(0L, Long.parseLong(rawValue == null ? "" : rawValue.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String humanizePetKey(String petKey) {
        if (petKey == null || petKey.isBlank()) {
            return "Pet";
        }

        String[] parts = petKey.trim().split("[_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }

        if (builder.length() == 0) {
            return "Pet";
        }
        if (!builder.toString().endsWith(" Pet")) {
            builder.append(" Pet");
        }
        return builder.toString();
    }

    private static GangLayout buildGangLayout(List<String> lines) {
        List<GangPrimaryLine> primary = new ArrayList<>();
        List<String> metadata = new ArrayList<>();
        List<String> cleaned = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            cleaned.add(line.trim());
        }

        int onlineIndex = -1;
        int offlineIndex = -1;
        for (int i = 0; i < cleaned.size(); i++) {
            String normalized = normalizeStatusToken(cleaned.get(i));
            if (onlineIndex < 0 && normalized.startsWith("online")) {
                onlineIndex = i;
            }
            if (offlineIndex < 0 && normalized.startsWith("offline")) {
                offlineIndex = i;
            }
        }

        if (onlineIndex < 0 && offlineIndex < 0) {
            for (String line : cleaned) {
                appendGangMetadataLines(metadata, line);
            }
            return new GangLayout(primary, metadata);
        }

        for (int i = 0; i < cleaned.size(); i++) {
            String line = cleaned.get(i);
            if (onlineIndex >= 0 && i >= onlineIndex && (offlineIndex < 0 || i < offlineIndex)) {
                boolean header = i == onlineIndex;
                primary.add(new GangPrimaryLine(line, 1, header));
                continue;
            }
            if (offlineIndex >= 0 && i >= offlineIndex) {
                boolean header = i == offlineIndex;
                primary.add(new GangPrimaryLine(line, -1, header));
                continue;
            }
            appendGangMetadataLines(metadata, line);
        }

        return new GangLayout(primary, metadata);
    }

    private static void appendGangMetadataLines(List<String> metadata, String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return;
        }

        String line = rawLine.trim();
        String normalizedLine = normalizeStatusToken(line);

        if (line.contains("|") && normalizedLine.contains("truce")) {
            String membersSegment = "";
            String truceSegment = "";
            String[] segments = line.split("\\|");
            for (String rawSegment : segments) {
                String segment = rawSegment == null ? "" : rawSegment.trim();
                if (segment.isBlank()) {
                    continue;
                }
                String normalizedSegment = normalizeStatusToken(segment);
                if (normalizedSegment.startsWith("member")) {
                    membersSegment = segment;
                } else if (normalizedSegment.startsWith("truce")) {
                    truceSegment = segment;
                }
            }

            String preferredTruce = preferredGangTruce(truceSegment);
            if (!preferredTruce.isBlank()) {
                metadata.add("Truce: " + preferredTruce);
                return;
            }
            if (!membersSegment.isBlank()) {
                metadata.add(membersSegment);
                return;
            }
        }

        if (normalizedLine.startsWith("truce")) {
            String preferredTruce = preferredGangTruce(line);
            if (!preferredTruce.isBlank()) {
                metadata.add("Truce: " + preferredTruce);
            }
            return;
        }

        metadata.add(line);
    }

    private static String preferredGangTruce(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }

        int colonIndex = line.indexOf(':');
        String value = colonIndex >= 0 ? line.substring(colonIndex + 1).trim() : line.trim();
        if (value.isBlank()) {
            return "";
        }

        String normalized = normalizeStatusToken(value);
        if (normalized.isBlank()
                || normalized.equals("none")
                || normalized.equals("n/a")
                || normalized.equals("na")
                || normalized.equals("0")
                || normalized.matches("\\d+")) {
            return "";
        }

        String[] tokens = value.split("[,/|]");
        String first = tokens.length == 0 ? value : tokens[0];
        return first.trim();
    }

    private static void renderGangPrimaryLine(
            DrawContext drawContext,
            TextRenderer textRenderer,
            GangPrimaryLine line,
            int rowY,
            int lineHeight,
            int contentLeft,
            int contentRight) {
        String text = line.text();
        if (text.isBlank()) {
            return;
        }
        int textY = rowY + 2;
        int textX = contentLeft + (line.header() ? 1 : 3);

        if (line.header()) {
            int headerColor = line.section() > 0 ? 0x1F4A33 : 0x2E3848;
            int headerText = line.section() > 0 ? 0xFFA7F1C2 : 0xFFC6D3E7;
            int textMaxWidth = Math.max(8, contentRight - textX - 1);
            String display = textRenderer.trimToWidth(text, textMaxWidth);
            drawContext.fill(
                    contentLeft,
                    rowY + 1,
                    contentRight,
                    rowY + lineHeight - 2,
                    withAlpha(headerColor, 170));
            drawContext.drawTextWithShadow(textRenderer, display, textX, textY, headerText);
            return;
        }

        String mainText = text;
        String suffixText = "";
        Matcher trailingMatcher = GANG_TRAILING_TIME_PATTERN.matcher(mainText);
        if (line.section() > 0 && trailingMatcher.matches()) {
            mainText = trailingMatcher.group(1).trim();
            suffixText = trailingMatcher.group(2).trim();
        }

        int contentMaxRight = contentRight - 1;
        if (!suffixText.isBlank()) {
            String suffixDisplay = textRenderer.trimToWidth("(" + suffixText + ")", 48);
            int suffixWidth = textRenderer.getWidth(suffixDisplay);
            if (suffixWidth + 24 <= (contentRight - textX)) {
                int suffixX = contentRight - suffixWidth;
                drawContext.drawTextWithShadow(
                        textRenderer, suffixDisplay, suffixX, textY, 0xFFA6B8CF);
                contentMaxRight = suffixX - 3;
            }
        }

        int textMaxWidth = Math.max(8, contentMaxRight - textX);
        int textColor =
                line.section() > 0 ? 0xFFDDF8E7 : line.section() < 0 ? 0xFFBCC8D8 : 0xFFE4ECF8;
        Matcher memberMatcher = GANG_MEMBER_LINE_PATTERN.matcher(mainText);
        if (!memberMatcher.matches()) {
            String display = textRenderer.trimToWidth(mainText, textMaxWidth);
            drawContext.drawTextWithShadow(textRenderer, display, textX, textY, textColor);
            return;
        }

        String rankToken = memberMatcher.group(1);
        String gangTagToken = memberMatcher.group(2);
        String nameToken = memberMatcher.group(3) == null ? "" : memberMatcher.group(3).trim();
        int drawX = textX;

        String rankDisplay = textRenderer.trimToWidth(rankToken, Math.max(8, textMaxWidth));
        drawContext.drawTextWithShadow(
                textRenderer,
                rankDisplay,
                drawX,
                textY,
                line.section() > 0 ? 0xFF9DE9C0 : 0xFF9FB5D0);
        drawX += textRenderer.getWidth(rankDisplay) + 2;

        if (gangTagToken != null && !gangTagToken.isBlank() && drawX < contentMaxRight - 6) {
            int tagWidth =
                    Math.min(textRenderer.getWidth(gangTagToken), contentMaxRight - drawX - 4);
            if (tagWidth > 0) {
                String tagDisplay = textRenderer.trimToWidth(gangTagToken, tagWidth);
                drawContext.drawTextWithShadow(textRenderer, tagDisplay, drawX, textY, 0xFF7FCBFF);
                drawX += textRenderer.getWidth(tagDisplay) + 2;
            }
        }

        int nameWidth = Math.max(8, contentMaxRight - drawX);
        String nameDisplay = textRenderer.trimToWidth(nameToken, nameWidth);
        drawContext.drawTextWithShadow(textRenderer, nameDisplay, drawX, textY, textColor);
    }

    private static void renderGangMetadataLine(
            DrawContext drawContext,
            TextRenderer textRenderer,
            String rawText,
            int rowY,
            int rowHeight,
            int contentLeft,
            int contentRight) {
        if (rawText == null || rawText.isBlank()) {
            return;
        }

        if (renderGangPointsBankLine(
                drawContext, textRenderer, rawText, rowY, rowHeight, contentLeft, contentRight)) {
            return;
        }

        int color = 0xFF9FB1C8;
        String normalized = normalizeStatusToken(rawText);
        if (normalized.startsWith("truce")) {
            color = 0xFFB9D9FF;
        } else if (normalized.startsWith("target")) {
            color = 0xFFE3C7B0;
        }

        drawScaledGangMetadataLine(
                drawContext,
                textRenderer,
                rawText,
                rowY,
                rowHeight,
                contentLeft,
                contentRight,
                color);
    }

    private static boolean renderGangPointsBankLine(
            DrawContext drawContext,
            TextRenderer textRenderer,
            String rawText,
            int rowY,
            int rowHeight,
            int contentLeft,
            int contentRight) {
        String lowered = rawText.toLowerCase(java.util.Locale.ROOT);
        int pointsIndex = lowered.indexOf("points:");
        int bankIndex = lowered.indexOf("bank:");
        if (pointsIndex < 0 || bankIndex < 0) {
            return false;
        }

        String pointsValue = "";
        String bankValue = "";
        if (pointsIndex < bankIndex) {
            pointsValue =
                    rawText.substring(pointsIndex + "points:".length(), bankIndex)
                            .replace("|", "")
                            .trim();
            bankValue = rawText.substring(bankIndex + "bank:".length()).replace("|", "").trim();
        } else {
            bankValue =
                    rawText.substring(bankIndex + "bank:".length(), pointsIndex)
                            .replace("|", "")
                            .trim();
            pointsValue =
                    rawText.substring(pointsIndex + "points:".length()).replace("|", "").trim();
        }

        if (pointsValue.isBlank() && bankValue.isBlank()) {
            return false;
        }

        int gap = 4;
        int totalWidth = Math.max(16, contentRight - contentLeft);
        int segmentWidth = Math.max(40, (totalWidth - gap) / 2);
        int leftSegmentX = contentLeft;
        int rightSegmentX = contentRight - segmentWidth;
        int rowTop = rowY + 1;
        int rowBottom = rowY + rowHeight - 1;

        drawContext.fill(
                leftSegmentX,
                rowTop,
                leftSegmentX + segmentWidth,
                rowBottom,
                withAlpha(0x35522F, 150));
        drawContext.fill(
                rightSegmentX,
                rowTop,
                rightSegmentX + segmentWidth,
                rowBottom,
                withAlpha(0x584732, 150));

        drawMiniItem(drawContext, Items.NETHER_STAR, leftSegmentX + 2, rowY + 2);
        drawMiniItem(drawContext, Items.GOLD_INGOT, rightSegmentX + 2, rowY + 2);

        String formattedPoints = formatGangMetricValue(pointsValue, false);
        String formattedBank = formatGangMetricValue(bankValue, true);
        String leftText = textRenderer.trimToWidth(formattedPoints, Math.max(8, segmentWidth - 16));
        String rightText =
                textRenderer.trimToWidth("Bank " + formattedBank, Math.max(8, segmentWidth - 16));
        drawContext.drawTextWithShadow(
                textRenderer, leftText, leftSegmentX + 14, rowY + 2, 0xFFD6F4C8);
        drawContext.drawTextWithShadow(
                textRenderer, rightText, rightSegmentX + 14, rowY + 2, 0xFFF7D8A8);
        return true;
    }

    private static String formatGangMetricValue(String rawValue, boolean preferCurrency) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        String cleaned = rawValue.replace("|", "").trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        boolean hasGpSuffix = normalizeStatusToken(cleaned).endsWith(" gp");
        if (hasGpSuffix && cleaned.length() > 3) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        boolean hasDollarPrefix = cleaned.startsWith("$");
        String numericCandidate = hasDollarPrefix ? cleaned.substring(1).trim() : cleaned;
        OptionalDouble parsedValue = parseLeaderboardValue(numericCandidate, true);
        if (parsedValue.isEmpty()) {
            return rawValue.trim();
        }

        long rounded = Math.max(0L, Math.round(parsedValue.orElse(0.0D)));
        String grouped = String.format(java.util.Locale.US, "%,d", rounded);
        if (preferCurrency || hasDollarPrefix) {
            return "$" + grouped;
        }
        if (hasGpSuffix) {
            return grouped + " GP";
        }
        return grouped;
    }

    private static void drawMiniItem(DrawContext drawContext, Item item, int x, int y) {
        if (item == null) {
            return;
        }
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(x, y);
        drawContext.getMatrices().scale(0.5F, 0.5F);
        drawContext.drawItem(item.getDefaultStack(), 0, 0);
        drawContext.getMatrices().popMatrix();
    }

    private static void drawScaledGangMetadataLine(
            DrawContext drawContext,
            TextRenderer textRenderer,
            String rawText,
            int rowY,
            int rowHeight,
            int contentLeft,
            int contentRight,
            int color) {
        if (rawText == null || rawText.isBlank()) {
            return;
        }

        float scale = 0.82F;
        int availableWidth = Math.max(8, contentRight - contentLeft - 1);
        String display =
                textRenderer.trimToWidth(
                        rawText.trim(), Math.max(8, Math.round(availableWidth / scale)));
        int scaledTextHeight = Math.max(1, Math.round(textRenderer.fontHeight * scale));
        int textY = rowY + Math.max(0, (rowHeight - scaledTextHeight) / 2);

        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(contentLeft, textY);
        drawContext.getMatrices().scale(scale, scale);
        drawContext.drawTextWithShadow(textRenderer, display, 0, 0, color);
        drawContext.getMatrices().popMatrix();
    }

    private static LeaderboardEntry parseLeaderboardEntry(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = LEADERBOARD_ENTRY_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }

        return new LeaderboardEntry(rank, matcher.group(2).trim(), matcher.group(3).trim());
    }

    private static OptionalDouble parseLeaderboardValue(
            String valueText, boolean allowBillionSuffix) {
        if (valueText == null || valueText.isBlank()) {
            return OptionalDouble.empty();
        }

        String normalized = valueText.replace(",", "").trim();
        Matcher matcher = LEADERBOARD_VALUE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return OptionalDouble.empty();
        }

        double numeric;
        try {
            numeric = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }

        double multiplier = 1.0D;
        String suffixText = matcher.group(2);
        if (suffixText != null && !suffixText.isBlank()) {
            char suffix = Character.toLowerCase(suffixText.charAt(0));
            multiplier =
                    switch (suffix) {
                        case 'k' -> 1_000.0D;
                        case 'm' -> 1_000_000.0D;
                        case 'b' -> allowBillionSuffix ? 1_000_000_000.0D : 1.0D;
                        case 't' -> 1_000_000_000_000.0D;
                        default -> 1.0D;
                    };
        }

        double value = Math.max(0.0D, numeric * multiplier);
        return OptionalDouble.of(value);
    }

    private static boolean isBlocksLeaderboardPanel(HudWidgetPanel panel) {
        if (panel == null) {
            return false;
        }
        if (CompanionConfig.HUD_WIDGET_LEADERBOARD_BLOCKS_ID.equals(panel.widgetId())) {
            return true;
        }
        if (!CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID.equals(panel.widgetId())) {
            return false;
        }
        String titleKey = panel.titleTranslationKey();
        return titleKey != null
                && titleKey.contains(CompanionConfig.HUD_WIDGET_LEADERBOARD_BLOCKS_ID);
    }

    private static boolean isLocalPlayerEntry(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        String normalizedEntry = normalizeLeaderboardName(entryName);
        if (normalizedEntry.isEmpty()) {
            return false;
        }

        String normalizedProfileName =
                normalizeLeaderboardName(client.player.getName().getString());
        if (!normalizedProfileName.isEmpty() && normalizedEntry.equals(normalizedProfileName)) {
            return true;
        }

        return false;
    }

    private static String normalizeLeaderboardName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutFormatting = value.replaceAll("(?i)§[0-9A-FK-OR]", "");
        return withoutFormatting.replaceAll("[^A-Za-z0-9_]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static int leaderboardRankColor(int rank) {
        return switch (rank) {
            case 1 -> 0xFFFFD56D;
            case 2 -> 0xFFD8E0F0;
            case 3 -> 0xFFE8AD7D;
            default -> 0xFF8FB4E6;
        };
    }

    private static String compactLeaderboardTag(String name) {
        if (name == null || name.isBlank()) {
            return "P";
        }
        String normalized = name.trim().replaceAll("[^A-Za-z0-9]", "");
        if (normalized.length() >= 3) {
            return normalized.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String compactLeaderboardTitle(String title) {
        String normalized = normalizeStatusToken(title);
        if (normalized.contains("gift")) {
            return "GFT";
        }
        if (normalized.contains("gang")) {
            return "GNG";
        }
        if (normalized.contains("block")) {
            return "BLK";
        }
        if (normalized.contains("level")) {
            return "LVL";
        }
        return "TOP";
    }

    private static String compactEventLabel(String label) {
        String normalized = normalizeStatusToken(label);
        return switch (normalized) {
            case "next level cap day unlock" -> "Lvl Cap Unlock";
            case "credit shop reset" -> "Credit Reset";
            case "altar spawn" -> "Altar";
            case "next reboot" -> "Reboot";
            default -> label;
        };
    }

    private static String compactEventTag(String label) {
        if (label == null || label.isBlank()) {
            return "EVT";
        }
        String normalized = label.trim().replaceAll("[^A-Za-z0-9]", "");
        if (normalized.length() >= 3) {
            return normalized.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String compactSatchelTag(String label) {
        if (label == null || label.isBlank()) {
            return "SAT";
        }
        String normalized = label.trim().replaceAll("[^A-Za-z0-9]", "");
        if (normalized.length() >= 3) {
            return normalized.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String compactCooldownLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Cooldown";
        }
        String trimmed = label.trim();
        String normalized = normalizeStatusToken(trimmed);
        if (normalized.contains("ping")) {
            return "Ping";
        }
        if (normalized.startsWith("rank kit ")) {
            return "Kit " + trimmed.substring("Rank Kit ".length());
        }
        if (normalized.startsWith("cooldown ")) {
            trimmed = trimmed.substring("Cooldown ".length()).trim();
            normalized = normalizeStatusToken(trimmed);
        }
        if (normalized.startsWith("perk ")) {
            while (normalized.startsWith("perk ")) {
                normalized = normalized.substring("perk ".length()).trim();
            }
            if (!normalized.isBlank()) {
                String command = normalized.split("\\s+")[0];
                if (!command.isBlank()) {
                    return "/" + command.toLowerCase(java.util.Locale.ROOT);
                }
            }
        }
        return trimmed;
    }

    private static String compactSatchelValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "--";
        }
        return trimmed;
    }

    private static String compactStatusText(String statusText, boolean cooldownMode) {
        String trimmed = primaryStatusSegment(statusText);
        if (trimmed.isEmpty()) {
            return "--";
        }

        String normalized = normalizeStatusToken(trimmed);
        if (normalized.startsWith("check in ")) {
            return trimmed.substring(9).trim();
        }
        if ("unavailable".equals(normalized)) {
            return cooldownMode ? "--" : "N/A";
        }
        if ("not scheduled".equals(normalized)) {
            return "Not Scheduled";
        }
        if ("max day".equals(normalized)) {
            return "MAX";
        }
        if ("now".equals(normalized)) {
            return "LIVE";
        }
        return trimmed;
    }

    private static float parseSatchelProgress(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return 0.0F;
        }

        String trimmed = valueText.trim();
        int split = trimmed.indexOf(' ');
        String ratio = split > 0 ? trimmed.substring(0, split) : trimmed;
        int slashIndex = ratio.indexOf('/');
        if (slashIndex <= 0 || slashIndex >= ratio.length() - 1) {
            return 0.0F;
        }

        long stored = parseCompactAmount(ratio.substring(0, slashIndex));
        long capacity = parseCompactAmount(ratio.substring(slashIndex + 1));
        if (stored < 0L || capacity <= 0L) {
            return 0.0F;
        }

        return clamp01((float) stored / (float) capacity);
    }

    private static boolean isSatchelFull(String valueText, float progress) {
        if (progress >= 0.999F) {
            return true;
        }

        if (valueText == null || valueText.isBlank()) {
            return false;
        }

        String normalized = normalizeStatusToken(valueText);
        if (normalized.contains("full") || normalized.contains("max")) {
            return true;
        }

        String trimmed = valueText.trim();
        int split = trimmed.indexOf(' ');
        String ratio = split > 0 ? trimmed.substring(0, split) : trimmed;
        int slashIndex = ratio.indexOf('/');
        if (slashIndex <= 0 || slashIndex >= ratio.length() - 1) {
            return false;
        }

        long stored = parseCompactAmount(ratio.substring(0, slashIndex));
        long capacity = parseCompactAmount(ratio.substring(slashIndex + 1));
        return stored >= 0L && capacity > 0L && stored >= capacity;
    }

    private static int satchelFullPulseAlpha() {
        long now = System.currentTimeMillis();
        double phase = (now % 1000L) / 1000.0D;
        double wave = 0.5D + (Math.sin(phase * Math.PI * 2.0D) * 0.5D);
        return 96 + (int) Math.round(wave * 90.0D);
    }

    private static long parseCompactAmount(String value) {
        if (value == null || value.isBlank()) {
            return -1L;
        }

        String trimmed = value.trim().toUpperCase(java.util.Locale.ROOT).replace(",", "");
        if (trimmed.isEmpty()) {
            return -1L;
        }

        long multiplier = 1L;
        char suffix = trimmed.charAt(trimmed.length() - 1);
        if (suffix == 'K' || suffix == 'M' || suffix == 'B' || suffix == 'T') {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
            multiplier =
                    switch (suffix) {
                        case 'K' -> 1_000L;
                        case 'M' -> 1_000_000L;
                        case 'B' -> 1_000_000_000L;
                        case 'T' -> 1_000_000_000_000L;
                        default -> 1L;
                    };
        }

        try {
            double parsed = Double.parseDouble(trimmed);
            if (parsed < 0.0D) {
                return -1L;
            }
            return Math.round(parsed * multiplier);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String normalizeStatusToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String primaryStatusSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int metadataSeparator = value.indexOf('|');
        String primary = metadataSeparator >= 0 ? value.substring(0, metadataSeparator) : value;
        return primary.trim();
    }

    private static boolean isUnavailableStatus(String normalized) {
        return "unavailable".equals(normalized)
                || "not scheduled".equals(normalized)
                || "max day".equals(normalized)
                || "n/a".equals(normalized);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record CoordsPingInfo(Vec3d pos, long expiresAtMillis, Identifier worldId) {
        private boolean isExpired() {
            return expiresAtMillis < System.currentTimeMillis();
        }
    }

    /**
     * Adds or removes the coords ping based on the given pos. If the given position is the same as
     * the existing coordinates ping, and the player is in the same world, then the existing beacon
     * is removed. Otherwise, a new ping is created at the given location. Will also play a sound
     * effect based on whether the beacon is added or removed.
     *
     * @param pos The position of the beacon to be added or removed
     */
    public void addOrRemoveCoordsPing(Vec3d pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        // If this ping is not identical to the existing ping, then set the ping to this location
        if (coordsPing == null
                || !pos.equals(coordsPing.pos.subtract(COORDS_PING_BEAM_OFFSET))
                || !client.world.getRegistryKey().getValue().equals(coordsPing.worldId)
                || coordsPing.isExpired()) {
            setCoordsPing(pos);
        } else {
            // This ping is the same one as the previous one, so instead of placing a new ping,
            // remove the old one
            deleteCoordsPing();
        }
    }

    /**
     * Updates the current coordinates ping to the given location. This ping will be visible to the
     * player for the next {@value
     * me.landon.client.runtime.CompanionClientRuntime#COORDS_PING_LIFETIME_MILLIS} milliseconds.
     * Also plays a sound effect to the player for this action.
     *
     * @param pos the location of the new ping.
     */
    private void setCoordsPing(Vec3d pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(
                () -> {
                    if (client.world == null) return;

                    Vec3d centered = new Vec3d(pos.x, pos.y, pos.z).add(COORDS_PING_BEAM_OFFSET);
                    long expires =
                            safeAddMillis(System.currentTimeMillis(), COORDS_PING_LIFETIME_MILLIS);
                    Identifier worldId = client.world.getRegistryKey().getValue();

                    synchronized (this) {
                        coordsPing = new CoordsPingInfo(centered, expires, worldId);
                    }

                    if (client.player != null) {
                        client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                    }
                });
    }

    /**
     * Deletes any active coords ping by setting coordsPing to null. Also plays a sound effect to
     * the player for this action.
     */
    private void deleteCoordsPing() {
        coordsPing = null;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.playSound(SoundEvents.ENTITY_ENDER_EYE_DEATH, 1f, 1.0f);
        }
    }

    private void processPingKeybinds(MinecraftClient client) {
        if (gangPingKeyBinding == null || trucePingKeyBinding == null) {
            return;
        }

        boolean gangTriggered = false;
        while (gangPingKeyBinding.wasPressed()) {
            gangTriggered = true;
        }
        boolean gangDown = isKeyBindingDown(gangPingKeyBinding);
        if (gangDown && !gangPingKeyWasDown) {
            gangTriggered = true;
        }
        if (gangTriggered) {
            handlePingKeyPress(client, ProtocolConstants.PING_TYPE_GANG);
        }
        gangPingKeyWasDown = gangDown;

        boolean truceTriggered = false;
        while (trucePingKeyBinding.wasPressed()) {
            truceTriggered = true;
        }
        boolean truceDown = isKeyBindingDown(trucePingKeyBinding);
        if (truceDown && !trucePingKeyWasDown) {
            truceTriggered = true;
        }
        if (truceTriggered) {
            handlePingKeyPress(client, ProtocolConstants.PING_TYPE_TRUCE);
        }
        trucePingKeyWasDown = truceDown;
    }

    private void handlePingKeyPress(MinecraftClient client, int pingType) {
        if (!shouldHandlePingKeybinds(client)) {
            return;
        }

        if (canSendPingIntent(client) && sendPingIntent(pingType)) {
            return;
        }

        maybeSendPingUnavailableFeedback(client);
    }

    private boolean sendPingIntent(int pingType) {
        return sendC2S(new ProtocolMessage.PingIntentC2S(pingType));
    }

    private void emitPingBeaconParticles(MinecraftClient client) {
        if (!shouldRenderPingBeacons(client) || client.world == null || !pingParticlesEnabled()) {
            return;
        }

        if ((client.world.getTime() & 1L) != 0L) {
            return;
        }

        RenderTickCounter renderTickCounter = client.getRenderTickCounter();
        float tickProgress =
                renderTickCounter == null ? 0.0F : renderTickCounter.getTickProgress(false);
        boolean staticMode = pingVisualModeStatic();
        long now = System.currentTimeMillis();
        IntSet gangVisualIds =
                resolveActivePingVisualIds(
                        session.gangPingBeaconIdsSnapshot(),
                        gangPingVisualExpiryAtMillis,
                        gangPingVisualSeededIds,
                        now);
        IntSet truceVisualIds =
                resolveActivePingVisualIds(
                        session.trucePingBeaconIdsSnapshot(),
                        trucePingVisualExpiryAtMillis,
                        trucePingVisualSeededIds,
                        now);

        renderPingBeaconParticles(
                client.world,
                gangVisualIds,
                gangPingStaticAnchors,
                tickProgress,
                staticMode,
                ParticleTypes.SOUL_FIRE_FLAME,
                ParticleTypes.END_ROD);
        renderPingBeaconParticles(
                client.world,
                truceVisualIds,
                trucePingStaticAnchors,
                tickProgress,
                staticMode,
                ParticleTypes.FLAME,
                ParticleTypes.SMALL_FLAME);
    }

    private synchronized void onWorldRenderEndMain(WorldRenderContext context) {
        renderPingBeaconBeams(MinecraftClient.getInstance(), context);
    }

    private void renderPingBeaconBeams(MinecraftClient client, WorldRenderContext context) {
        if (client.world == null
                || context == null
                || context.matrices() == null
                || context.commandQueue() == null
                || client.gameRenderer == null
                || client.gameRenderer.getCamera() == null) {
            return;
        }

        RenderTickCounter renderTickCounter = client.getRenderTickCounter();
        float tickProgress =
                renderTickCounter == null ? 0.0F : renderTickCounter.getTickProgress(false);
        float beamRotationDegrees =
                (float) Math.floorMod(client.world.getTime(), 40L) + tickProgress;
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        boolean staticMode = pingVisualModeStatic();
        long now = System.currentTimeMillis();

        renderPingBeaconBeams(
                client, context, cameraPos, beamRotationDegrees, PING_BEAM_COLOR_COORDS);

        if (!shouldRenderPingBeacons(client)) {
            return;
        }

        IntSet gangEntityIds =
                resolveActivePingVisualIds(
                        session.gangPingBeaconIdsSnapshot(),
                        gangPingVisualExpiryAtMillis,
                        gangPingVisualSeededIds,
                        now);
        IntSet truceEntityIds =
                resolveActivePingVisualIds(
                        session.trucePingBeaconIdsSnapshot(),
                        trucePingVisualExpiryAtMillis,
                        trucePingVisualSeededIds,
                        now);

        renderPingBeaconBeams(
                context,
                client.world,
                gangEntityIds,
                gangPingStaticAnchors,
                cameraPos,
                tickProgress,
                beamRotationDegrees,
                staticMode,
                PING_BEAM_COLOR_GANG);
        renderPingBeaconBeams(
                context,
                client.world,
                truceEntityIds,
                trucePingStaticAnchors,
                cameraPos,
                tickProgress,
                beamRotationDegrees,
                staticMode,
                PING_BEAM_COLOR_TRUCE);
    }

    private static void renderPingBeaconBeams(
            WorldRenderContext context,
            ClientWorld world,
            IntSet entityIds,
            Int2ObjectMap<PingVisualAnchorSnapshot> staticAnchors,
            Vec3d cameraPos,
            float tickProgress,
            float beamRotationDegrees,
            boolean staticMode,
            int beamColor) {
        var matrices = context.matrices();
        var commandQueue = context.commandQueue();
        prunePingVisualAnchorSnapshots(staticAnchors, entityIds);

        for (int entityId : entityIds) {
            PingVisualAnchorSnapshot anchorSnapshot =
                    resolvePingVisualAnchorSnapshot(
                            world, entityId, staticAnchors, tickProgress, staticMode);
            if (anchorSnapshot == null) {
                continue;
            }

            Vec3d beamBasePos = anchorSnapshot.beamBasePos();
            matrices.push();
            matrices.translate(
                    beamBasePos.x - cameraPos.x - 0.5D,
                    beamBasePos.y - cameraPos.y,
                    beamBasePos.z - cameraPos.z - 0.5D);
            BeaconBlockEntityRenderer.renderBeam(
                    matrices,
                    commandQueue,
                    BeaconBlockEntityRenderer.BEAM_TEXTURE,
                    1.0F,
                    beamRotationDegrees,
                    0,
                    PING_BEAM_HEIGHT,
                    beamColor,
                    PING_BEAM_INNER_RADIUS,
                    PING_BEAM_OUTER_RADIUS);
            matrices.pop();
        }
    }

    private void renderPingBeaconBeams(
            MinecraftClient client,
            WorldRenderContext context,
            Vec3d cameraPos,
            float beamRotationDegrees,
            int beamColor) {
        CoordsPingInfo ping;
        synchronized (this) {
            ping = this.coordsPing;
        }
        if (ping == null) return;

        if (client == null || client.world == null) return;
        // If this ping has expired, don't render it
        if (ping.isExpired()) return;

        // If this ping is on a different world, don't render it
        Identifier currentWorldId = client.world.getRegistryKey().getValue();
        if (ping.worldId() != null && !ping.worldId().equals(currentWorldId)) return;

        Vec3d pos = ping.pos();
        if (pos == null || !pos.isFinite()) return;

        var matrices = context.matrices();
        var commandQueue = context.commandQueue();

        double beamBaseY = pos.y + 0.1D;

        matrices.push();
        matrices.translate(
                pos.x - cameraPos.x - 0.5D, beamBaseY - cameraPos.y, pos.z - cameraPos.z - 0.5D);

        BeaconBlockEntityRenderer.renderBeam(
                matrices,
                commandQueue,
                BeaconBlockEntityRenderer.BEAM_TEXTURE,
                1.0F,
                beamRotationDegrees,
                0,
                PING_BEAM_HEIGHT,
                beamColor,
                PING_BEAM_INNER_RADIUS,
                PING_BEAM_OUTER_RADIUS);

        matrices.pop();
    }

    private void renderPingBeaconLabels(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (drawContext == null
                || client.world == null
                || client.player == null
                || client.gameRenderer == null
                || client.gameRenderer.getCamera() == null
                || client.textRenderer == null) {
            return;
        }

        // Render the coords ping
        renderCoordsPingBeaconLabel(drawContext, client);

        if (!shouldRenderPingBeacons(client)) {
            return;
        }

        float tickProgress = tickCounter == null ? 0.0F : tickCounter.getTickProgress(false);
        boolean staticMode = pingVisualModeStatic();
        long now = System.currentTimeMillis();
        IntSet gangEntityIds =
                resolveActivePingVisualIds(
                        session.gangPingBeaconIdsSnapshot(),
                        gangPingVisualExpiryAtMillis,
                        gangPingVisualSeededIds,
                        now);
        IntSet truceEntityIds =
                resolveActivePingVisualIds(
                        session.trucePingBeaconIdsSnapshot(),
                        trucePingVisualExpiryAtMillis,
                        trucePingVisualSeededIds,
                        now);
        IntSet renderedEntityIds = new IntOpenHashSet(gangEntityIds.size() + truceEntityIds.size());

        renderPingBeaconLabelsForType(
                drawContext,
                client,
                gangEntityIds,
                gangPingStaticAnchors,
                gangPingLabelSnapshots,
                renderedEntityIds,
                tickProgress,
                staticMode,
                PING_LABEL_TEXT_COLOR_GANG,
                PING_LABEL_DISTANCE_COLOR_GANG,
                PING_LABEL_FRAME_COLOR_GANG,
                PING_LABEL_BACKGROUND_COLOR_GANG);
        renderPingBeaconLabelsForType(
                drawContext,
                client,
                truceEntityIds,
                trucePingStaticAnchors,
                trucePingLabelSnapshots,
                renderedEntityIds,
                tickProgress,
                staticMode,
                PING_LABEL_TEXT_COLOR_TRUCE,
                PING_LABEL_DISTANCE_COLOR_TRUCE,
                PING_LABEL_FRAME_COLOR_TRUCE,
                PING_LABEL_BACKGROUND_COLOR_TRUCE);
    }

    private static void renderPingBeaconLabelsForType(
            DrawContext drawContext,
            MinecraftClient client,
            IntSet entityIds,
            Int2ObjectMap<PingVisualAnchorSnapshot> staticAnchors,
            Int2ObjectMap<PingLabelSnapshot> labelSnapshots,
            IntSet renderedEntityIds,
            float tickProgress,
            boolean staticMode,
            int textColor,
            int distanceColor,
            int frameColor,
            int backgroundColor) {
        ClientWorld world = client.world;
        if (world == null || client.player == null || client.textRenderer == null) {
            return;
        }

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        Vec3d cameraLookDirection =
                Vec3d.fromPolar(
                                client.gameRenderer.getCamera().getPitch(),
                                client.gameRenderer.getCamera().getYaw())
                        .normalize();
        prunePingVisualAnchorSnapshots(staticAnchors, entityIds);
        prunePingLabelSnapshots(labelSnapshots, entityIds);

        for (int entityId : entityIds) {
            if (entityId < 0 || renderedEntityIds.contains(entityId)) {
                continue;
            }

            Entity entity = world.getEntityById(entityId);
            PingLabelSnapshot snapshot;
            PingVisualAnchorSnapshot anchorSnapshot =
                    resolvePingVisualAnchorSnapshot(
                            world, entityId, staticAnchors, tickProgress, staticMode);
            if (entity == null || entity.isRemoved()) {
                snapshot = labelSnapshots.get(entityId);
                if (snapshot == null) {
                    continue;
                }
            } else {
                if (anchorSnapshot == null || anchorSnapshot.labelAnchorPos() == null) {
                    continue;
                }

                snapshot =
                        new PingLabelSnapshot(
                                anchorSnapshot.labelAnchorPos(),
                                entity.getName().getString(),
                                resolveEntityHealth(entity));
                labelSnapshots.put(entityId, snapshot);
            }

            Vec3d labelAnchor = snapshot.anchorPos();
            if (labelAnchor == null || !labelAnchor.isFinite()) {
                continue;
            }
            if (labelAnchor.subtract(cameraPos).dotProduct(cameraLookDirection) <= 0.0D) {
                continue;
            }

            Vec3d projected = client.gameRenderer.project(labelAnchor);
            if (!projected.isFinite()) {
                continue;
            }

            double normalizedX = (projected.x * 0.5D) + 0.5D;
            double normalizedY = 0.5D - (projected.y * 0.5D);
            if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) {
                continue;
            }

            int centerX = (int) Math.round(normalizedX * screenWidth);
            int anchorY = (int) Math.round(normalizedY * screenHeight);
            drawPingBeaconLabel(
                    drawContext,
                    client.textRenderer,
                    centerX,
                    anchorY,
                    snapshot.entityName(),
                    snapshot.healthValue(),
                    Math.sqrt(client.player.squaredDistanceTo(labelAnchor)),
                    textColor,
                    distanceColor,
                    frameColor,
                    backgroundColor);
            renderedEntityIds.add(entityId);
        }
    }

    private void renderCoordsPingBeaconLabel(DrawContext drawContext, MinecraftClient client) {
        if (client == null
                || client.world == null
                || client.player == null
                || client.textRenderer == null
                || client.gameRenderer == null
                || client.gameRenderer.getCamera() == null) {
            return;
        }

        CoordsPingInfo info;
        synchronized (this) {
            info = this.coordsPing;
        }
        if (info == null) return;

        if (info.isExpired()) return;

        Identifier currentWorldId = client.world.getRegistryKey().getValue();
        if (info.worldId() != null && !info.worldId().equals(currentWorldId)) return;

        Vec3d pos = info.pos();
        if (pos == null || !pos.isFinite()) return;

        Vec3d labelAnchor = pos.add(0.0D, PING_LABEL_VERTICAL_OFFSET, 0.0D);

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        Vec3d cameraLookDirection =
                Vec3d.fromPolar(
                                client.gameRenderer.getCamera().getPitch(),
                                client.gameRenderer.getCamera().getYaw())
                        .normalize();

        if (labelAnchor.subtract(cameraPos).dotProduct(cameraLookDirection) <= 0.0D) return;

        Vec3d projected = client.gameRenderer.project(labelAnchor);
        if (!projected.isFinite()) return;

        double normalizedX = (projected.x * 0.5D) + 0.5D;
        double normalizedY = 0.5D - (projected.y * 0.5D);
        if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) return;

        int centerX = (int) Math.round(normalizedX * screenWidth);
        int anchorY = (int) Math.round(normalizedY * screenHeight);

        String name =
                String.format(
                        java.util.Locale.ROOT,
                        "(%d, %d, %d)",
                        (int) Math.floor(pos.x),
                        (int) Math.floor(pos.y),
                        (int) Math.floor(pos.z));

        drawPingBeaconLabel(
                drawContext,
                client.textRenderer,
                centerX,
                anchorY,
                name,
                Float.NaN,
                Math.sqrt(client.player.squaredDistanceTo(labelAnchor)),
                PING_LABEL_TEXT_COLOR_COORDS,
                PING_LABEL_DISTANCE_COLOR_COORDS,
                PING_LABEL_FRAME_COLOR_COORDS,
                PING_LABEL_BACKGROUND_COLOR_COORDS);
    }

    private static void drawPingBeaconLabel(
            DrawContext drawContext,
            TextRenderer textRenderer,
            int centerX,
            int anchorY,
            String entityName,
            float healthValue,
            double distance,
            int textColor,
            int distanceColor,
            int frameColor,
            int backgroundColor) {
        String lineOne =
                Float.isFinite(healthValue)
                        ? entityName + "  " + formatPingHealth(healthValue) + " HP"
                        : entityName;
        String lineTwo = Math.max(0, (int) Math.round(distance)) + " blocks away";
        int lineOneWidth = textRenderer.getWidth(lineOne);
        int lineTwoWidth = textRenderer.getWidth(lineTwo);
        int maxWidth = Math.max(lineOneWidth, lineTwoWidth);
        int boxPadding = 3;
        int boxTop = anchorY - (textRenderer.fontHeight * 2) - 8;
        int boxBottom = boxTop + (textRenderer.fontHeight * 2) + 5;
        int boxLeft = centerX - (maxWidth / 2) - boxPadding;
        int boxRight = centerX + ((maxWidth + 1) / 2) + boxPadding;
        int lineOneX = centerX - (lineOneWidth / 2);
        int lineTwoX = centerX - (lineTwoWidth / 2);
        int lineOneY = boxTop + 2;
        int lineTwoY = lineOneY + textRenderer.fontHeight + 1;

        drawContext.fill(boxLeft, boxTop, boxRight, boxBottom, withAlpha(backgroundColor, 188));
        drawContext.fill(boxLeft, boxTop, boxRight, boxTop + 1, withAlpha(frameColor, 240));
        drawContext.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, withAlpha(frameColor, 220));
        drawContext.drawTextWithShadow(textRenderer, lineOne, lineOneX, lineOneY, textColor);
        drawContext.drawTextWithShadow(textRenderer, lineTwo, lineTwoX, lineTwoY, distanceColor);
    }

    private static String formatPingHealth(float healthValue) {
        if (!Float.isFinite(healthValue)) {
            return "?";
        }
        if (healthValue <= 0.0F) {
            return "0";
        }

        float roundedToTenth = Math.round(healthValue * 10.0F) / 10.0F;
        float roundedToWhole = Math.round(roundedToTenth);
        if (Math.abs(roundedToTenth - roundedToWhole) < 0.05F) {
            return Integer.toString((int) roundedToWhole);
        }

        return String.format(java.util.Locale.ROOT, "%.1f", roundedToTenth);
    }

    private static float resolveEntityHealth(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return Float.NaN;
        }

        return livingEntity.getHealth() + livingEntity.getAbsorptionAmount();
    }

    private static void prunePingLabelSnapshots(
            Int2ObjectMap<PingLabelSnapshot> labelSnapshots, IntSet activeEntityIds) {
        var iterator = labelSnapshots.keySet().iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.nextInt();
            if (!activeEntityIds.contains(entityId)) {
                iterator.remove();
            }
        }
    }

    private IntSet resolveActivePingVisualIds(
            IntSet serverMarkerIds, Int2LongMap visualExpiryMap, IntSet visualSeededIds, long now) {
        IntSet activeIds = new IntOpenHashSet(serverMarkerIds.size() + visualExpiryMap.size());
        long durationMillis = pingVisualDurationMillis();

        for (int entityId : serverMarkerIds) {
            if (entityId < 0) {
                continue;
            }

            if (!visualExpiryMap.containsKey(entityId) && !visualSeededIds.contains(entityId)) {
                visualExpiryMap.put(entityId, safeAddMillis(now, durationMillis));
                visualSeededIds.add(entityId);
            }

            if (visualExpiryMap.get(entityId) > now) {
                activeIds.add(entityId);
            }
        }

        var iterator = visualExpiryMap.int2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            Int2LongMap.Entry entry = iterator.next();
            if (entry.getLongValue() <= now) {
                iterator.remove();
                continue;
            }

            activeIds.add(entry.getIntKey());
        }

        return activeIds;
    }

    private static void renderPingBeaconParticles(
            ClientWorld world,
            IntSet entityIds,
            Int2ObjectMap<PingVisualAnchorSnapshot> staticAnchors,
            float tickProgress,
            boolean staticMode,
            ParticleEffect coreParticle,
            ParticleEffect orbitParticle) {
        prunePingVisualAnchorSnapshots(staticAnchors, entityIds);
        for (int entityId : entityIds) {
            PingVisualAnchorSnapshot anchorSnapshot =
                    resolvePingVisualAnchorSnapshot(
                            world, entityId, staticAnchors, tickProgress, staticMode);
            if (anchorSnapshot == null) {
                continue;
            }

            spawnPingBeaconParticles(
                    world, anchorSnapshot.beamBasePos(), entityId, coreParticle, orbitParticle);
        }
    }

    private static void spawnPingBeaconParticles(
            ClientWorld world,
            Vec3d beamBasePos,
            int entitySeed,
            ParticleEffect coreParticle,
            ParticleEffect orbitParticle) {
        double baseX = beamBasePos.x;
        double baseY = beamBasePos.y + 0.2D;
        double baseZ = beamBasePos.z;

        for (int segment = 0; segment < PING_PARTICLE_COLUMN_SEGMENTS; segment++) {
            double y = baseY + (segment * 0.42D);
            world.addImportantParticleClient(coreParticle, baseX, y, baseZ, 0.0D, 0.012D, 0.0D);

            double radius = 0.2D + (segment * 0.025D);
            double angle = (world.getTime() * 0.22D) + (segment * 1.17D) + (entitySeed * 0.13D);
            double orbitX = baseX + (Math.cos(angle) * radius);
            double orbitZ = baseZ + (Math.sin(angle) * radius);
            world.addParticleClient(orbitParticle, orbitX, y, orbitZ, 0.0D, 0.01D, 0.0D);
        }

        double topY = baseY + (PING_PARTICLE_COLUMN_SEGMENTS * 0.42D) + 0.2D;
        world.addImportantParticleClient(orbitParticle, baseX, topY, baseZ, 0.0D, 0.02D, 0.0D);
    }

    private static PingVisualAnchorSnapshot resolvePingVisualAnchorSnapshot(
            ClientWorld world,
            int entityId,
            Int2ObjectMap<PingVisualAnchorSnapshot> staticAnchors,
            float tickProgress,
            boolean staticMode) {
        if (entityId < 0) {
            return null;
        }

        if (staticMode) {
            PingVisualAnchorSnapshot existingSnapshot = staticAnchors.get(entityId);
            if (existingSnapshot != null) {
                return existingSnapshot;
            }
        }

        Entity entity = world.getEntityById(entityId);
        if (entity == null || entity.isRemoved()) {
            return staticMode ? staticAnchors.get(entityId) : null;
        }

        PingVisualAnchorSnapshot liveSnapshot =
                createPingVisualAnchorSnapshot(entity, tickProgress);
        if (liveSnapshot != null && staticMode) {
            staticAnchors.put(entityId, liveSnapshot);
        }
        return liveSnapshot;
    }

    private static PingVisualAnchorSnapshot createPingVisualAnchorSnapshot(
            Entity entity, float tickProgress) {
        Vec3d entityPos = entity.getLerpedPos(tickProgress);
        if (entityPos == null || !entityPos.isFinite()) {
            return null;
        }

        Vec3d sideOffset = computePingBeamHorizontalOffset(entity);
        if (!sideOffset.isFinite()) {
            return null;
        }

        Vec3d beamBasePos =
                new Vec3d(
                        entityPos.x + sideOffset.x, entityPos.y + 0.1D, entityPos.z + sideOffset.z);
        Vec3d labelAnchorPos =
                new Vec3d(
                        entityPos.x + sideOffset.x,
                        entityPos.y + entity.getHeight() + PING_LABEL_VERTICAL_OFFSET,
                        entityPos.z + sideOffset.z);
        if (!beamBasePos.isFinite() || !labelAnchorPos.isFinite()) {
            return null;
        }

        return new PingVisualAnchorSnapshot(beamBasePos, labelAnchorPos);
    }

    private static Vec3d computePingBeamHorizontalOffset(Entity entity) {
        Vec3d forward = Vec3d.fromPolar(0.0F, entity.getYaw());
        Vec3d side = new Vec3d(-forward.z, 0.0D, forward.x);
        if (!side.isFinite() || side.lengthSquared() < 1.0E-6D) {
            return new Vec3d(PING_BEAM_HORIZONTAL_OFFSET, 0.0D, 0.0D);
        }

        return side.normalize().multiply(PING_BEAM_HORIZONTAL_OFFSET);
    }

    private static void prunePingVisualAnchorSnapshots(
            Int2ObjectMap<PingVisualAnchorSnapshot> anchorSnapshots, IntSet activeEntityIds) {
        var iterator = anchorSnapshots.keySet().iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.nextInt();
            if (!activeEntityIds.contains(entityId)) {
                iterator.remove();
            }
        }
    }

    private synchronized void attemptSendClientHello(MinecraftClient client) {
        if (session.helloSent()
                || client.player == null
                || helloRetriesRemaining <= 0
                || helloRetryCooldownTicks > 0) {
            return;
        }

        if (!isCompanionTargetServer(client)) {
            return;
        }

        if (!ClientPlayNetworking.canSend(CompanionRawPayload.ID)) {
            if (!helloUnavailableLogged) {
                helloUnavailableLogged = true;
                LOGGER.debug("ClientHello postponed because channel cannot send yet");
            }
            helloRetryCooldownTicks =
                    Math.max(helloRetryCooldownTicks, CLIENT_HELLO_CHANNEL_UNAVAILABLE_RETRY_TICKS);
            return;
        }

        helloUnavailableLogged = false;
        helloRetriesRemaining--;
        String clientVersionPayload =
                launcherProofProvider.applyTo(buildAttestation.asStructuredClientVersion());
        ProtocolMessage.ClientHelloC2S hello =
                new ProtocolMessage.ClientHelloC2S(
                        clientVersionPayload, CLIENT_CAPABILITIES_BITSET);
        if (sendC2S(hello)) {
            session.markHelloSent();
            disableHelloRetryState();
            return;
        }

        if (helloRetriesRemaining > 0) {
            helloRetryCooldownTicks = CLIENT_HELLO_RETRY_INTERVAL_TICKS;
        }
    }

    private synchronized boolean sendC2S(ProtocolMessage message) {
        MinecraftClient client = MinecraftClient.getInstance();
        companionSendAttempts++;

        if (client.player == null) {
            companionSendBlockedNoPlayer++;
            return false;
        }

        if (!isCompanionTargetServer(client)) {
            companionSendBlockedTargetServer++;
            return false;
        }

        if (!ClientPlayNetworking.canSend(CompanionRawPayload.ID)) {
            companionSendBlockedChannelUnavailable++;
            return false;
        }

        long now = System.currentTimeMillis();
        if (!consumeCompanionSendPermit(now)) {
            companionSendBlockedRateLimit++;
            maybeLogCompanionRateLimit(now, client, message);
            return false;
        }

        try {
            byte[] bytes = protocolCodec.encode(message);
            ClientPlayNetworking.send(new CompanionRawPayload(bytes));
            companionSendSuccess++;
            return true;
        } catch (RuntimeException ex) {
            companionSendBlockedEncoding++;
            LOGGER.warn(
                    "Failed to send companion payload messageType={}",
                    message.getClass().getSimpleName(),
                    ex);
            return false;
        }
    }

    private void initializeHelloRetryState() {
        helloRetriesRemaining = CLIENT_HELLO_MAX_ATTEMPTS;
        helloRetryCooldownTicks = 0;
        helloUnavailableLogged = false;
    }

    private void disableHelloRetryState() {
        helloRetriesRemaining = 0;
        helloRetryCooldownTicks = 0;
        helloUnavailableLogged = false;
    }

    private void resetCompanionSendDiagnostics() {
        companionSendWindowStartAtMillis = 0L;
        companionSendWindowCount = 0;
        companionSendAttempts = 0L;
        companionSendSuccess = 0L;
        companionSendBlockedNoPlayer = 0L;
        companionSendBlockedTargetServer = 0L;
        companionSendBlockedChannelUnavailable = 0L;
        companionSendBlockedRateLimit = 0L;
        companionSendBlockedEncoding = 0L;
        lastCompanionRateLimitWarnAtMillis = 0L;
        lastCompanionDiagnosticsLogAtMillis = 0L;
    }

    private boolean consumeCompanionSendPermit(long nowMillis) {
        if (companionSendWindowStartAtMillis <= 0L
                || (nowMillis - companionSendWindowStartAtMillis) >= COMPANION_C2S_WINDOW_MILLIS) {
            companionSendWindowStartAtMillis = nowMillis;
            companionSendWindowCount = 0;
        }

        if (companionSendWindowCount >= COMPANION_C2S_MAX_PER_SECOND) {
            return false;
        }

        companionSendWindowCount++;
        return true;
    }

    private void maybeLogCompanionRateLimit(
            long nowMillis, MinecraftClient client, ProtocolMessage message) {
        if ((nowMillis - lastCompanionRateLimitWarnAtMillis)
                < COMPANION_C2S_RATE_LIMIT_WARN_COOLDOWN_MILLIS) {
            return;
        }

        lastCompanionRateLimitWarnAtMillis = nowMillis;
        LOGGER.warn(
                "Companion packet rate-limit active server={} messageType={} windowCount={} windowMs={}",
                resolveCurrentServerAddress(client),
                message.getClass().getSimpleName(),
                companionSendWindowCount,
                COMPANION_C2S_WINDOW_MILLIS);
    }

    private void maybeLogCompanionPacketDiagnostics(MinecraftClient client) {
        if (!PACKET_DIAGNOSTICS_ENABLED) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastCompanionDiagnosticsLogAtMillis)
                < COMPANION_DIAGNOSTICS_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastCompanionDiagnosticsLogAtMillis = now;
        LOGGER.info(
                "Companion packet diagnostics server={} attempts={} sent={} blockedNoPlayer={} blockedTarget={} blockedChannel={} blockedRateLimit={} blockedEncoding={} helloSent={} helloRetriesRemaining={} helloCooldownTicks={}",
                resolveCurrentServerAddress(client),
                companionSendAttempts,
                companionSendSuccess,
                companionSendBlockedNoPlayer,
                companionSendBlockedTargetServer,
                companionSendBlockedChannelUnavailable,
                companionSendBlockedRateLimit,
                companionSendBlockedEncoding,
                session.helloSent(),
                helloRetriesRemaining,
                helloRetryCooldownTicks);
    }

    private static String resolveCurrentServerAddress(MinecraftClient client) {
        if (client == null) {
            return "unknown";
        }

        ServerInfo currentServer = client.getCurrentServerEntry();
        if (currentServer == null
                || currentServer.address == null
                || currentServer.address.isBlank()) {
            return "unknown";
        }

        return currentServer.address;
    }

    private boolean isCompanionTargetServer(MinecraftClient client) {
        if (client == null) {
            return false;
        }

        ServerInfo currentServer = client.getCurrentServerEntry();
        if (currentServer == null
                || currentServer.address == null
                || currentServer.address.isBlank()) {
            return false;
        }

        String normalizedHost = normalizeServerAddressHost(currentServer.address);
        if (normalizedHost.isEmpty()) {
            return false;
        }

        CompanionConfig currentConfig = getConfig();
        for (String allowedServerId : currentConfig.allowedServerIds) {
            if (matchesAllowedServerId(normalizedHost, allowedServerId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesAllowedServerId(String normalizedHost, String allowedServerId) {
        String normalizedAllowed = normalizeServerAddressHost(allowedServerId);
        if (normalizedAllowed.isEmpty()) {
            return false;
        }

        return normalizedHost.equals(normalizedAllowed)
                || normalizedHost.endsWith("." + normalizedAllowed);
    }

    private static String normalizeServerAddressHost(String address) {
        if (address == null) {
            return "";
        }

        String normalized = address.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }

        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator >= 0 && schemeSeparator + 3 < normalized.length()) {
            normalized = normalized.substring(schemeSeparator + 3);
        }

        int pathSeparator = normalized.indexOf('/');
        if (pathSeparator >= 0) {
            normalized = normalized.substring(0, pathSeparator);
        }

        if (normalized.startsWith("[")) {
            int closingBracket = normalized.indexOf(']');
            if (closingBracket > 0) {
                normalized = normalized.substring(1, closingBracket);
            }
        } else {
            int lastColon = normalized.lastIndexOf(':');
            if (lastColon > 0 && normalized.indexOf(':') == lastColon) {
                normalized = normalized.substring(0, lastColon);
            }
        }

        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static boolean isTruthy(String rawValue) {
        if (rawValue == null) {
            return false;
        }

        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        return "1".equals(normalized)
                || "true".equalsIgnoreCase(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized);
    }

    private synchronized void logMalformedOncePerConnection(BinaryDecodingException ex) {
        CompanionConfig currentConfig = getConfig();

        if (currentConfig.logMalformedOncePerConnection && session.malformedPacketLogged()) {
            return;
        }

        if (currentConfig.logMalformedOncePerConnection) {
            session.markMalformedPacketLogged();
        }

        LOGGER.warn("Dropped malformed companion payload: {}", ex.getMessage());
    }

    private synchronized void ensureFeatureDefaultsPersisted() {
        CompanionConfig currentConfig = getConfig();
        boolean changed = false;

        for (ClientFeatureDefinition feature : ClientFeatures.all()) {
            if (!currentConfig.featureToggles.containsKey(feature.id())) {
                currentConfig.featureToggles.put(feature.id(), feature.defaultEnabled());
                changed = true;
            }
        }

        if (changed) {
            configManager.save(currentConfig);
            config = currentConfig;
        }
    }

    private void initializePingKeybinds() {
        if (gangPingKeyBinding == null) {
            gangPingKeyBinding =
                    registerPingKeybind("key.cosmicprisonsmod.ping.gang", GLFW.GLFW_KEY_G);
        }

        if (trucePingKeyBinding == null) {
            trucePingKeyBinding =
                    registerPingKeybind("key.cosmicprisonsmod.ping.truce", GLFW.GLFW_KEY_H);
        }
    }

    private CompanionConfig getConfig() {
        if (config == null) {
            config = configManager.getOrLoad();
        }

        return config;
    }

    private boolean getFeatureToggleState(String featureId) {
        CompanionConfig currentConfig = getConfig();
        Boolean stored = currentConfig.featureToggles.get(featureId);

        if (stored != null) {
            return stored;
        }

        return ClientFeatures.findById(featureId)
                .map(ClientFeatureDefinition::defaultEnabled)
                .orElse(false);
    }

    private boolean isFeatureSupportedByServer(ClientFeatureDefinition feature) {
        if (feature.requiredServerFeatureBit().isEmpty()) {
            return true;
        }

        if (!session.gateState().isEnabled()) {
            return false;
        }

        int requiredBit = feature.requiredServerFeatureBit().getAsInt();
        return (session.serverFeatureFlags() & requiredBit) != 0;
    }

    private boolean shouldRenderAnyInventoryItemOverlays() {
        return session.gateState().isEnabled()
                && session.inventoryItemOverlaysSupported()
                && (getFeatureToggleState(ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID)
                        || getFeatureToggleState(ClientFeatures.HUD_PETS_ID));
    }

    private boolean shouldRenderOverlayType(int overlayType) {
        if (!session.gateState().isEnabled() || !session.inventoryItemOverlaysSupported()) {
            return false;
        }

        return isPetOverlayType(overlayType)
                ? getFeatureToggleState(ClientFeatures.HUD_PETS_ID)
                : getFeatureToggleState(ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID);
    }

    private static boolean isPetOverlayType(int overlayType) {
        return overlayType == ProtocolConstants.OVERLAY_TYPE_PET_ACTIVE
                || overlayType == ProtocolConstants.OVERLAY_TYPE_PET_COOLDOWN;
    }

    private boolean shouldRenderWidget(String featureId) {
        return getFeatureToggleState(featureId) && isFeatureSupportedByServer(featureId);
    }

    private boolean shouldRenderHudWidgets() {
        return session.gateState().isEnabled() && session.hudWidgetsSupported();
    }

    private boolean shouldHandlePingKeybinds(MinecraftClient client) {
        return client != null && client.player != null && client.currentScreen == null;
    }

    private boolean shouldRenderPingBeacons(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.world != null
                && session.gateState().isEnabled()
                && getFeatureToggleState(ClientFeatures.PINGS_ID)
                && (isFeatureSupportedByServer(ClientFeatures.PINGS_ID)
                        || !session.gangPingBeaconIdsSnapshot().isEmpty()
                        || !session.trucePingBeaconIdsSnapshot().isEmpty()
                        || !gangPingVisualExpiryAtMillis.isEmpty()
                        || !trucePingVisualExpiryAtMillis.isEmpty());
    }

    private boolean canSendPingIntent(MinecraftClient client) {
        return client != null
                && client.player != null
                && session.gateState().isEnabled()
                && getFeatureToggleState(ClientFeatures.PINGS_ID)
                && isFeatureSupportedByServer(ClientFeatures.PINGS_ID)
                && ClientPlayNetworking.canSend(CompanionRawPayload.ID);
    }

    private void maybeSendPingUnavailableFeedback(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastPingFeedbackAtMillis) < PING_FEEDBACK_COOLDOWN_MILLIS) {
            return;
        }
        lastPingFeedbackAtMillis = now;

        if (!session.gateState().isEnabled()) {
            sendClientMessage(
                    client, Text.translatable("text.cosmicprisonsmod.pings.unavailable.handshake"));
            return;
        }

        if (!getFeatureToggleState(ClientFeatures.PINGS_ID)) {
            sendClientMessage(
                    client, Text.translatable("text.cosmicprisonsmod.pings.unavailable.disabled"));
            return;
        }

        if (!isFeatureSupportedByServer(ClientFeatures.PINGS_ID)) {
            sendClientMessage(
                    client,
                    Text.translatable("text.cosmicprisonsmod.pings.unavailable.unsupported"));
            return;
        }

        sendClientMessage(
                client, Text.translatable("text.cosmicprisonsmod.pings.unavailable.channel"));
    }

    private static boolean isKeyBindingDown(KeyBinding keyBinding) {
        return keyBinding != null && keyBinding.isPressed();
    }

    private long pingVisualDurationMillis() {
        return pingVisualDurationSeconds() * MILLIS_PER_SECOND;
    }

    private static long safeAddMillis(long baseMillis, long addMillis) {
        if (addMillis <= 0L) {
            return baseMillis;
        }

        if (Long.MAX_VALUE - baseMillis < addMillis) {
            return Long.MAX_VALUE;
        }

        return baseMillis + addMillis;
    }

    private void clearPingVisualTracking() {
        gangPingVisualExpiryAtMillis.clear();
        trucePingVisualExpiryAtMillis.clear();
        gangPingVisualSeededIds.clear();
        trucePingVisualSeededIds.clear();
        clearPingAnchorSnapshots();
    }

    private void clearPingAnchorSnapshots() {
        gangPingStaticAnchors.clear();
        trucePingStaticAnchors.clear();
        gangPingLabelSnapshots.clear();
        trucePingLabelSnapshots.clear();
    }

    private boolean shouldApplyPeacefulMiningPassThrough(MinecraftClient client) {
        if (client == null
                || client.player == null
                || client.world == null
                || client.options == null) {
            return false;
        }

        if (!session.gateState().isEnabled()
                || !isFeatureSupportedByServer(ClientFeatures.PEACEFUL_MINING_ID)
                || !getFeatureToggleState(ClientFeatures.PEACEFUL_MINING_ID)) {
            return false;
        }

        if (!client.player.getMainHandStack().isIn(ItemTags.PICKAXES)) {
            return false;
        }

        return client.options.attackKey.isPressed()
                || (client.interactionManager != null
                        && client.interactionManager.isBreakingBlock());
    }

    private static void drawOverlayText(
            DrawContext drawContext,
            int slotX,
            int slotY,
            ConnectionSessionState.ItemOverlayEntry overlayEntry) {
        if (overlayEntry.displayText() == null || overlayEntry.displayText().isEmpty()) {
            return;
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        if (textRenderer == null) {
            return;
        }

        String displayText = overlayEntry.displayText();
        int textWidth = textRenderer.getWidth(displayText);

        if (textWidth <= 0) {
            return;
        }

        float baseScale = 0.68F;
        float fittedScale = Math.min(baseScale, 15.0F / textWidth);
        float textScale = Math.max(0.35F, fittedScale);
        int scaledTextWidth = Math.max(1, Math.round(textWidth * textScale));
        int scaledTextHeight = Math.max(1, Math.round(textRenderer.fontHeight * textScale));
        int textX = Math.max(slotX + 1, slotX + 16 - scaledTextWidth - 1);
        int textY = Math.max(slotY + 1, slotY + 16 - scaledTextHeight - 1);
        int textColor = colorForOverlayType(overlayEntry.overlayType());
        int backgroundColor = backgroundColorForOverlayType(overlayEntry.overlayType());
        int backgroundLeft = Math.max(slotX, textX - 1);
        int backgroundTop = Math.max(slotY, textY - 1);
        int backgroundRight = Math.min(slotX + 16, textX + scaledTextWidth + 1);
        int backgroundBottom = Math.min(slotY + 16, textY + scaledTextHeight + 1);

        drawContext.fill(
                backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, backgroundColor);
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(textX, textY);
        drawContext.getMatrices().scale(textScale, textScale);
        drawContext.drawTextWithShadow(textRenderer, displayText, 0, 0, textColor);
        drawContext.getMatrices().popMatrix();
    }

    private static int colorForOverlayType(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xFF5DE6FF;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xFF84F08F;
            case ProtocolConstants.OVERLAY_TYPE_GANG_POINT_NOTE -> 0xFFFFC659;
            case ProtocolConstants.OVERLAY_TYPE_SATCHEL_PERCENT -> 0xFFB5E86C;
            case ProtocolConstants.OVERLAY_TYPE_PET_ACTIVE -> 0xFF8BF5C2;
            case ProtocolConstants.OVERLAY_TYPE_PET_COOLDOWN -> 0xFFFFB27D;
            default -> 0xFFE6E6E6;
        };
    }

    private static int backgroundColorForOverlayType(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xA01E3F52;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xA01B3C25;
            case ProtocolConstants.OVERLAY_TYPE_GANG_POINT_NOTE -> 0xA04A361A;
            case ProtocolConstants.OVERLAY_TYPE_SATCHEL_PERCENT -> 0xA0284A1E;
            case ProtocolConstants.OVERLAY_TYPE_PET_ACTIVE -> 0xA01D4733;
            case ProtocolConstants.OVERLAY_TYPE_PET_COOLDOWN -> 0xA04E2A1B;
            default -> 0xA0333333;
        };
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static String normalizeWidgetId(String widgetId) {
        if (widgetId == null) {
            return "";
        }
        return widgetId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private static KeyBinding registerPingKeybind(String translationKey, int defaultKeyCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        translationKey,
                        InputUtil.Type.KEYSYM,
                        defaultKeyCode,
                        PING_KEYBIND_CATEGORY));
    }

    private static void bindKey(KeyBinding keyBinding, KeyInput keyInput) {
        keyBinding.setBoundKey(InputUtil.fromKeyCode(keyInput));
        KeyBinding.updateKeysByCode();
        persistGameOptions();
    }

    private static void persistGameOptions() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.options != null) {
            client.options.write();
        }
    }

    private static void sendClientMessage(MinecraftClient client, Text text) {
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }

    private static final class LauncherProofProvider {
        private volatile String cachedProof = "";
        private volatile boolean loaded;

        private String applyTo(String structuredVersion) {
            String proof = resolveProof();
            if (proof.isBlank()) {
                return structuredVersion;
            }

            return structuredVersion + ";proof=" + proof;
        }

        private String resolveProof() {
            if (loaded) {
                return cachedProof;
            }

            synchronized (this) {
                if (loaded) {
                    return cachedProof;
                }

                String proof = sanitizeProof(readProofFromPropertyOrEnv());
                if (proof.isBlank()) {
                    proof = sanitizeProof(readProofFromFileProperty());
                }

                cachedProof = proof;
                loaded = true;
                if (!proof.isBlank()) {
                    LOGGER.info("Launcher proof token attached to ClientHello");
                }
                return cachedProof;
            }
        }

        private static String readProofFromPropertyOrEnv() {
            String property = System.getProperty(LAUNCHER_PROOF_PROPERTY, "");
            if (!property.isBlank()) {
                return property;
            }

            String env = System.getenv(LAUNCHER_PROOF_ENV);
            return env == null ? "" : env;
        }

        private static String readProofFromFileProperty() {
            String filePath = System.getProperty(LAUNCHER_PROOF_FILE_PROPERTY, "");
            if (filePath.isBlank()) {
                return "";
            }

            try {
                Path path = Path.of(filePath).normalize();
                if (!Files.isRegularFile(path)) {
                    return "";
                }

                String token = Files.readString(path, StandardCharsets.UTF_8).trim();
                try {
                    Files.delete(path);
                } catch (Exception ex) {
                    LOGGER.debug("Failed to delete launcher proof file: {}", ex.getMessage());
                }
                return token;
            } catch (Exception ex) {
                LOGGER.debug("Failed to read launcher proof file: {}", ex.getMessage());
                return "";
            }
        }

        private static String sanitizeProof(String rawProof) {
            if (rawProof == null) {
                return "";
            }

            String trimmed = rawProof.trim();
            if (trimmed.isEmpty()) {
                return "";
            }

            if (trimmed.indexOf(';') >= 0
                    || trimmed.indexOf('\n') >= 0
                    || trimmed.indexOf('\r') >= 0) {
                return "";
            }

            if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_LAUNCHER_PROOF_BYTES) {
                return "";
            }

            return trimmed;
        }
    }

    private String resolveInstalledVersionForUpdateCheck() {
        BuildAttestation attestation = buildAttestation;
        if (attestation != null) {
            String buildId = sanitizeVersionToken(attestation.buildId());
            if (!buildId.isBlank()) {
                return buildId;
            }

            if (attestation.isSigned()) {
                String attestedModVersion = sanitizeVersionToken(attestation.modVersion());
                if (!attestedModVersion.isBlank()) {
                    return attestedModVersion;
                }
            }
        }

        String artifactVersion = resolveRuntimeArtifactVersion();
        if (!artifactVersion.isBlank()) {
            return artifactVersion;
        }

        String embeddedVersion = resolveEmbeddedBuildVersion();
        if (!embeddedVersion.isBlank()) {
            return embeddedVersion;
        }

        return resolveModVersion();
    }

    private String resolveRuntimeArtifactVersion() {
        try {
            Path artifactPath =
                    Path.of(
                            CompanionClientRuntime.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
            String fileName =
                    artifactPath.getFileName() == null ? "" : artifactPath.getFileName().toString();
            Matcher matcher = RUNTIME_ARTIFACT_VERSION_PATTERN.matcher(fileName);
            if (!matcher.matches()) {
                return "";
            }
            return sanitizeVersionToken(matcher.group(1));
        } catch (Exception ex) {
            LOGGER.debug("Failed to resolve runtime artifact version: {}", ex.getMessage());
            return "";
        }
    }

    private String resolveEmbeddedBuildVersion() {
        try (InputStream input =
                CompanionClientRuntime.class
                        .getClassLoader()
                        .getResourceAsStream("official-build.properties")) {
            if (input == null) {
                return "";
            }

            Properties properties = new Properties();
            properties.load(input);
            String buildId = sanitizeVersionToken(properties.getProperty("buildId"));
            if (!buildId.isBlank()) {
                return buildId;
            }

            return sanitizeVersionToken(properties.getProperty("modVersion"));
        } catch (Exception ex) {
            LOGGER.debug("Failed to resolve embedded build version: {}", ex.getMessage());
            return "";
        }
    }

    private static String sanitizeVersionToken(String rawVersion) {
        if (rawVersion == null) {
            return "";
        }

        String normalized = rawVersion.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        if ("unknown".equalsIgnoreCase(normalized)
                || "dev-local".equalsIgnoreCase(normalized)
                || "UNSIGNED".equalsIgnoreCase(normalized)) {
            return "";
        }

        return normalized;
    }

    private static final class ModUpdateChecker {
        private final Supplier<String> currentVersionSupplier;
        private final HttpClient httpClient;
        private final ExecutorService executor;
        private volatile long nextCheckAtMillis;
        private volatile boolean inFlight;
        private volatile UpdateNotice notice;

        private ModUpdateChecker(Supplier<String> currentVersionSupplier) {
            this.currentVersionSupplier = currentVersionSupplier;
            this.httpClient =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(4))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
            this.executor =
                    Executors.newSingleThreadExecutor(
                            runnable -> {
                                Thread thread = new Thread(runnable, "cosmicprisons-update-check");
                                thread.setDaemon(true);
                                return thread;
                            });
            this.notice = UpdateNotice.notOutdated(currentVersionNow());
            this.nextCheckAtMillis = 0L;
            this.inFlight = false;
        }

        private void tick() {
            long now = System.currentTimeMillis();
            if (inFlight || now < nextCheckAtMillis) {
                return;
            }

            inFlight = true;
            nextCheckAtMillis = now + UPDATE_CHECK_INTERVAL_MILLIS;
            executor.execute(this::runCheck);
        }

        private UpdateNotice notice() {
            return notice;
        }

        private void runCheck() {
            try {
                HttpRequest request =
                        HttpRequest.newBuilder(URI.create(MOD_RELEASE_MANIFEST_URL))
                                .timeout(Duration.ofSeconds(6))
                                .header("Accept", "application/json")
                                .header("User-Agent", "CosmicPrisonsMod-UpdateChecker")
                                .GET()
                                .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return;
                }

                String latestVersion = parseManifestVersion(response.body());
                if (latestVersion.isBlank()) {
                    return;
                }

                String currentVersion = currentVersionNow();
                notice = evaluateNotice(currentVersion, latestVersion);
            } catch (Exception ex) {
                LOGGER.debug("Update check failed: {}", ex.getMessage());
            } finally {
                inFlight = false;
            }
        }

        private String currentVersionNow() {
            String resolved = sanitizeVersionToken(currentVersionSupplier.get());
            if (!resolved.isBlank()) {
                return resolved;
            }

            String fallback = sanitizeVersionToken(resolveModVersion());
            return fallback.isBlank() ? "unknown" : fallback;
        }

        private static UpdateNotice evaluateNotice(String currentVersion, String latestVersion) {
            ParsedVersion currentParsed = ParsedVersion.parse(currentVersion);
            ParsedVersion latestParsed = ParsedVersion.parse(latestVersion);
            if (currentParsed == null || latestParsed == null) {
                return UpdateNotice.notOutdated(currentVersion);
            }

            if (latestParsed.compareTo(currentParsed) > 0) {
                return UpdateNotice.outdated(currentVersion, latestVersion);
            }

            return UpdateNotice.notOutdated(currentVersion);
        }

        private static String parseManifestVersion(String body) {
            Matcher matcher = MANIFEST_MOD_VERSION_PATTERN.matcher(body);
            if (!matcher.find()) {
                return "";
            }

            return matcher.group(1).trim();
        }

        private record UpdateNotice(boolean outdated, String currentVersion, String latestVersion) {
            private static UpdateNotice notOutdated(String currentVersion) {
                return new UpdateNotice(false, currentVersion, currentVersion);
            }

            private static UpdateNotice outdated(String currentVersion, String latestVersion) {
                return new UpdateNotice(true, currentVersion, latestVersion);
            }
        }

        private record ParsedVersion(int major, int minor, int patch)
                implements Comparable<ParsedVersion> {
            private static ParsedVersion parse(String raw) {
                if (raw == null || raw.isBlank()) {
                    return null;
                }

                Matcher matcher = VERSION_PATTERN.matcher(raw.trim());
                if (!matcher.matches()) {
                    return null;
                }

                int major = Integer.parseInt(matcher.group(1));
                int minor = parsePart(matcher.group(2));
                int patch = parsePart(matcher.group(3));
                return new ParsedVersion(major, minor, patch);
            }

            private static int parsePart(String value) {
                if (value == null || value.isBlank()) {
                    return 0;
                }

                return Integer.parseInt(value);
            }

            @Override
            public int compareTo(ParsedVersion other) {
                int majorCompare = Integer.compare(major, other.major);
                if (majorCompare != 0) {
                    return majorCompare;
                }

                int minorCompare = Integer.compare(minor, other.minor);
                if (minorCompare != 0) {
                    return minorCompare;
                }

                return Integer.compare(patch, other.patch);
            }
        }
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(CosmicPrisonsMod.MOD_ID)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private record GangLayout(List<GangPrimaryLine> primaryLines, List<String> metadataLines) {}

    private record GangPrimaryLine(String text, int section, boolean header) {}

    private record PetWidgetRow(
            String petKey,
            String displayName,
            int level,
            int count,
            int levelProgressPercent,
            long activeRemainingSeconds,
            long cooldownRemainingSeconds,
            boolean overflow) {
        private long statusSeconds() {
            if (activeRemainingSeconds > 0L) {
                return activeRemainingSeconds;
            }
            return cooldownRemainingSeconds;
        }
    }

    private record LeaderboardCandidate(
            HudWidgetCatalog.WidgetDescriptor descriptor, List<String> lines) {}

    private record LeaderboardEntry(int rank, String name, String value) {}

    /** Immutable geometry snapshot for a renderable HUD widget panel. */
    public record HudWidgetPanel(
            String widgetId,
            String titleTranslationKey,
            int accentColor,
            int x,
            int y,
            int width,
            int height,
            int lineHeight,
            float scale,
            boolean compactMode,
            List<String> lines) {
        public HudWidgetPanel {
            scale =
                    (float)
                            CompanionConfig.clampHudWidgetScale(
                                    Double.isFinite(scale) ? scale : 1.0D);
            lines = List.copyOf(lines);
        }

        /** Returns panel width in actual rendered pixels after scale is applied. */
        public int physicalWidth() {
            return Math.max(1, Math.round(width * scale));
        }

        /** Returns panel height in actual rendered pixels after scale is applied. */
        public int physicalHeight() {
            return Math.max(1, Math.round(height * scale));
        }
    }

    private record KnownOverlayStack(
            ItemStack stack, ConnectionSessionState.ItemOverlayEntry overlayEntry) {}
}
