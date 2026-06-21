package me.landon.client.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.landon.client.feature.ClientFeatures;
import me.landon.cosmicapi.protocol.CosmicApiProtocolConstants;
import me.landon.cosmicapi.protocol.CosmicApiRuntimePayloads;
import me.landon.cosmicapi.protocol.CosmicApiServerMessage;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanionClientRuntimeCosmicApiActionTest {
    private CompanionClientRuntime runtime;
    private ConnectionSessionState session;

    @BeforeEach
    void setUp() throws Exception {
        runtime = CompanionClientRuntime.getInstance();
        session = session(runtime);
        runtime.onCosmicApiSessionCleared();
        runtime.onCosmicApiSessionResolved(
                "test", CosmicApiProtocolConstants.OFFICIAL_REQUESTED_SCOPES, List.of());
    }

    @Test
    void emptyHudWidgetSnapshotClearsExistingWidgets() throws Exception {
        session.replaceHudWidgets(
                List.of(
                        new CosmicApiRuntimePayloads.HudWidget(
                                "events", List.of("Meteor: 1m"), 0)));
        assertFalse(session.hudWidgetsSnapshot().isEmpty());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("widgets", List.of());

        assertTrue(applyAction("hud.widgets", payload));
        assertTrue(session.hudWidgetsSnapshot().isEmpty());
    }

    @Test
    void emptyInventoryOverlaySnapshotClearsExistingOverlays() throws Exception {
        session.replaceInventoryItemOverlays(
                List.of(new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "8K")));
        assertFalse(session.inventoryItemOverlaysSnapshot().isEmpty());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inventoryOverlays", List.of());

        assertTrue(applyAction("inventory.overlays", payload));
        assertTrue(session.inventoryItemOverlaysSnapshot().isEmpty());
    }

    @Test
    void hudWidgetUpdateAndRemoveKeepOtherWidgets() throws Exception {
        session.replaceHudWidgets(
                List.of(
                        new CosmicApiRuntimePayloads.HudWidget("events", List.of("Meteor"), 0),
                        new CosmicApiRuntimePayloads.HudWidget(
                                "cooldowns", List.of("Charge: 8s"), 0)));

        assertTrue(
                applyAction(
                        "hud.widget.set",
                        Map.of("widgetId", "events", "lines", List.of("Meteor: 2m"))));
        assertEquals(List.of("Meteor: 2m"), session.getHudWidget("events").lines());
        assertTrue(session.hudWidgetsSnapshot().containsKey("cooldowns"));

        assertTrue(applyAction("hud.widget.remove", Map.of("widgetId", "cooldowns")));
        assertFalse(session.hudWidgetsSnapshot().containsKey("cooldowns"));
        assertTrue(session.hudWidgetsSnapshot().containsKey("events"));
    }

    @Test
    void inventoryOverlayUpdateAndRemoveKeepOtherSlots() throws Exception {
        session.replaceInventoryItemOverlays(
                List.of(
                        new CosmicApiRuntimePayloads.InventoryItemOverlay(0, 1, "8K"),
                        new CosmicApiRuntimePayloads.InventoryItemOverlay(1, 2, "12K")));

        assertTrue(
                applyAction(
                        "inventory.overlay.set",
                        Map.of("slot", 0, "overlayType", "cosmic.energy", "displayText", "9K")));
        assertEquals("9K", session.getInventoryItemOverlay(0).displayText());
        assertTrue(session.inventoryItemOverlaysSnapshot().containsKey(1));

        assertTrue(applyAction("inventory.overlay.remove", Map.of("slot", 1)));
        assertFalse(session.inventoryItemOverlaysSnapshot().containsKey(1));
        assertTrue(session.inventoryItemOverlaysSnapshot().containsKey(0));
    }

    @Test
    void sameGangMarkerSnapshotUpdatesSessionState() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("markerType", CosmicApiProtocolConstants.MARKER_TYPE_SAME_GANG);
        payload.put("entityIds", List.of(22, 34));

        assertTrue(applyAction("entity.markers", payload));
        assertEquals(
                List.of(22, 34),
                session.sameGangEntityIdsSnapshot().intStream().sorted().boxed().toList());

        Map<String, Object> replacementPayload = new LinkedHashMap<>();
        replacementPayload.put("markerType", CosmicApiProtocolConstants.MARKER_TYPE_SAME_GANG);
        replacementPayload.put("entityIds", List.of(34));

        assertTrue(applyAction("entity.markers", replacementPayload));
        assertEquals(
                List.of(34),
                session.sameGangEntityIdsSnapshot().intStream().sorted().boxed().toList());
    }

    @Test
    void markerClearAllClearsEveryTrackedMarkerType() throws Exception {
        session.applySameGangEntityDelta(List.of(22), List.of());
        session.applyPeacefulMiningPassThroughDelta(List.of(33), List.of());
        session.applyGangPingBeaconDelta(List.of(44), List.of());
        session.applyTrucePingBeaconDelta(List.of(55), List.of());

        assertTrue(applyAction("markers.clear", Map.of("clearAll", true)));

        assertTrue(session.sameGangEntityIdsSnapshot().isEmpty());
        assertTrue(session.peacefulMiningPassThroughIdsSnapshot().isEmpty());
        assertTrue(session.gangPingBeaconIdsSnapshot().isEmpty());
        assertTrue(session.trucePingBeaconIdsSnapshot().isEmpty());
    }

    @Test
    void hookEventIsRecordedForDiagnostics() {
        CosmicApiServerMessage message =
                new CosmicApiServerMessage(
                        "event",
                        "hook",
                        "player.enchant_proc",
                        true,
                        "sess_api",
                        "",
                        "",
                        "",
                        "",
                        List.of(),
                        List.of("player.enchant_proc"),
                        Map.of(
                                "eventType",
                                "player.enchant_proc",
                                "payload",
                                Map.of("enchantId", "shatter")));

        runtime.handleCosmicApiHookEvent(message);

        assertEquals(1, runtime.cosmicApiHookEventCountsSnapshot().get("player.enchant_proc"));
        assertEquals(1, runtime.recentCosmicApiHookEventsSnapshot().size());
        assertEquals(
                "player.enchant_proc",
                runtime.recentCosmicApiHookEventsSnapshot().getFirst().eventType());
    }

    @Test
    void approvedScopesDriveFeatureSupport() {
        runtime.onCosmicApiSessionCleared();
        runtime.onCosmicApiSessionResolved("test", List.of("ui.hud:read"), List.of());

        assertTrue(runtime.isFeatureSupportedByServer(ClientFeatures.HUD_EVENTS_ID));
        assertFalse(runtime.isFeatureSupportedByServer(ClientFeatures.PINGS_ID));
        assertFalse(runtime.isFeatureSupportedByServer(ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID));
    }

    @Test
    void capabilitiesCannotElevateUnapprovedScopes() throws Exception {
        runtime.onCosmicApiSessionCleared();
        runtime.onCosmicApiSessionResolved("test", List.of("ui.hud:read"), List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("featureFlags", CosmicApiProtocolConstants.FEATURE_GANG_TRUCE_PINGS);
        payload.put("hudWidgets", true);
        payload.put("inventoryOverlays", true);

        assertTrue(applyAction("session.capabilities", payload));
        assertFalse(runtime.isFeatureSupportedByServer(ClientFeatures.PINGS_ID));
        assertFalse(runtime.isFeatureSupportedByServer(ClientFeatures.HUD_EVENTS_ID));
        assertFalse(runtime.isFeatureSupportedByServer(ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID));
    }

    @Test
    void cosmicApiRuntimeRoutesAllowedServerHookEvent() throws Exception {
        CosmicApiClientRuntime apiRuntime = CosmicApiClientRuntime.getInstance();
        setField(apiRuntime, "activeSessionId", "sess_api");
        setField(apiRuntime, "allowedHooks", List.of("player.enchant_proc"));
        CosmicApiServerMessage message =
                new CosmicApiServerMessage(
                        "event",
                        "hook",
                        "player.enchant_proc",
                        true,
                        "sess_api",
                        "",
                        "",
                        "",
                        "",
                        List.of(),
                        List.of(),
                        Map.of("eventType", "player.enchant_proc"));

        try {
            Method method =
                    CosmicApiClientRuntime.class.getDeclaredMethod(
                            "handleServerMessage", CosmicApiServerMessage.class);
            method.setAccessible(true);
            method.invoke(apiRuntime, message);
        } finally {
            setField(apiRuntime, "activeSessionId", "");
            setField(apiRuntime, "allowedHooks", List.of());
        }

        assertEquals(1, runtime.cosmicApiHookEventCountsSnapshot().get("player.enchant_proc"));
    }

    @Test
    void cosmicApiRuntimeIgnoresServerActionWithoutScopeGrant() throws Exception {
        CosmicApiClientRuntime apiRuntime = CosmicApiClientRuntime.getInstance();
        CosmicApiServerMessage message =
                new CosmicApiServerMessage(
                        "action",
                        "",
                        "hud.widgets",
                        true,
                        "sess_api",
                        "",
                        "",
                        "",
                        "",
                        List.of(),
                        List.of(),
                        Map.of(
                                "widgets",
                                List.of(Map.of("widgetId", "events", "lines", List.of("Meteor")))));
        Method method =
                CosmicApiClientRuntime.class.getDeclaredMethod(
                        "handleServerMessage", CosmicApiServerMessage.class);
        method.setAccessible(true);

        try {
            setField(apiRuntime, "activeSessionId", "sess_api");
            setField(apiRuntime, "allowedScopes", List.of());
            method.invoke(apiRuntime, message);
            assertTrue(session.hudWidgetsSnapshot().isEmpty());

            setField(apiRuntime, "allowedScopes", List.of("ui.hud:read"));
            method.invoke(apiRuntime, message);
            assertTrue(session.hudWidgetsSnapshot().containsKey("events"));
        } finally {
            setField(apiRuntime, "activeSessionId", "");
            setField(apiRuntime, "allowedScopes", List.of());
        }
    }

    @Test
    void cosmicApiRuntimeRecordsPongLatency() throws Exception {
        CosmicApiClientRuntime apiRuntime = CosmicApiClientRuntime.getInstance();
        long clientTimeMillis = System.currentTimeMillis() - 25L;
        CosmicApiServerMessage message =
                new CosmicApiServerMessage(
                        "pong",
                        "",
                        "",
                        true,
                        "sess_api",
                        "",
                        "",
                        "",
                        "",
                        List.of(),
                        List.of(),
                        Map.of("clientTimeMillis", clientTimeMillis, "serverTimeMillis", 456L));
        Method method =
                CosmicApiClientRuntime.class.getDeclaredMethod(
                        "handleServerMessage", CosmicApiServerMessage.class);
        method.setAccessible(true);

        try {
            setField(apiRuntime, "activeSessionId", "sess_api");
            method.invoke(apiRuntime, message);

            assertTrue(apiRuntime.lastPongReceivedAtMillis() > 0L);
            assertTrue(apiRuntime.lastRoundTripMillis() >= 0L);
            assertEquals(456L, apiRuntime.lastServerTimeMillis());
        } finally {
            setField(apiRuntime, "activeSessionId", "");
        }
    }

    @Test
    void waypointPayloadParsesNestedPositionAndLifetime() throws Exception {
        Map<String, Object> rawPayload =
                Map.of(
                        "waypoint",
                        Map.of("x", 12.5D, "y", 64D, "z", -42D, "label", "Quest Target"),
                        "ttlSeconds",
                        12);

        Map<String, Object> payload = waypointPayload(rawPayload);
        Vec3d position = waypointPosition(payload);

        assertEquals(12.5D, position.x, 0.001D);
        assertEquals(64D, position.y, 0.001D);
        assertEquals(-42D, position.z, 0.001D);
        assertEquals("Quest Target", payload.get("label"));
        assertEquals(12_000L, waypointLifetimeMillis(payload));
    }

    private boolean applyAction(String actionType, Map<String, Object> payload) throws Exception {
        Method method =
                CompanionClientRuntime.class.getDeclaredMethod(
                        "applyCosmicApiAction", String.class, Map.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(runtime, actionType, payload);
    }

    private static ConnectionSessionState session(CompanionClientRuntime runtime) throws Exception {
        Field field = CompanionClientRuntime.class.getDeclaredField("session");
        field.setAccessible(true);
        return (ConnectionSessionState) field.get(runtime);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> waypointPayload(Map<String, Object> rawPayload)
            throws Exception {
        Method method =
                CompanionClientRuntime.class.getDeclaredMethod("waypointPayload", Map.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(null, rawPayload);
    }

    private static Vec3d waypointPosition(Map<String, Object> payload) throws Exception {
        Method method =
                CompanionClientRuntime.class.getDeclaredMethod("waypointPosition", Map.class);
        method.setAccessible(true);
        return (Vec3d) method.invoke(null, payload);
    }

    private static long waypointLifetimeMillis(Map<String, Object> payload) throws Exception {
        Method method =
                CompanionClientRuntime.class.getDeclaredMethod("waypointLifetimeMillis", Map.class);
        method.setAccessible(true);
        return (Long) method.invoke(null, payload);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
