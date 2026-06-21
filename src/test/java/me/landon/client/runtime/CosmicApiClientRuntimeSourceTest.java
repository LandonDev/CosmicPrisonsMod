package me.landon.client.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CosmicApiClientRuntimeSourceTest {
    @Test
    void officialHelloWaitsForServerReadyWindow() throws IOException {
        String source =
                Files.readString(
                        Path.of(
                                "src/client/java/me/landon/client/runtime/CosmicApiClientRuntime.java"));
        String joinHandler = methodBlock(source, "private synchronized void onJoin");
        String sendAttempt = methodBlock(source, "private void attemptSendClientHello");

        assertTrue(
                source.contains("INITIAL_HELLO_DELAY_TICKS"),
                "Cosmic API hello must keep an initial delay constant for the server READY window.");
        assertTrue(
                joinHandler.contains("helloRetryCooldownTicks = INITIAL_HELLO_DELAY_TICKS;"),
                "Cosmic API hello must not be sent immediately from JOIN.");
        assertTrue(
                sendAttempt.contains("ClientPlayNetworking.canSend(CosmicApiRawPayload.ID)"),
                "Cosmic API hello must wait until Fabric reports the play channel can send.");
    }

    private static String methodBlock(String source, String signature) {
        int method = source.indexOf(signature);
        assertTrue(method >= 0, signature + " must exist.");
        int bodyStart = source.indexOf('{', method);
        assertTrue(bodyStart >= 0, signature + " must have a body.");
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, index);
                }
            }
        }
        throw new AssertionError(signature + " body was not closed.");
    }
}
