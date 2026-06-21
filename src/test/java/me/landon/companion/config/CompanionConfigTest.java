package me.landon.companion.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void sanitizeKeepsServerCompanionCustomHudWidgetLayout() {
        CompanionConfig config = CompanionConfig.defaults();
        config.hudWidgetPositions.put(
                CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID,
                new CompanionConfig.HudWidgetPosition(0.25D, 0.33D));
        config.hudWidgetScales.put(CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID, 0.88D);
        config.hudWidgetWidthMultipliers.put(
                CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID, 1.18D);

        config.sanitize();

        assertTrue(
                config.hudWidgetPositions.containsKey(
                        CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID));
        assertEquals(
                0.25D,
                config.hudWidgetPositions.get(CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID)
                        .x);
        assertEquals(
                0.88D,
                config.hudWidgetScales.get(CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID));
        assertEquals(
                1.18D,
                config.hudWidgetWidthMultipliers.get(
                        CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID));
    }
}
