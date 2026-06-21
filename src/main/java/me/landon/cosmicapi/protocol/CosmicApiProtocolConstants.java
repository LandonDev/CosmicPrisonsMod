package me.landon.cosmicapi.protocol;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.Identifier;

public final class CosmicApiProtocolConstants {
    public static final Identifier CHANNEL_ID = Identifier.of("cosmicapi", "main");
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PACKET_BYTES = 16 * 1024;
    public static final int MAX_WIDGET_LINES = 16;

    public static final String MESSAGE_TYPE_CLIENT_HELLO = "client_hello";
    public static final String OFFICIAL_CLIENT_ID = "client_mqmw37lx0g5rxqp952";
    public static final String OFFICIAL_MOD_ID = "cosmicprisons-official-mod";
    public static final String MOD_LOADER = "fabric";
    public static final int SERVER_FEATURE_HUD_WIDGETS = 1 << 0;
    public static final int SERVER_FEATURE_ENTITY_MARKERS = 1 << 1;
    public static final int SERVER_FEATURE_ASK_QUESTION = 1 << 2;
    public static final int SERVER_FEATURE_BROADCAST = 1 << 3;
    public static final int SERVER_FEATURE_PING_PONG = 1 << 4;
    public static final int SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS = 1 << 5;
    public static final int FEATURE_GANG_TRUCE_PINGS = 1 << 6;
    public static final int SERVER_FEATURE_GANG_TRUCE_PINGS = FEATURE_GANG_TRUCE_PINGS;
    public static final int PING_TYPE_GANG = 1;
    public static final int PING_TYPE_TRUCE = 2;
    public static final int MARKER_TYPE_SAME_GANG = 1;
    public static final int MARKER_TYPE_PEACEFUL_MINING_PASS_THROUGH = 2;
    public static final int MARKER_TYPE_GANG_PING_BEACON = 3;
    public static final int MARKER_TYPE_TRUCE_PING_BEACON = 4;
    public static final int OVERLAY_TYPE_COSMIC_ENERGY = 1;
    public static final int OVERLAY_TYPE_MONEY_NOTE = 2;
    public static final int OVERLAY_TYPE_GANG_POINT_NOTE = 3;
    public static final int OVERLAY_TYPE_SATCHEL_PERCENT = 4;
    public static final int OVERLAY_TYPE_PET_ACTIVE = 5;
    public static final int OVERLAY_TYPE_PET_COOLDOWN = 6;
    public static final List<String> OFFICIAL_REQUESTED_SCOPES =
            List.of(
                    "ui.hud:read",
                    "ui.markers:read",
                    "ui.prompt:write",
                    "player.inventory_overlays:read",
                    "events:read",
                    "leaderboards:read",
                    "server.merchants:read",
                    "server.meteors:read",
                    "server.guards:read",
                    "mod.sessions.same_mod:read",
                    "player.profile:read",
                    "player.cooldowns:read",
                    "player.effects:read",
                    "player.pets:read",
                    "player.trinkets:read",
                    "player.satchels:read",
                    "player.inventory:read",
                    "player.private_vaults:read",
                    "player.chat_channel:read",
                    "gang.profile:read",
                    "gang.members:read",
                    "gang.messages:read",
                    "gang.pings:read",
                    "gang.target:read",
                    "gang.relations:read",
                    "gang.bank:read",
                    "gang.messages:write",
                    "gang.ping:write",
                    "hooks.player.enchant_proc:read",
                    "hooks.player.absorber:read",
                    "hooks.player.command:read",
                    "hooks.bandit.kill:read");
    public static final List<String> OFFICIAL_REQUESTED_HOOKS =
            List.of(
                    "server.event.changed",
                    "server.event.schedule.changed",
                    "server.meteor.landing.changed",
                    "server.merchant.spawned",
                    "server.merchant.despawned",
                    "server.guards.snapshot.changed",
                    "player.enchant_proc",
                    "player.cooldowns.changed",
                    "player.effects.changed",
                    "player.absorber.used",
                    "player.command.succeeded",
                    "player.pet.changed",
                    "player.trinket.changed",
                    "player.chat_channel.changed",
                    "gang.chat.message.created",
                    "gang.ping.created",
                    "bandit.killed");

    public static int featureFlagsForScopes(List<String> scopes) {
        int featureFlags = SERVER_FEATURE_PING_PONG | SERVER_FEATURE_BROADCAST;
        if (hasScope(scopes, "ui.hud:read")) {
            featureFlags |= SERVER_FEATURE_HUD_WIDGETS;
        }
        if (hasScope(scopes, "ui.markers:read")) {
            featureFlags |= SERVER_FEATURE_ENTITY_MARKERS;
        }
        if (hasScope(scopes, "ui.prompt:write")) {
            featureFlags |= SERVER_FEATURE_ASK_QUESTION;
        }
        if (hasScope(scopes, "player.inventory_overlays:read")) {
            featureFlags |= SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS;
        }
        if (hasScope(scopes, "gang.ping:write")) {
            featureFlags |= FEATURE_GANG_TRUCE_PINGS;
        }
        return featureFlags;
    }

    public static String requiredScopeForServerAction(String actionType) {
        return switch (normalizeActionType(actionType)) {
            case "hud.widgets",
                            "hudwidgets",
                            "hud.widget.state",
                            "hud.widget",
                            "hud.widget.set",
                            "hud.widget.update",
                            "hud.widget.clear",
                            "hud.widget.remove",
                            "hud",
                            "widget",
                            "widget.set",
                            "widget.update",
                            "widget.clear",
                            "widget.remove",
                            "widgets",
                            "session.capabilities",
                            "capabilities" ->
                    "ui.hud:read";
            case "inventory.overlays",
                            "inventory.item.overlays",
                            "inventory.overlay",
                            "inventory.overlay.set",
                            "inventory.overlay.update",
                            "inventory.overlay.clear",
                            "inventory.overlay.remove",
                            "inventory.item.overlay",
                            "inventory.item.overlay.set",
                            "inventory.item.overlay.update",
                            "inventory.item.overlay.clear",
                            "inventory.item.overlay.remove",
                            "inventoryoverlays",
                            "inventoryitemoverlays",
                            "overlays" ->
                    "player.inventory_overlays:read";
            case "entity.markers",
                            "entitymarkers",
                            "entity.marker.delta",
                            "entity.markers.clear",
                            "entity.marker.clear",
                            "markers",
                            "markers.clear",
                            "markerdeltas",
                            "waypoint",
                            "waypoints",
                            "waypoint.set",
                            "waypoint.clear",
                            "waypoint.remove",
                            "ui.waypoint",
                            "ui.waypoint.set",
                            "ui.waypoint.clear",
                            "ui.waypoint.remove" ->
                    "ui.markers:read";
            case "question.ask", "questionask", "ask.question", "askquestion" -> "ui.prompt:write";
            default -> null;
        };
    }

    public static String requiredScopeForClientAction(String actionType) {
        return switch (normalizeActionType(actionType)) {
            case "ping.intent", "pingintent" -> "gang.ping:write";
            case "question.answer", "questionanswer" -> "ui.prompt:write";
            case "gang.message",
                            "gangmessage",
                            "gang.message.send",
                            "gangmessagesend",
                            "gang.messages.send",
                            "gang.chat.message",
                            "gang.chat.message.send" ->
                    "gang.messages:write";
            default -> null;
        };
    }

    public static boolean hasScope(List<String> scopes, String requiredScope) {
        if (requiredScope == null || requiredScope.isBlank()) {
            return true;
        }
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        String required = canonicalScope(requiredScope);
        for (String scope : scopes) {
            if (required.equals(canonicalScope(scope))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeActionType(String actionType) {
        return actionType == null
                ? ""
                : actionType.trim().toLowerCase(Locale.ROOT).replace('_', '.');
    }

    private static String normalizeScope(String scope) {
        return scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
    }

    private static String canonicalScope(String scope) {
        String normalized = normalizeScope(scope);
        return "gang.message:write".equals(normalized) ? "gang.messages:write" : normalized;
    }

    private CosmicApiProtocolConstants() {}
}
