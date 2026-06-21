package me.landon.cosmicapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CosmicApiProtocolCodecTest {
    private final CosmicApiProtocolCodec codec = new CosmicApiProtocolCodec();

    @Test
    void officialHelloIncludesRegistryIdentityAndScopes() {
        CosmicApiClientHello hello = CosmicApiClientHello.official("ins_test", "1.21.11", "1.3.0");

        String json = new String(codec.encodeClientHello(hello), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"type\":\"client_hello\""));
        assertTrue(json.contains("\"protocolVersion\":1"));
        assertTrue(json.contains("\"clientId\":\"client_mqmw37lx0g5rxqp952\""));
        assertTrue(json.contains("\"modId\":\"cosmicprisons-official-mod\""));
        assertTrue(json.contains("\"modLoader\":\"fabric\""));
        assertTrue(json.contains("\"requestedScopes\":[\"ui.hud:read\""));
        assertTrue(json.contains("\"ui.markers:read\""));
        assertTrue(json.contains("\"player.inventory_overlays:read\""));
        assertTrue(json.contains("\"leaderboards:read\""));
        assertTrue(json.contains("\"server.merchants:read\""));
        assertTrue(json.contains("\"server.meteors:read\""));
        assertTrue(json.contains("\"server.guards:read\""));
        assertTrue(json.contains("\"mod.sessions.same_mod:read\""));
        assertTrue(json.contains("\"player.cooldowns:read\""));
        assertTrue(json.contains("\"player.effects:read\""));
        assertTrue(json.contains("\"player.trinkets:read\""));
        assertTrue(json.contains("\"player.chat_channel:read\""));
        assertTrue(json.contains("\"gang.messages:read\""));
        assertTrue(json.contains("\"gang.pings:read\""));
        assertTrue(json.contains("\"gang.messages:write\""));
        assertTrue(json.contains("\"gang.ping:write\""));
        assertTrue(json.contains("\"hooks.player.command:read\""));
        assertTrue(json.contains("\"requestedHooks\":[\"server.event.changed\""));
        assertTrue(json.contains("\"server.event.schedule.changed\""));
        assertTrue(json.contains("\"server.meteor.landing.changed\""));
        assertTrue(json.contains("\"server.merchant.spawned\""));
        assertTrue(json.contains("\"server.merchant.despawned\""));
        assertTrue(json.contains("\"server.guards.snapshot.changed\""));
        assertTrue(json.contains("\"player.cooldowns.changed\""));
        assertTrue(json.contains("\"player.effects.changed\""));
        assertTrue(json.contains("\"player.command.succeeded\""));
        assertTrue(json.contains("\"player.pet.changed\""));
        assertTrue(json.contains("\"player.trinket.changed\""));
        assertTrue(json.contains("\"player.chat_channel.changed\""));
        assertTrue(json.contains("\"gang.chat.message.created\""));
        assertTrue(json.contains("\"gang.ping.created\""));
    }

    @Test
    void clientHelloJsonEscapesStringValues() {
        CosmicApiClientHello hello =
                new CosmicApiClientHello(
                        "client_mqmw37lx0g5rxqp952",
                        "cosmicprisons-official-mod",
                        "ins_quoted",
                        "fabric",
                        "1.21.11",
                        "1.3.0\"test",
                        List.of("events:read"),
                        List.of("server.event.changed"));

        String json = new String(codec.encodeClientHello(hello), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"modVersion\":\"1.3.0\\\"test\""));
    }

    @Test
    void decodesServerResolvePayload() {
        String json =
                """
                {"type":"resolve","event":"session_resolved","allowed":true,"sessionId":"sess_api","reason":"ok","allowedScopes":["events:read","player.profile:read"],"allowedHooks":["server.event.changed"]}
                """;

        CosmicApiServerMessage message =
                codec.decodeServerMessage(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("resolve", message.type());
        assertEquals("session_resolved", message.event());
        assertTrue(message.allowed());
        assertEquals("sess_api", message.sessionId());
        assertEquals(List.of("events:read", "player.profile:read"), message.allowedScopes());
        assertEquals(List.of("server.event.changed"), message.allowedHooks());
    }

    @Test
    void clientActionIncludesSessionActionTypeAndPayload() {
        String json =
                new String(
                        codec.encodeClientAction(
                                "sess_api",
                                "ping.intent",
                                Map.of("pingType", "gang", "legacyPingType", 1)),
                        StandardCharsets.UTF_8);

        assertTrue(json.contains("\"type\":\"action\""));
        assertTrue(json.contains("\"sessionId\":\"sess_api\""));
        assertTrue(json.contains("\"actionType\":\"ping.intent\""));
        assertTrue(json.contains("\"pingType\":\"gang\""));
        assertTrue(json.contains("\"legacyPingType\":1"));
    }

    @Test
    void gangMessageClientActionRequiresWriteScope() {
        assertEquals(
                "gang.messages:write",
                CosmicApiProtocolConstants.requiredScopeForClientAction("gang.message.send"));
        assertTrue(
                CosmicApiProtocolConstants.hasScope(
                        List.of("gang.message:write"), "gang.messages:write"));
    }

    @Test
    void waypointServerActionsUseMarkerReadScope() {
        assertEquals(
                "ui.markers:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction("waypoint.set"));
        assertEquals(
                "ui.markers:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction("waypoint.clear"));
    }

    @Test
    void singleEntryServerActionsUseExistingFeatureScopes() {
        assertEquals(
                "ui.hud:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction("hud.widget.set"));
        assertEquals(
                "ui.hud:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction("hud.widget.remove"));
        assertEquals(
                "player.inventory_overlays:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction("inventory.overlay.set"));
        assertEquals(
                "player.inventory_overlays:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction(
                        "inventory.overlay.remove"));
        assertEquals(
                "ui.markers:read",
                CosmicApiProtocolConstants.requiredScopeForServerAction("markers.clear"));
    }

    @Test
    void clientEventIncludesHookEventType() {
        String json =
                new String(
                        codec.encodeClientEvent(
                                "sess_api", "player.command.succeeded", Map.of("command", "spawn")),
                        StandardCharsets.UTF_8);

        assertTrue(json.contains("\"type\":\"event\""));
        assertTrue(json.contains("\"sessionId\":\"sess_api\""));
        assertTrue(json.contains("\"eventType\":\"player.command.succeeded\""));
        assertTrue(json.contains("\"command\":\"spawn\""));
    }

    @Test
    void clientPingIncludesSessionAndClientTime() {
        String json = new String(codec.encodeClientPing("sess_api", 123L), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"type\":\"ping\""));
        assertTrue(json.contains("\"sessionId\":\"sess_api\""));
        assertTrue(json.contains("\"clientTimeMillis\":123"));
    }

    @Test
    void decodesServerActionPayload() {
        String json =
                """
                {"type":"action","sessionId":"sess_api","actionType":"question.ask","payload":{"questionId":12,"questionText":"Name?","maxAnswerChars":24}}
                """;

        CosmicApiServerMessage message =
                codec.decodeServerMessage(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("action", message.type());
        assertEquals("sess_api", message.sessionId());
        assertEquals("question.ask", message.actionType());
        assertEquals(12.0, message.payload().get("questionId"));
        assertEquals("Name?", message.payload().get("questionText"));
        assertEquals(24.0, message.payload().get("maxAnswerChars"));
    }

    @Test
    void decodesServerPongPayload() {
        String json =
                """
                {"type":"pong","sessionId":"sess_api","payload":{"clientTimeMillis":123,"serverTimeMillis":456}}
                """;

        CosmicApiServerMessage message =
                codec.decodeServerMessage(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("pong", message.type());
        assertEquals("sess_api", message.sessionId());
        assertEquals(123.0, message.payload().get("clientTimeMillis"));
        assertEquals(456.0, message.payload().get("serverTimeMillis"));
    }

    @Test
    void decodesServerHookEventPayload() {
        String json =
                """
                {"type":"event","event":"hook","sessionId":"sess_api","eventType":"player.enchant_proc","payload":{"eventType":"player.enchant_proc","payload":{"enchantId":"shatter"}}}
                """;

        CosmicApiServerMessage message =
                codec.decodeServerMessage(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("event", message.type());
        assertEquals("hook", message.event());
        assertEquals("sess_api", message.sessionId());
        assertEquals("player.enchant_proc", message.actionType());
        assertEquals("player.enchant_proc", message.payload().get("eventType"));
        assertTrue(message.payload().containsKey("payload"));
    }
}
