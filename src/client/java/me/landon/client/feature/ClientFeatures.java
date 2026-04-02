package me.landon.client.feature;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import me.landon.companion.protocol.ProtocolConstants;

/**
 * Central catalog of client features.
 *
 * <p>To add a new feature, register a {@link ClientFeatureDefinition} constant and include it in
 * {@link #ALL}. The runtime and settings UI consume this catalog dynamically.
 */
public final class ClientFeatures {
    public static final String PEACEFUL_MINING_ID = "peaceful_mining";
    public static final ClientFeatureDefinition PEACEFUL_MINING =
            new ClientFeatureDefinition(
                    PEACEFUL_MINING_ID,
                    "text.cosmicprisonsmod.feature.peaceful_mining.name",
                    "text.cosmicprisonsmod.feature.peaceful_mining.icon",
                    "text.cosmicprisonsmod.feature.peaceful_mining.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_ENTITY_MARKERS));

    public static final String INVENTORY_ITEM_OVERLAYS_ID = "inventory_item_overlays";
    public static final ClientFeatureDefinition INVENTORY_ITEM_OVERLAYS =
            new ClientFeatureDefinition(
                    INVENTORY_ITEM_OVERLAYS_ID,
                    "text.cosmicprisonsmod.feature.inventory_item_overlays.name",
                    "text.cosmicprisonsmod.feature.inventory_item_overlays.icon",
                    "text.cosmicprisonsmod.feature.inventory_item_overlays.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS));

    public static final String HUD_COOLDOWNS_ID = "hud_cooldowns";
    public static final ClientFeatureDefinition HUD_COOLDOWNS =
            new ClientFeatureDefinition(
                    HUD_COOLDOWNS_ID,
                    "text.cosmicprisonsmod.feature.hud_cooldowns.name",
                    "text.cosmicprisonsmod.feature.hud_cooldowns.icon",
                    "text.cosmicprisonsmod.feature.hud_cooldowns.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS));

    public static final String HUD_PETS_ID = "hud_pets";
    public static final ClientFeatureDefinition HUD_PETS =
            new ClientFeatureDefinition(
                    HUD_PETS_ID,
                    "text.cosmicprisonsmod.feature.hud_pets.name",
                    "text.cosmicprisonsmod.feature.hud_pets.icon",
                    "text.cosmicprisonsmod.feature.hud_pets.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS));

    public static final String HUD_EVENTS_ID = "hud_events";
    public static final ClientFeatureDefinition HUD_EVENTS =
            new ClientFeatureDefinition(
                    HUD_EVENTS_ID,
                    "text.cosmicprisonsmod.feature.hud_events.name",
                    "text.cosmicprisonsmod.feature.hud_events.icon",
                    "text.cosmicprisonsmod.feature.hud_events.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS));

    public static final String HUD_SATCHEL_DISPLAY_ID = "hud_satchel_display";
    public static final ClientFeatureDefinition HUD_SATCHEL_DISPLAY =
            new ClientFeatureDefinition(
                    HUD_SATCHEL_DISPLAY_ID,
                    "text.cosmicprisonsmod.feature.hud_satchel_display.name",
                    "text.cosmicprisonsmod.feature.hud_satchel_display.icon",
                    "text.cosmicprisonsmod.feature.hud_satchel_display.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS));

    public static final String HUD_GANG_ID = "hud_gang";
    public static final ClientFeatureDefinition HUD_GANG =
            new ClientFeatureDefinition(
                    HUD_GANG_ID,
                    "text.cosmicprisonsmod.feature.hud_gang.name",
                    "text.cosmicprisonsmod.feature.hud_gang.icon",
                    "text.cosmicprisonsmod.feature.hud_gang.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS));

    public static final String HUD_LEADERBOARDS_ID = "hud_leaderboards";
    public static final ClientFeatureDefinition HUD_LEADERBOARDS =
            new ClientFeatureDefinition(
                    HUD_LEADERBOARDS_ID,
                    "text.cosmicprisonsmod.feature.hud_leaderboards.name",
                    "text.cosmicprisonsmod.feature.hud_leaderboards.icon",
                    "text.cosmicprisonsmod.feature.hud_leaderboards.description",
                    true,
                    OptionalInt.of(ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS));

    public static final String PINGS_ID = "pings";
    public static final ClientFeatureDefinition PINGS =
            new ClientFeatureDefinition(
                    PINGS_ID,
                    "text.cosmicprisonsmod.feature.pings.name",
                    "text.cosmicprisonsmod.feature.pings.icon",
                    "text.cosmicprisonsmod.feature.pings.description",
                    true,
                    OptionalInt.of(ProtocolConstants.FEATURE_GANG_TRUCE_PINGS));

    private static final List<ClientFeatureDefinition> ALL =
            List.of(
                    PEACEFUL_MINING,
                    INVENTORY_ITEM_OVERLAYS,
                    HUD_COOLDOWNS,
                    HUD_PETS,
                    HUD_EVENTS,
                    HUD_SATCHEL_DISPLAY,
                    HUD_GANG,
                    HUD_LEADERBOARDS,
                    PINGS);

    private ClientFeatures() {}

    /**
     * Returns all known feature definitions in deterministic UI/render order.
     *
     * @return Immutable feature list used by runtime and settings screens.
     */
    public static List<ClientFeatureDefinition> all() {
        return ALL;
    }

    /**
     * Resolves a feature by id.
     *
     * @param id Stable feature id from config or protocol-driven logic.
     * @return Matching feature definition, or empty when unknown.
     */
    public static Optional<ClientFeatureDefinition> findById(String id) {
        return ALL.stream().filter(feature -> feature.id().equals(id)).findFirst();
    }
}
