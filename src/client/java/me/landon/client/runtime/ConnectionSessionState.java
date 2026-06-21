package me.landon.client.runtime;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.landon.cosmicapi.protocol.CosmicApiProtocolConstants;
import me.landon.cosmicapi.protocol.CosmicApiRuntimePayloads;

/**
 * Mutable per-connection client state populated from Cosmic API payloads.
 *
 * <p>This class is reset on disconnect and owns transient runtime data such as widget snapshots,
 * item overlays, marker ids, and session metadata.
 */
public final class ConnectionSessionState {
    /** Lightweight overlay snapshot for a single inventory slot. */
    public record ItemOverlayEntry(int overlayType, String displayText) {}

    /** Snapshot of one HUD widget payload as received from the server. */
    public record HudWidgetEntry(List<String> lines, int ttlSeconds, long receivedAtEpochMillis) {
        public HudWidgetEntry {
            lines = List.copyOf(lines);
        }
    }

    private final Map<Integer, ItemOverlayEntry> inventoryItemOverlays = new LinkedHashMap<>();
    private final Map<String, HudWidgetEntry> hudWidgets = new LinkedHashMap<>();
    private final IntSet peacefulMiningPassThroughIds = new IntOpenHashSet();
    private final IntSet sameGangEntityIds = new IntOpenHashSet();
    private final IntSet gangPingBeaconIds = new IntOpenHashSet();
    private final IntSet trucePingBeaconIds = new IntOpenHashSet();

    private String serverId = "";
    private String serverPluginVersion = "";
    private int serverFeatureFlags;

    private boolean enabled;
    private boolean hudWidgetsSupported;
    private boolean inventoryItemOverlaysSupported;

    /** Clears all handshake/session fields and payload snapshots for a new connection. */
    public void reset() {
        clearInventoryItemOverlays();
        clearHudWidgets();
        clearPeacefulMiningPassThroughIds();
        clearSameGangEntityIds();
        clearGangPingBeaconIds();
        clearTrucePingBeaconIds();
        serverId = "";
        serverPluginVersion = "";
        serverFeatureFlags = 0;
        enabled = false;
        hudWidgetsSupported = false;
        inventoryItemOverlaysSupported = false;
    }

    public void enableCosmicApiSession(
            String serverId, String serverPluginVersion, int featureFlags) {
        this.serverId = serverId == null ? "" : serverId;
        this.serverPluginVersion = serverPluginVersion == null ? "" : serverPluginVersion;
        this.serverFeatureFlags = featureFlags;
        this.enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String serverId() {
        return serverId;
    }

    public String serverPluginVersion() {
        return serverPluginVersion;
    }

    public int serverFeatureFlags() {
        return serverFeatureFlags;
    }

    public void setInventoryItemOverlaysSupported(boolean inventoryItemOverlaysSupported) {
        this.inventoryItemOverlaysSupported = inventoryItemOverlaysSupported;
    }

    public boolean inventoryItemOverlaysSupported() {
        return inventoryItemOverlaysSupported;
    }

    public void setHudWidgetsSupported(boolean hudWidgetsSupported) {
        this.hudWidgetsSupported = hudWidgetsSupported;
    }

    public boolean hudWidgetsSupported() {
        return hudWidgetsSupported;
    }

    /**
     * Replaces the full inventory overlay snapshot with normalized slot mappings.
     *
     * <p>Invalid slots are ignored.
     */
    public void replaceInventoryItemOverlays(
            List<CosmicApiRuntimePayloads.InventoryItemOverlay> overlays) {
        inventoryItemOverlays.clear();

        for (CosmicApiRuntimePayloads.InventoryItemOverlay overlay : overlays) {
            upsertInventoryItemOverlay(overlay);
        }
    }

    public void upsertInventoryItemOverlay(CosmicApiRuntimePayloads.InventoryItemOverlay overlay) {
        if (overlay == null) {
            return;
        }

        int normalizedSlot = normalizePlayerStorageSlot(overlay.slot());

        if (normalizedSlot < PLAYER_STORAGE_MIN_SLOT || normalizedSlot > PLAYER_STORAGE_MAX_SLOT) {
            return;
        }

        inventoryItemOverlays.put(
                normalizedSlot, new ItemOverlayEntry(overlay.overlayType(), overlay.displayText()));
    }

    public void removeInventoryItemOverlay(int slot) {
        int normalizedSlot = normalizePlayerStorageSlot(slot);

        if (normalizedSlot < PLAYER_STORAGE_MIN_SLOT || normalizedSlot > PLAYER_STORAGE_MAX_SLOT) {
            return;
        }

        inventoryItemOverlays.remove(normalizedSlot);
    }

    public void clearInventoryItemOverlays() {
        inventoryItemOverlays.clear();
    }

    /**
     * Replaces the full HUD widget snapshot.
     *
     * <p>Null widget ids and over-limit lines are discarded to preserve protocol bounds.
     */
    public void replaceHudWidgets(List<CosmicApiRuntimePayloads.HudWidget> widgets) {
        hudWidgets.clear();

        for (CosmicApiRuntimePayloads.HudWidget widget : widgets) {
            upsertHudWidget(widget);
        }
    }

    public void upsertHudWidget(CosmicApiRuntimePayloads.HudWidget widget) {
        if (widget == null) {
            return;
        }

        String widgetId = normalizeWidgetId(widget.widgetId());
        if (widgetId.isEmpty()) {
            return;
        }

        List<String> lines = new ArrayList<>(widget.lines().size());
        for (String line : widget.lines()) {
            lines.add(line == null ? "" : line);
            if (lines.size() >= CosmicApiProtocolConstants.MAX_WIDGET_LINES) {
                break;
            }
        }

        int ttlSeconds = Math.max(0, widget.ttlSeconds());
        hudWidgets.put(widgetId, new HudWidgetEntry(lines, ttlSeconds, System.currentTimeMillis()));
    }

    public void removeHudWidget(String widgetId) {
        String normalizedWidgetId = normalizeWidgetId(widgetId);
        if (normalizedWidgetId.isEmpty()) {
            return;
        }

        hudWidgets.remove(normalizedWidgetId);
    }

    public void clearHudWidgets() {
        hudWidgets.clear();
    }

    public HudWidgetEntry getHudWidget(String widgetId) {
        if (widgetId == null) {
            return null;
        }

        return hudWidgets.get(normalizeWidgetId(widgetId));
    }

    public Map<String, HudWidgetEntry> hudWidgetsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(hudWidgets));
    }

    /** Applies an add/remove delta for peaceful-mining pass-through entity markers. */
    public void applyPeacefulMiningPassThroughDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        for (int entityId : addEntityIds) {
            if (entityId < 0) {
                continue;
            }

            peacefulMiningPassThroughIds.add(entityId);
        }

        for (int entityId : removeEntityIds) {
            if (entityId < 0) {
                continue;
            }

            peacefulMiningPassThroughIds.remove(entityId);
        }
    }

    public boolean isPeacefulMiningPassThroughEntity(int entityId) {
        return entityId >= 0 && peacefulMiningPassThroughIds.contains(entityId);
    }

    public IntSet peacefulMiningPassThroughIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(peacefulMiningPassThroughIds));
    }

    public void clearPeacefulMiningPassThroughIds() {
        peacefulMiningPassThroughIds.clear();
    }

    /** Applies an add/remove delta for same-gang entity markers. */
    public void applySameGangEntityDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        applyEntityIdDelta(sameGangEntityIds, addEntityIds, removeEntityIds);
    }

    public boolean isSameGangEntity(int entityId) {
        return entityId >= 0 && sameGangEntityIds.contains(entityId);
    }

    public IntSet sameGangEntityIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(sameGangEntityIds));
    }

    public void clearSameGangEntityIds() {
        sameGangEntityIds.clear();
    }

    /** Applies an add/remove delta for gang ping beacon entities. */
    public void applyGangPingBeaconDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        applyEntityIdDelta(gangPingBeaconIds, addEntityIds, removeEntityIds);
    }

    /** Applies an add/remove delta for truce ping beacon entities. */
    public void applyTrucePingBeaconDelta(
            List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        applyEntityIdDelta(trucePingBeaconIds, addEntityIds, removeEntityIds);
    }

    public IntSet gangPingBeaconIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(gangPingBeaconIds));
    }

    public IntSet trucePingBeaconIdsSnapshot() {
        return IntSets.unmodifiable(new IntOpenHashSet(trucePingBeaconIds));
    }

    public void clearGangPingBeaconIds() {
        gangPingBeaconIds.clear();
    }

    public void clearTrucePingBeaconIds() {
        trucePingBeaconIds.clear();
    }

    public ItemOverlayEntry getInventoryItemOverlay(int slot) {
        return inventoryItemOverlays.get(slot);
    }

    public Map<Integer, ItemOverlayEntry> inventoryItemOverlaysSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(inventoryItemOverlays));
    }

    /**
     * Normalizes slot indexes into player storage space (0-35).
     *
     * <p>Supports legacy server payloads that send NMS hotbar slots (36-44).
     */
    private static int normalizePlayerStorageSlot(int rawSlot) {
        if (rawSlot >= PLAYER_STORAGE_MIN_SLOT && rawSlot <= PLAYER_STORAGE_MAX_SLOT) {
            return rawSlot;
        }

        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }

        return -1;
    }

    private static String normalizeWidgetId(String widgetId) {
        if (widgetId == null) {
            return "";
        }

        return widgetId.trim().toLowerCase(Locale.ROOT);
    }

    private static void applyEntityIdDelta(
            IntSet target, List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        for (int entityId : addEntityIds) {
            if (entityId < 0) {
                continue;
            }

            target.add(entityId);
        }

        for (int entityId : removeEntityIds) {
            if (entityId < 0) {
                continue;
            }

            target.remove(entityId);
        }
    }

    private static final int PLAYER_STORAGE_MIN_SLOT = 0;
    private static final int PLAYER_STORAGE_MAX_SLOT = 35;
}
