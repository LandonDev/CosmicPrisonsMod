package me.landon.client.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.landon.cosmicapi.protocol.CosmicApiRuntimePayloads;
import org.junit.jupiter.api.Test;

class ConnectionSessionStateTest {
    @Test
    void replacesEntireOverlaySnapshot() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(
                        new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "12K"),
                        new CosmicApiRuntimePayloads.InventoryItemOverlay(9, 2, "4.5M")));

        sessionState.replaceInventoryItemOverlays(
                List.of(new CosmicApiRuntimePayloads.InventoryItemOverlay(1, 1, "22K")));

        assertNull(sessionState.getInventoryItemOverlay(0));
        assertNull(sessionState.getInventoryItemOverlay(9));
        ConnectionSessionState.ItemOverlayEntry entry = sessionState.getInventoryItemOverlay(1);
        assertEquals(1, entry.overlayType());
        assertEquals("22K", entry.displayText());
    }

    @Test
    void emptySnapshotClearsOverlayMap() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "8K")));
        assertTrue(sessionState.inventoryItemOverlaysSnapshot().containsKey(0));

        sessionState.replaceInventoryItemOverlays(List.of());

        assertTrue(sessionState.inventoryItemOverlaysSnapshot().isEmpty());
    }

    @Test
    void upsertAndRemoveOverlayKeepsOtherSlots() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(
                        new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "8K"),
                        new CosmicApiRuntimePayloads.InventoryItemOverlay(1, 2, "12K")));

        sessionState.upsertInventoryItemOverlay(
                new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "9K"));
        sessionState.removeInventoryItemOverlay(1);

        assertEquals("9K", sessionState.getInventoryItemOverlay(0).displayText());
        assertNull(sessionState.getInventoryItemOverlay(1));
    }

    @Test
    void normalizesNmsHotbarSlots() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceInventoryItemOverlays(
                List.of(new CosmicApiRuntimePayloads.InventoryItemOverlay(36, 1, "9K")));

        ConnectionSessionState.ItemOverlayEntry entry = sessionState.getInventoryItemOverlay(0);
        assertNotNull(entry);
        assertEquals("9K", entry.displayText());
    }

    @Test
    void resetClearsOverlaysAndSupportFlag() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.setInventoryItemOverlaysSupported(true);
        sessionState.replaceInventoryItemOverlays(
                List.of(new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "8K")));

        sessionState.reset();

        assertTrue(sessionState.inventoryItemOverlaysSnapshot().isEmpty());
        assertFalse(sessionState.inventoryItemOverlaysSupported());
    }

    @Test
    void hudWidgetSnapshotReplacesPriorState() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceHudWidgets(
                List.of(
                        new CosmicApiRuntimePayloads.HudWidget("events", List.of("Meteor: 10m"), 0),
                        new CosmicApiRuntimePayloads.HudWidget(
                                "cooldowns", List.of("Gang Join: 2m"), 5)));

        sessionState.replaceHudWidgets(
                List.of(
                        new CosmicApiRuntimePayloads.HudWidget(
                                "satchels", List.of("Coal: 1K/2K x1"), 0)));

        assertNull(sessionState.getHudWidget("events"));
        assertNull(sessionState.getHudWidget("cooldowns"));
        ConnectionSessionState.HudWidgetEntry satchels = sessionState.getHudWidget("satchels");
        assertNotNull(satchels);
        assertEquals(List.of("Coal: 1K/2K x1"), satchels.lines());
    }

    @Test
    void resetClearsHudWidgetsAndSupportFlag() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.setHudWidgetsSupported(true);
        sessionState.replaceHudWidgets(
                List.of(
                        new CosmicApiRuntimePayloads.HudWidget(
                                "events", List.of("Meteor: 1m"), 1)));

        sessionState.reset();

        assertTrue(sessionState.hudWidgetsSnapshot().isEmpty());
        assertFalse(sessionState.hudWidgetsSupported());
    }

    @Test
    void upsertAndRemoveHudWidgetKeepsOtherWidgets() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.replaceHudWidgets(
                List.of(
                        new CosmicApiRuntimePayloads.HudWidget("events", List.of("Meteor"), 0),
                        new CosmicApiRuntimePayloads.HudWidget(
                                "cooldowns", List.of("Charge: 8s"), 0)));

        sessionState.upsertHudWidget(
                new CosmicApiRuntimePayloads.HudWidget("events", List.of("Meteor: 2m"), 0));
        sessionState.removeHudWidget("cooldowns");

        assertEquals(List.of("Meteor: 2m"), sessionState.getHudWidget("events").lines());
        assertNull(sessionState.getHudWidget("cooldowns"));
    }

    @Test
    void peacefulMiningDeltaAddsAndRemovesEntityIds() {
        ConnectionSessionState sessionState = new ConnectionSessionState();

        sessionState.applyPeacefulMiningPassThroughDelta(List.of(4, 9, 14), List.of());
        assertTrue(sessionState.isPeacefulMiningPassThroughEntity(4));
        assertTrue(sessionState.isPeacefulMiningPassThroughEntity(9));
        assertTrue(sessionState.isPeacefulMiningPassThroughEntity(14));

        sessionState.applyPeacefulMiningPassThroughDelta(List.of(), List.of(9, 44));
        assertFalse(sessionState.isPeacefulMiningPassThroughEntity(9));
        assertTrue(sessionState.isPeacefulMiningPassThroughEntity(14));
    }

    @Test
    void resetClearsPeacefulMiningEntityIds() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.applyPeacefulMiningPassThroughDelta(List.of(11), List.of());
        assertTrue(sessionState.isPeacefulMiningPassThroughEntity(11));

        sessionState.reset();

        assertFalse(sessionState.isPeacefulMiningPassThroughEntity(11));
        assertTrue(sessionState.peacefulMiningPassThroughIdsSnapshot().isEmpty());
    }

    @Test
    void sameGangDeltaAddsAndRemovesEntityIds() {
        ConnectionSessionState sessionState = new ConnectionSessionState();

        sessionState.applySameGangEntityDelta(List.of(3, 12, 19), List.of());
        assertTrue(sessionState.isSameGangEntity(3));
        assertTrue(sessionState.isSameGangEntity(12));
        assertTrue(sessionState.isSameGangEntity(19));

        sessionState.applySameGangEntityDelta(List.of(), List.of(12, 44));
        assertFalse(sessionState.isSameGangEntity(12));
        assertTrue(sessionState.isSameGangEntity(19));
    }

    @Test
    void resetClearsSameGangEntityIds() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.applySameGangEntityDelta(List.of(11), List.of());
        assertTrue(sessionState.isSameGangEntity(11));

        sessionState.reset();

        assertFalse(sessionState.isSameGangEntity(11));
        assertTrue(sessionState.sameGangEntityIdsSnapshot().isEmpty());
    }

    @Test
    void gangAndTrucePingDeltasTrackIndependentEntityIds() {
        ConnectionSessionState sessionState = new ConnectionSessionState();

        sessionState.applyGangPingBeaconDelta(List.of(7, 9), List.of());
        sessionState.applyTrucePingBeaconDelta(List.of(14, 15), List.of());
        assertEquals(
                List.of(7, 9),
                sessionState.gangPingBeaconIdsSnapshot().intStream().sorted().boxed().toList());
        assertEquals(
                List.of(14, 15),
                sessionState.trucePingBeaconIdsSnapshot().intStream().sorted().boxed().toList());

        sessionState.applyGangPingBeaconDelta(List.of(), List.of(7));
        sessionState.applyTrucePingBeaconDelta(List.of(), List.of(15));
        assertEquals(
                List.of(9),
                sessionState.gangPingBeaconIdsSnapshot().intStream().sorted().boxed().toList());
        assertEquals(
                List.of(14),
                sessionState.trucePingBeaconIdsSnapshot().intStream().sorted().boxed().toList());
    }

    @Test
    void resetClearsGangAndTrucePingBeaconIds() {
        ConnectionSessionState sessionState = new ConnectionSessionState();
        sessionState.applyGangPingBeaconDelta(List.of(1), List.of());
        sessionState.applyTrucePingBeaconDelta(List.of(2), List.of());

        sessionState.reset();

        assertTrue(sessionState.gangPingBeaconIdsSnapshot().isEmpty());
        assertTrue(sessionState.trucePingBeaconIdsSnapshot().isEmpty());
    }
}
