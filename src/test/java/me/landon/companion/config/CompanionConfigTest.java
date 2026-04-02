package me.landon.companion.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompanionConfigTest {
    @Test
    void defaultsUseFollowingPingMode() {
        CompanionConfig config = CompanionConfig.defaults();

        assertEquals(CompanionConfig.PING_VISUAL_MODE_FOLLOW, config.pingVisualMode);
        assertEquals(15, CompanionConfig.PING_VISUAL_DURATION_SECONDS_MAX);
    }

    @Test
    void sanitizeNormalizesPingVisualSettings() {
        CompanionConfig config = CompanionConfig.defaults();
        config.pingVisualMode = " STATIC ";
        config.pingVisualDurationSeconds = 99;

        config.sanitize();

        assertEquals(CompanionConfig.PING_VISUAL_MODE_STATIC, config.pingVisualMode);
        assertEquals(
                CompanionConfig.PING_VISUAL_DURATION_SECONDS_MAX, config.pingVisualDurationSeconds);
    }

    @Test
    void sanitizeFallsBackToFollowingForUnknownPingMode() {
        CompanionConfig config = CompanionConfig.defaults();
        config.pingVisualMode = "orbit";

        config.sanitize();

        assertEquals(CompanionConfig.PING_VISUAL_MODE_FOLLOW, config.pingVisualMode);
    }
}
