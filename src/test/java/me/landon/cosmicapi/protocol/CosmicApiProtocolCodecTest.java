package me.landon.cosmicapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CosmicApiProtocolCodecTest {
    private final CosmicApiProtocolCodec codec = new CosmicApiProtocolCodec();

    @Test
    void officialHelloIncludesRegistryIdentityAndScopes() {
        CosmicApiClientHello hello = CosmicApiClientHello.official("ins_test", "1.21.11", "1.3.0");

        String json = new String(codec.encodeClientHello(hello), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"type\":\"client_hello\""));
        assertTrue(json.contains("\"protocolVersion\":1"));
        assertTrue(json.contains("\"clientId\":\"cosmic_official\""));
        assertTrue(json.contains("\"modId\":\"cosmicprisonsmod\""));
        assertTrue(json.contains("\"modLoader\":\"fabric\""));
        assertTrue(json.contains("\"requestedScopes\":[\"events:read\""));
        assertTrue(json.contains("\"leaderboards:read\""));
        assertTrue(json.contains("\"player.cooldowns:read\""));
        assertTrue(json.contains("\"requestedHooks\":[\"server.event.changed\""));
        assertTrue(json.contains("\"player.command.succeeded\""));
    }

    @Test
    void clientHelloJsonEscapesStringValues() {
        CosmicApiClientHello hello =
                new CosmicApiClientHello(
                        "cosmic_official",
                        "cosmicprisonsmod",
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

        CosmicApiServerMessage message = codec.decodeServerMessage(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("resolve", message.type());
        assertEquals("session_resolved", message.event());
        assertTrue(message.allowed());
        assertEquals("sess_api", message.sessionId());
        assertEquals(List.of("events:read", "player.profile:read"), message.allowedScopes());
        assertEquals(List.of("server.event.changed"), message.allowedHooks());
    }
}
