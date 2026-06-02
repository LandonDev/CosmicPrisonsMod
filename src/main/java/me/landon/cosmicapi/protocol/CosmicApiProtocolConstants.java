package me.landon.cosmicapi.protocol;

import java.util.List;
import net.minecraft.util.Identifier;

public final class CosmicApiProtocolConstants {
    public static final Identifier CHANNEL_ID = Identifier.of("cosmicapi", "main");
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PACKET_BYTES = 16 * 1024;

    public static final String MESSAGE_TYPE_CLIENT_HELLO = "client_hello";
    public static final String OFFICIAL_CLIENT_ID = "cosmic_official";
    public static final String OFFICIAL_MOD_ID = "cosmicprisonsmod";
    public static final String MOD_LOADER = "fabric";
    public static final List<String> OFFICIAL_REQUESTED_SCOPES =
            List.of(
                    "events:read",
                    "leaderboards:read",
                    "player.profile:read",
                    "gang.profile:read",
                    "player.cooldowns:read");
    public static final List<String> OFFICIAL_REQUESTED_HOOKS =
            List.of(
                    "server.event.changed",
                    "player.enchant_proc",
                    "player.absorber.used",
                    "player.command.succeeded",
                    "bandit.killed");

    private CosmicApiProtocolConstants() {}
}
