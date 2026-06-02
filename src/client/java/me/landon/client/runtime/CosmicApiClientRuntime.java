package me.landon.client.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import me.landon.CosmicPrisonsMod;
import me.landon.cosmicapi.network.CosmicApiRawPayload;
import me.landon.cosmicapi.protocol.CosmicApiClientHello;
import me.landon.cosmicapi.protocol.CosmicApiProtocolCodec;
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
    private static final int HELLO_RETRY_INTERVAL_TICKS = 40;
    private static final int MAX_HELLO_ATTEMPTS = 20;
    private static final String INSTALL_ID_FILENAME = "cosmic-api-install-id.txt";

    private final CosmicApiProtocolCodec protocolCodec = new CosmicApiProtocolCodec();

    private boolean initialized;
    private boolean helloSent;
    private boolean channelUnavailableLogged;
    private int helloRetriesRemaining = MAX_HELLO_ATTEMPTS;
    private int helloRetryCooldownTicks;
    private String cachedInstallId = "";
    private String activeSessionId = "";
    private List<String> allowedScopes = List.of();
    private List<String> allowedHooks = List.of();

    private CosmicApiClientRuntime() {}

    public static CosmicApiClientRuntime getInstance() {
        return INSTANCE;
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
    }

    private synchronized void onJoin(MinecraftClient client) {
        resetConnectionState();
        attemptSendClientHello(client);
    }

    private synchronized void onDisconnect() {
        resetConnectionState();
    }

    private synchronized void onEndTick(MinecraftClient client) {
        if (helloRetryCooldownTicks > 0) {
            helloRetryCooldownTicks--;
            return;
        }
        attemptSendClientHello(client);
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

        switch (message.type()) {
            case "grant_required" -> {
                activeSessionId = "";
                allowedScopes = List.of();
                allowedHooks = List.of();
                LOGGER.info(
                        "Cosmic API access approval requested for {} ({})",
                        valueOrFallback(message.appName(), "registered mod"),
                        message.requestId());
            }
            case "resolve" -> handleResolveMessage(message);
            case "action" ->
                    LOGGER.debug(
                            "Received Cosmic API action for session {}",
                            valueOrFallback(message.sessionId(), "unknown"));
            default -> LOGGER.debug("Received Cosmic API payload type {}", message.type());
        }
    }

    private void attemptSendClientHello(MinecraftClient client) {
        if (helloSent || client.player == null || helloRetriesRemaining <= 0) {
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
            helloSent = true;
            LOGGER.info("Cosmic API official client hello sent");
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to send Cosmic API official client hello", ex);
            if (helloRetriesRemaining > 0) {
                helloRetryCooldownTicks = HELLO_RETRY_INTERVAL_TICKS;
            }
        }
    }

    private void resetConnectionState() {
        helloSent = false;
        channelUnavailableLogged = false;
        helloRetriesRemaining = MAX_HELLO_ATTEMPTS;
        helloRetryCooldownTicks = 0;
        activeSessionId = "";
        allowedScopes = List.of();
        allowedHooks = List.of();
    }

    private void handleResolveMessage(CosmicApiServerMessage message) {
        if (message.allowed()) {
            activeSessionId = message.sessionId();
            allowedScopes = List.copyOf(message.allowedScopes());
            allowedHooks = List.copyOf(message.allowedHooks());
            LOGGER.info(
                    "Cosmic API session approved with {} scope(s) and {} hook(s)",
                    allowedScopes.size(),
                    allowedHooks.size());
            return;
        }

        activeSessionId = "";
        allowedScopes = List.of();
        allowedHooks = List.of();
        LOGGER.info(
                "Cosmic API session denied: {}",
                valueOrFallback(message.reason(), "access_denied"));
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
}
