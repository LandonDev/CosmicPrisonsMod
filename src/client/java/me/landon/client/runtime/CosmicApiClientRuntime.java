package me.landon.client.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.landon.CosmicPrisonsMod;
import me.landon.cosmicapi.network.CosmicApiRawPayload;
import me.landon.cosmicapi.protocol.CosmicApiClientHello;
import me.landon.cosmicapi.protocol.CosmicApiProtocolCodec;
import me.landon.cosmicapi.protocol.CosmicApiProtocolConstants;
import me.landon.cosmicapi.protocol.CosmicApiServerMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class CosmicApiClientRuntime {
    private static final CosmicApiClientRuntime INSTANCE = new CosmicApiClientRuntime();
    private static final Logger LOGGER = LoggerFactory.getLogger(CosmicPrisonsMod.MOD_ID + "/api");
    private static final int INITIAL_HELLO_DELAY_TICKS = 60;
    private static final int HELLO_RETRY_INTERVAL_TICKS = 40;
    private static final int MAX_HELLO_ATTEMPTS = 20;
    private static final int PING_INTERVAL_TICKS = 200;
    private static final String INSTALL_ID_FILENAME = "cosmic-api-install-id.txt";

    private final CosmicApiProtocolCodec protocolCodec = new CosmicApiProtocolCodec();

    private boolean initialized;
    private boolean helloResponseReceived;
    private boolean channelUnavailableLogged;
    private int helloRetriesRemaining = MAX_HELLO_ATTEMPTS;
    private int helloRetryCooldownTicks;
    private int pingCooldownTicks;
    private String cachedInstallId = "";
    private String activeSessionId = "";
    private List<String> allowedScopes = List.of();
    private List<String> allowedHooks = List.of();
    private long lastPingSentAtMillis;
    private long lastPongReceivedAtMillis;
    private long lastRoundTripMillis = -1L;
    private long lastServerTimeMillis;

    private CosmicApiClientRuntime() {}

    public static CosmicApiClientRuntime getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isSessionActive() {
        return !activeSessionId.isBlank();
    }

    public synchronized boolean hasAllowedScope(String scope) {
        return CosmicApiProtocolConstants.hasScope(allowedScopes, scope);
    }

    public synchronized boolean hasAllowedHook(String hook) {
        return hook != null && allowedHooks.contains(hook);
    }

    public synchronized long lastPongReceivedAtMillis() {
        return lastPongReceivedAtMillis;
    }

    public synchronized long lastRoundTripMillis() {
        return lastRoundTripMillis;
    }

    public synchronized long lastServerTimeMillis() {
        return lastServerTimeMillis;
    }

    public synchronized boolean sendAction(String actionType, Map<String, Object> payload) {
        if (activeSessionId.isBlank() || actionType == null || actionType.isBlank()) {
            return false;
        }

        String requiredScope = CosmicApiProtocolConstants.requiredScopeForClientAction(actionType);
        if (requiredScope != null && !hasAllowedScope(requiredScope)) {
            LOGGER.debug("Skipped Cosmic API action {} without approved scope", actionType);
            return false;
        }

        try {
            ClientPlayNetworking.send(
                    new CosmicApiRawPayload(
                            protocolCodec.encodeClientAction(
                                    activeSessionId, actionType, safePayload(payload))));
            LOGGER.debug("Sent Cosmic API action {}", actionType);
            return true;
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to send Cosmic API action {}", actionType, ex);
            return false;
        }
    }

    public synchronized boolean sendEvent(String eventType, Map<String, Object> payload) {
        if (activeSessionId.isBlank() || eventType == null || eventType.isBlank()) {
            return false;
        }

        if (!hasAllowedHook(eventType)) {
            LOGGER.debug("Skipped Cosmic API event {} without approved hook grant", eventType);
            return false;
        }

        try {
            ClientPlayNetworking.send(
                    new CosmicApiRawPayload(
                            protocolCodec.encodeClientEvent(
                                    activeSessionId, eventType, safePayload(payload))));
            LOGGER.debug("Sent Cosmic API event {}", eventType);
            return true;
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to send Cosmic API event {}", eventType, ex);
            return false;
        }
    }

    public synchronized void initializeClient() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientPlayNetworking.registerGlobalReceiver(
                CosmicApiRawPayload.ID, this::onPayloadReceived);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        System.out.println("[CosmicPrisonsMod] Cosmic API client runtime initialized");
        LOGGER.info("Cosmic API client runtime initialized");
    }

    private synchronized void onJoin(MinecraftClient client) {
        resetConnectionState();
        System.out.println("[CosmicPrisonsMod] Joined server; preparing Cosmic API hello");
        LOGGER.info("Cosmic API connection joined; preparing official client hello");
        helloRetryCooldownTicks = INITIAL_HELLO_DELAY_TICKS;
    }

    private synchronized void onDisconnect() {
        resetConnectionState();
    }

    private synchronized void onEndTick(MinecraftClient client) {
        if (!helloResponseReceived) {
            if (helloRetryCooldownTicks > 0) {
                helloRetryCooldownTicks--;
            } else {
                attemptSendClientHello(client);
            }
        }
        maybeSendClientPing(client);
    }

    private void onPayloadReceived(
            CosmicApiRawPayload payload, ClientPlayNetworking.Context context) {
        CosmicApiServerMessage message;
        try {
            message = protocolCodec.decodeServerMessage(payload.payloadBytes());
        } catch (RuntimeException ex) {
            LOGGER.debug("Ignored invalid Cosmic API server payload: {}", ex.getMessage());
            return;
        }

        context.client().execute(() -> handleServerMessage(message));
    }

    private synchronized void handleServerMessage(CosmicApiServerMessage message) {
        helloResponseReceived = true;
        System.out.println("[CosmicPrisonsMod] Received Cosmic API payload type " + message.type());
        switch (message.type()) {
            case "grant_required" -> {
                activeSessionId = "";
                allowedScopes = List.of();
                allowedHooks = List.of();
                CompanionClientRuntime.getInstance().onCosmicApiSessionCleared();
                System.out.println(
                        "[CosmicPrisonsMod] Cosmic API access approval requested for "
                                + valueOrFallback(message.appName(), "registered mod")
                                + " ("
                                + message.requestId()
                                + ")");
                LOGGER.info(
                        "Cosmic API access approval requested for {} ({})",
                        valueOrFallback(message.appName(), "registered mod"),
                        message.requestId());
            }
            case "resolve" -> handleResolveMessage(message);
            case "action" -> handleActionMessage(message);
            case "event", "hook" -> handleEventMessage(message);
            case "pong" -> handlePongMessage(message);
            default -> LOGGER.debug("Received Cosmic API payload type {}", message.type());
        }
    }

    private void attemptSendClientHello(MinecraftClient client) {
        if (helloResponseReceived || client.player == null || helloRetriesRemaining <= 0) {
            return;
        }

        if (!ClientPlayNetworking.canSend(CosmicApiRawPayload.ID)) {
            if (!channelUnavailableLogged) {
                channelUnavailableLogged = true;
                LOGGER.debug("Cosmic API hello postponed because channel cannot send yet");
            }
            helloRetryCooldownTicks = HELLO_RETRY_INTERVAL_TICKS;
            return;
        }

        channelUnavailableLogged = false;
        helloRetriesRemaining--;

        try {
            CosmicApiClientHello hello =
                    CosmicApiClientHello.official(
                            resolveInstallId(), resolveMinecraftVersion(), resolveModVersion());
            ClientPlayNetworking.send(
                    new CosmicApiRawPayload(protocolCodec.encodeClientHello(hello)));
            helloRetryCooldownTicks = HELLO_RETRY_INTERVAL_TICKS;
            System.out.println(
                    "[CosmicPrisonsMod] Cosmic API official client hello sent, attempts remaining "
                            + helloRetriesRemaining);
            LOGGER.info("Cosmic API official client hello sent");
        } catch (RuntimeException ex) {
            System.out.println(
                    "[CosmicPrisonsMod] Failed to send Cosmic API official client hello: "
                            + ex.getMessage());
            LOGGER.warn("Failed to send Cosmic API official client hello", ex);
            if (helloRetriesRemaining > 0) {
                helloRetryCooldownTicks = HELLO_RETRY_INTERVAL_TICKS;
            }
        }
    }

    private void resetConnectionState() {
        helloResponseReceived = false;
        channelUnavailableLogged = false;
        helloRetriesRemaining = MAX_HELLO_ATTEMPTS;
        helloRetryCooldownTicks = 0;
        pingCooldownTicks = 0;
        activeSessionId = "";
        allowedScopes = List.of();
        allowedHooks = List.of();
        lastPingSentAtMillis = 0L;
        lastPongReceivedAtMillis = 0L;
        lastRoundTripMillis = -1L;
        lastServerTimeMillis = 0L;
    }

    private void handleResolveMessage(CosmicApiServerMessage message) {
        if (message.allowed()) {
            activeSessionId = valueOrFallback(message.sessionId(), "");
            allowedScopes = copyOrEmpty(message.allowedScopes());
            allowedHooks = copyOrEmpty(message.allowedHooks());
            pingCooldownTicks = 0;
            System.out.println(
                    "[CosmicPrisonsMod] Cosmic API session approved with "
                            + allowedScopes.size()
                            + " scope(s) and "
                            + allowedHooks.size()
                            + " hook(s)");
            LOGGER.info(
                    "Cosmic API session approved with {} scope(s) and {} hook(s)",
                    allowedScopes.size(),
                    allowedHooks.size());
            CompanionClientRuntime.getInstance()
                    .onCosmicApiSessionResolved(message.serverScope(), allowedScopes, allowedHooks);
            return;
        }

        activeSessionId = "";
        allowedScopes = List.of();
        allowedHooks = List.of();
        pingCooldownTicks = 0;
        CompanionClientRuntime.getInstance().onCosmicApiSessionCleared();
        System.out.println(
                "[CosmicPrisonsMod] Cosmic API session denied: "
                        + valueOrFallback(message.reason(), "access_denied"));
        LOGGER.info(
                "Cosmic API session denied: {}",
                valueOrFallback(message.reason(), "access_denied"));
    }

    private void handleActionMessage(CosmicApiServerMessage message) {
        if (activeSessionId.isBlank()) {
            LOGGER.debug("Ignored Cosmic API action before session approval");
            return;
        }

        String sessionId = valueOrFallback(message.sessionId(), "");
        if (!sessionId.isBlank() && !activeSessionId.equals(sessionId)) {
            LOGGER.debug(
                    "Ignored Cosmic API action for inactive session {}",
                    valueOrFallback(sessionId, "unknown"));
            return;
        }

        String actionType = valueOrFallback(message.actionType(), "");
        if (actionType.isBlank()) {
            actionType = stringPayloadValue(message.payload(), "actionType");
        }
        if (actionType.isBlank()) {
            actionType = stringPayloadValue(message.payload(), "type");
        }
        String requiredScope = CosmicApiProtocolConstants.requiredScopeForServerAction(actionType);
        if (requiredScope != null && !hasAllowedScope(requiredScope)) {
            LOGGER.debug(
                    "Ignored Cosmic API action {} without approved scope {}",
                    actionType,
                    requiredScope);
            return;
        }

        CompanionClientRuntime.getInstance().handleCosmicApiAction(message);
    }

    private void handlePongMessage(CosmicApiServerMessage message) {
        String sessionId = valueOrFallback(message.sessionId(), "");
        if (!sessionId.isBlank() && !activeSessionId.equals(sessionId)) {
            LOGGER.debug("Ignored Cosmic API pong for inactive session {}", sessionId);
            return;
        }

        long now = System.currentTimeMillis();
        long clientTimeMillis = longPayloadValue(message.payload(), "clientTimeMillis");
        if (clientTimeMillis <= 0L) {
            clientTimeMillis = longPayloadValue(message.payload(), "clientTime");
        }
        if (clientTimeMillis <= 0L) {
            clientTimeMillis = longPayloadValue(message.payload(), "sentAtMillis");
        }
        if (clientTimeMillis <= 0L) {
            clientTimeMillis = lastPingSentAtMillis;
        }

        lastPongReceivedAtMillis = now;
        lastServerTimeMillis = longPayloadValue(message.payload(), "serverTimeMillis");
        if (clientTimeMillis > 0L) {
            lastRoundTripMillis = Math.max(0L, now - clientTimeMillis);
        }
        LOGGER.debug("Cosmic API pong received, rtt={}ms", lastRoundTripMillis);
    }

    private void handleEventMessage(CosmicApiServerMessage message) {
        if (activeSessionId.isBlank()) {
            LOGGER.debug("Ignored Cosmic API hook event before session approval");
            return;
        }

        String sessionId = valueOrFallback(message.sessionId(), "");
        if (!sessionId.isBlank() && !activeSessionId.equals(sessionId)) {
            LOGGER.debug(
                    "Ignored Cosmic API hook event for inactive session {}",
                    valueOrFallback(sessionId, "unknown"));
            return;
        }

        String hookEvent = valueOrFallback(message.actionType(), "");
        if (hookEvent.isBlank()) {
            hookEvent = stringPayloadValue(message.payload(), "eventType");
        }
        if (hookEvent.isBlank()) {
            hookEvent = stringPayloadValue(message.payload(), "type");
        }
        if (!hasAllowedHook(hookEvent)) {
            LOGGER.debug("Ignored Cosmic API hook event without grant {}", hookEvent);
            return;
        }

        CompanionClientRuntime.getInstance().handleCosmicApiHookEvent(message);
    }

    private synchronized String resolveInstallId() {
        if (!cachedInstallId.isBlank()) {
            return cachedInstallId;
        }

        Path installIdPath =
                FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve(CosmicPrisonsMod.MOD_ID)
                        .resolve(INSTALL_ID_FILENAME);
        try {
            if (Files.isRegularFile(installIdPath)) {
                String existing = Files.readString(installIdPath, StandardCharsets.UTF_8).trim();
                if (isValidInstallId(existing)) {
                    cachedInstallId = existing;
                    return cachedInstallId;
                }
            }

            cachedInstallId = "ins_" + UUID.randomUUID().toString().replace("-", "");
            Files.createDirectories(installIdPath.getParent());
            Files.writeString(installIdPath, cachedInstallId, StandardCharsets.UTF_8);
            return cachedInstallId;
        } catch (IOException ex) {
            LOGGER.debug("Failed to persist Cosmic API install id: {}", ex.getMessage());
            cachedInstallId = "ins_" + UUID.randomUUID().toString().replace("-", "");
            return cachedInstallId;
        }
    }

    private void maybeSendClientPing(MinecraftClient client) {
        if (activeSessionId.isBlank() || client == null || client.player == null) {
            return;
        }
        if (pingCooldownTicks > 0) {
            pingCooldownTicks--;
            return;
        }

        long now = System.currentTimeMillis();
        try {
            ClientPlayNetworking.send(
                    new CosmicApiRawPayload(protocolCodec.encodeClientPing(activeSessionId, now)));
            lastPingSentAtMillis = now;
            pingCooldownTicks = PING_INTERVAL_TICKS;
            LOGGER.debug("Sent Cosmic API ping");
        } catch (RuntimeException ex) {
            LOGGER.debug("Failed to send Cosmic API ping: {}", ex.getMessage());
            pingCooldownTicks = PING_INTERVAL_TICKS;
        }
    }

    private static boolean isValidInstallId(String installId) {
        if (!installId.startsWith("ins_") || installId.length() > 80) {
            return false;
        }
        for (int index = 4; index < installId.length(); index++) {
            char character = installId.charAt(index);
            boolean valid =
                    (character >= 'a' && character <= 'z')
                            || (character >= 'A' && character <= 'Z')
                            || (character >= '0' && character <= '9')
                            || character == '_'
                            || character == '-';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static String resolveMinecraftVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map((container) -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(CosmicPrisonsMod.MOD_ID)
                .map((container) -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    private static String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> copyOrEmpty(List<String> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private static Map<String, Object> safePayload(Map<String, Object> payload) {
        return payload == null || payload.isEmpty() ? Map.of() : new LinkedHashMap<>(payload);
    }

    private static String stringPayloadValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null || !payload.containsKey(key)) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long longPayloadValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null || !payload.containsKey(key)) {
            return 0L;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
