package me.landon.client.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.landon.client.feature.ClientFeatures;
import me.landon.companion.config.CompanionConfig;
import org.junit.jupiter.api.Test;

class HudWidgetCatalogTest {
    @Test
    void sortsEventsClosestFirstAndHandlesStatusTextSafely() {
        Map<String, Boolean> visibility = new LinkedHashMap<>();
        visibility.put("meteor", true);
        visibility.put("next_reboot", true);
        visibility.put("next_level_cap_day_unlock", true);

        List<HudWidgetCatalog.ParsedLine> sorted =
                HudWidgetCatalog.sortEventsClosestFirst(
                        List.of(
                                "Next Reboot: Not Scheduled",
                                "Meteor: 14m",
                                "Next Level Cap Day Unlock: Max Day"),
                        visibility);

        assertEquals("Meteor", sorted.get(0).label());
        assertEquals("Next Reboot", sorted.get(1).label());
        assertEquals("Next Level Cap Day Unlock", sorted.get(2).label());
    }

    @Test
    void canHideConfiguredEventLines() {
        Map<String, Boolean> visibility = new LinkedHashMap<>();
        visibility.put("meteor", false);
        visibility.put("next_reboot", true);

        List<HudWidgetCatalog.ParsedLine> sorted =
                HudWidgetCatalog.sortEventsClosestFirst(
                        List.of("Meteor: 5m", "Next Reboot: 20m"), visibility);

        assertEquals(1, sorted.size());
        assertEquals("Next Reboot", sorted.getFirst().label());
    }

    @Test
    void cooldownLineActivityFilterKeepsOnlyActiveEntries() {
        assertTrue(HudWidgetCatalog.isCooldownLineActive("Gang Join: 2m 1s"));
        assertFalse(HudWidgetCatalog.isCooldownLineActive("Gang Join: Now"));
        assertFalse(HudWidgetCatalog.isCooldownLineActive("Gang Join: 0s"));
    }

    @Test
    void catalogIncludesServerCompanionCustomWidgets() {
        assertEquals(
                ClientFeatures.SERVER_WIDGETS_ID,
                HudWidgetCatalog.findWidget(CompanionConfig.HUD_WIDGET_COMP_SCOREBOARD_ID)
                        .orElseThrow()
                        .featureId());
        assertTrue(
                HudWidgetCatalog.findWidget(CompanionConfig.HUD_WIDGET_PRISONBREAK_SCOREBOARD_ID)
                        .isPresent());
        assertTrue(
                HudWidgetCatalog.findWidget(CompanionConfig.HUD_WIDGET_PRISONBREAK_COOLDOWNS_ID)
                        .isPresent());
        assertTrue(
                HudWidgetCatalog.findWidget(CompanionConfig.HUD_WIDGET_PRISONBREAK_PROCS_ID)
                        .isPresent());
    }
}
