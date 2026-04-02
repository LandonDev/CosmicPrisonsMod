package me.landon.client.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.landon.client.feature.ClientFeatures;
import me.landon.companion.config.CompanionConfig;

/**
 * Static catalog and parsing helpers for server-driven HUD widgets/events.
 *
 * <p>Use this class as the single source of widget ids, preview metadata, and line parsing rules
 * when implementing new HUD feature behavior.
 */
public final class HudWidgetCatalog {
    /** Metadata describing one HUD widget family. */
    public record WidgetDescriptor(
            String widgetId,
            String featureId,
            String titleTranslationKey,
            int accentColor,
            List<String> previewLines) {}

    /** Metadata describing a known event key/label pair. */
    public record EventDescriptor(String key, String label, String iconTag) {}

    /** Parsed representation of a common "Label: Value" status line. */
    public record ParsedLine(String label, String value) {
        /** Rebuilds this parsed line as display text. */
        public String asText() {
            if (value.isEmpty()) {
                return label;
            }
            return label + ": " + value;
        }
    }

    private static final Pattern DURATION_PART_PATTERN =
            Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);

    private static final WidgetDescriptor COOLDOWNS =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_COOLDOWNS_ID,
                    ClientFeatures.HUD_COOLDOWNS_ID,
                    "text.cosmicprisonsmod.hud.cooldowns.title",
                    0x4EA7FF,
                    List.of(
                            "Gang/Truce Ping: 15s",
                            "Gang Join: 3m 14s",
                            "Rank Kit Overlord: 1h 20m"));
    private static final WidgetDescriptor PETS =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_PETS_ID,
                    ClientFeatures.HUD_PETS_ID,
                    "text.cosmicprisonsmod.hud.pets.title",
                    0x89D56B,
                    List.of(
                            "bandit_king|Bandit King Pet|5|1|42|180|600",
                            "lucky|Lucky Pet|7|2|88|0|0",
                            "signal_jammer|Signal Jammer Pet|3|1|16|0|85"));
    private static final WidgetDescriptor EVENTS =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_EVENTS_ID,
                    ClientFeatures.HUD_EVENTS_ID,
                    "text.cosmicprisonsmod.hud.events.title",
                    0xF1B95E,
                    List.of(
                            "Meteor: 17m",
                            "KOTH: 45m",
                            "Merchant: 1h 4m",
                            "Next Reboot: Not Scheduled",
                            "Next Level Cap Day Unlock: Max Day"));
    private static final WidgetDescriptor SATCHELS =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_SATCHELS_ID,
                    ClientFeatures.HUD_SATCHEL_DISPLAY_ID,
                    "text.cosmicprisonsmod.hud.satchels.title",
                    0x6CE39B,
                    List.of("Coal: 412K/1.2M x2", "Gold: 84K/400K x1"));
    private static final WidgetDescriptor GANG =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_GANG_ID,
                    ClientFeatures.HUD_GANG_ID,
                    "text.cosmicprisonsmod.hud.gang.title",
                    0xFF8E63,
                    List.of(
                            "Astral Gang",
                            "Online (5):",
                            "[LDR] Landon (1h 12m)",
                            "[MOD] Nova (43m)",
                            "Offline (7):",
                            "[MEM] Helix",
                            "Points: 1.4B | Bank: 320M",
                            "Members: 12 | Truces: 4",
                            "Target: Cell 27"));
    private static final WidgetDescriptor LEADERBOARD_GIFT =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_LEADERBOARD_GIFT_ID,
                    ClientFeatures.HUD_LEADERBOARDS_ID,
                    "text.cosmicprisonsmod.hud.leaderboard_gift.title",
                    0xFFC97B,
                    List.of(
                            "Gift Top",
                            "#1 Landon - 24",
                            "#2 Nova - 21",
                            "#3 Helix - 18",
                            "#4 Orion - 15"));
    private static final WidgetDescriptor LEADERBOARD_GANG =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_LEADERBOARD_GANG_ID,
                    ClientFeatures.HUD_LEADERBOARDS_ID,
                    "text.cosmicprisonsmod.hud.leaderboard_gang.title",
                    0xFF84A3FF,
                    List.of(
                            "Gang Top",
                            "#1 Astral - 9.2B",
                            "#2 Nexus - 8.7B",
                            "#3 Ember - 8.1B",
                            "#4 Vanta - 7.9B"));
    private static final WidgetDescriptor LEADERBOARD_BLOCKS =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_LEADERBOARD_BLOCKS_ID,
                    ClientFeatures.HUD_LEADERBOARDS_ID,
                    "text.cosmicprisonsmod.hud.leaderboard_blocks.title",
                    0xFF7FD8A7,
                    List.of(
                            "Blocks Top",
                            "#1 Landon - 2.1M",
                            "#2 Nova - 1.9M",
                            "#3 Helix - 1.8M",
                            "#4 Orion - 1.7M"));
    private static final WidgetDescriptor LEADERBOARD_LEVEL =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_LEADERBOARD_LEVEL_ID,
                    ClientFeatures.HUD_LEADERBOARDS_ID,
                    "text.cosmicprisonsmod.hud.leaderboard_level.title",
                    0xFFBC9DFF,
                    List.of(
                            "Level Top",
                            "#1 Landon - 112",
                            "#2 Nova - 110",
                            "#3 Helix - 108",
                            "#4 Orion - 107"));
    private static final WidgetDescriptor LEADERBOARD_CYCLE =
            new WidgetDescriptor(
                    CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID,
                    ClientFeatures.HUD_LEADERBOARDS_ID,
                    "text.cosmicprisonsmod.hud.leaderboard_cycle.title",
                    0xFF9AB1D9,
                    LEADERBOARD_GIFT.previewLines());

    private static final List<WidgetDescriptor> LEADERBOARDS =
            List.of(LEADERBOARD_GIFT, LEADERBOARD_GANG, LEADERBOARD_BLOCKS, LEADERBOARD_LEVEL);

    private static final List<WidgetDescriptor> WIDGETS =
            List.of(
                    COOLDOWNS,
                    PETS,
                    EVENTS,
                    SATCHELS,
                    GANG,
                    LEADERBOARD_GIFT,
                    LEADERBOARD_GANG,
                    LEADERBOARD_BLOCKS,
                    LEADERBOARD_LEVEL,
                    LEADERBOARD_CYCLE);

    private static final Map<String, WidgetDescriptor> WIDGETS_BY_ID = buildWidgetsById(WIDGETS);

    private static final List<EventDescriptor> EVENTS_CATALOG =
            List.of(
                    new EventDescriptor(CompanionConfig.HUD_EVENT_METEORITE, "Meteorite", "MTR"),
                    new EventDescriptor(CompanionConfig.HUD_EVENT_METEOR, "Meteor", "MET"),
                    new EventDescriptor(
                            CompanionConfig.HUD_EVENT_ALTAR_SPAWN, "Altar Spawn", "ALT"),
                    new EventDescriptor(CompanionConfig.HUD_EVENT_KOTH, "KOTH", "KTH"),
                    new EventDescriptor(
                            CompanionConfig.HUD_EVENT_CREDIT_SHOP_RESET,
                            "Credit Shop Reset",
                            "CSR"),
                    new EventDescriptor(CompanionConfig.HUD_EVENT_JACKPOT, "Jackpot", "JPT"),
                    new EventDescriptor(CompanionConfig.HUD_EVENT_FLASH_SALE, "Flash Sale", "FLS"),
                    new EventDescriptor(CompanionConfig.HUD_EVENT_MERCHANT, "Merchant", "MRC"),
                    new EventDescriptor(
                            CompanionConfig.HUD_EVENT_NEXT_REBOOT, "Next Reboot", "RBT"),
                    new EventDescriptor(
                            CompanionConfig.HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK,
                            "Next Level Cap Day Unlock",
                            "CAP"));

    private static final Map<String, EventDescriptor> EVENT_BY_KEY =
            buildEventByKey(EVENTS_CATALOG);
    private static final Map<String, EventDescriptor> EVENT_BY_LABEL =
            buildEventByLabel(EVENTS_CATALOG);

    private HudWidgetCatalog() {}

    /** Returns all known HUD widget descriptors in deterministic render/settings order. */
    public static List<WidgetDescriptor> widgets() {
        return WIDGETS;
    }

    /** Resolves any widget descriptor by id (case-insensitive). */
    public static Optional<WidgetDescriptor> findWidget(String widgetId) {
        if (widgetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(WIDGETS_BY_ID.get(normalizeToken(widgetId)));
    }

    /** Returns leaderboard widget descriptors (excluding the rotating synthetic entry). */
    public static List<WidgetDescriptor> leaderboardWidgets() {
        return LEADERBOARDS;
    }

    /** Resolves a leaderboard widget descriptor by id (case-insensitive). */
    public static Optional<WidgetDescriptor> findLeaderboardWidget(String widgetId) {
        if (widgetId == null) {
            return Optional.empty();
        }
        for (WidgetDescriptor descriptor : LEADERBOARDS) {
            if (normalizeToken(descriptor.widgetId()).equals(normalizeToken(widgetId))) {
                return Optional.of(descriptor);
            }
        }
        return Optional.empty();
    }

    /** Returns the rotating leaderboard descriptor used for cycle mode rendering. */
    public static WidgetDescriptor leaderboardCycleWidget() {
        return LEADERBOARD_CYCLE;
    }

    /** Returns whether a widget id belongs to any leaderboard widget. */
    public static boolean isLeaderboardWidget(String widgetId) {
        return findLeaderboardWidget(widgetId).isPresent();
    }

    /** Returns whether a widget id is the rotating leaderboard widget id. */
    public static boolean isLeaderboardCycleWidget(String widgetId) {
        return normalizeToken(CompanionConfig.HUD_WIDGET_LEADERBOARD_CYCLE_ID)
                .equals(normalizeToken(widgetId));
    }

    /** Returns all known event descriptors used by event HUD rendering/sorting. */
    public static List<EventDescriptor> eventDescriptors() {
        return EVENTS_CATALOG;
    }

    /** Resolves an event descriptor by canonical event key. */
    public static Optional<EventDescriptor> findEventByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(EVENT_BY_KEY.get(normalizeToken(key)));
    }

    /** Resolves an event descriptor by display label. */
    public static Optional<EventDescriptor> findEventByLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(EVENT_BY_LABEL.get(normalizeToken(label)));
    }

    /** Splits a raw line into label/value using the first ':' separator. */
    public static ParsedLine splitLine(String line) {
        if (line == null) {
            return new ParsedLine("", "");
        }

        String trimmed = line.trim();
        int separator = trimmed.indexOf(':');

        if (separator <= 0 || separator >= trimmed.length() - 1) {
            return new ParsedLine(trimmed, "");
        }

        String label = trimmed.substring(0, separator).trim();
        String value = trimmed.substring(separator + 1).trim();
        return new ParsedLine(label, value);
    }

    /**
     * Parses and sorts event rows by soonest duration first.
     *
     * <p>Unknown lines are appended after known/sorted events, preserving their original order.
     */
    public static List<ParsedLine> sortEventsClosestFirst(
            List<String> rawLines, Map<String, Boolean> visibilityByEventKey) {
        List<ParsedEventLine> known = new ArrayList<>();
        List<ParsedLine> unknown = new ArrayList<>();

        for (int index = 0; index < rawLines.size(); index++) {
            ParsedLine parsedLine = splitLine(rawLines.get(index));
            Optional<EventDescriptor> eventDescriptor = findEventByLabel(parsedLine.label());

            if (eventDescriptor.isPresent()) {
                String eventKey = eventDescriptor.orElseThrow().key();
                if (Boolean.FALSE.equals(visibilityByEventKey.get(eventKey))) {
                    continue;
                }

                long sortSeconds = parseDurationSeconds(parsedLine.value()).orElse(Long.MAX_VALUE);
                known.add(new ParsedEventLine(parsedLine, sortSeconds, index));
                continue;
            }

            unknown.add(parsedLine);
        }

        known.sort(
                Comparator.comparingLong(ParsedEventLine::sortSeconds)
                        .thenComparingInt(ParsedEventLine::originalIndex));

        List<ParsedLine> sorted = new ArrayList<>(known.size() + unknown.size());
        for (ParsedEventLine line : known) {
            sorted.add(line.parsedLine());
        }
        sorted.addAll(unknown);
        return sorted;
    }

    /** Returns whether a cooldown line should be treated as active/armed. */
    public static boolean isCooldownLineActive(String rawLine) {
        ParsedLine parsedLine = splitLine(rawLine);
        String status = normalizeToken(primaryStatusSegment(parsedLine.value()));

        if (status.isEmpty()) {
            return !normalizeToken(parsedLine.label()).isEmpty();
        }

        OptionalLong durationSeconds = parseDurationSeconds(status);
        if (durationSeconds.isPresent()) {
            return durationSeconds.orElse(0L) > 0L;
        }

        return !isInactiveStatus(status);
    }

    /**
     * Attempts to parse a duration string into total seconds.
     *
     * @return Parsed seconds or empty when no duration token is present.
     */
    public static OptionalLong parseDurationSeconds(String statusText) {
        if (statusText == null) {
            return OptionalLong.empty();
        }

        String normalized = normalizeToken(primaryStatusSegment(statusText));
        if (normalized.isEmpty()) {
            return OptionalLong.empty();
        }

        if (normalized.startsWith("check in ")) {
            normalized = normalized.substring("check in ".length()).trim();
        }

        if (normalized.endsWith(")") && normalized.contains("(")) {
            int open = normalized.lastIndexOf('(');
            if (open >= 0) {
                OptionalLong nested =
                        parseDurationSeconds(
                                normalized.substring(open + 1, normalized.length() - 1));
                if (nested.isPresent()) {
                    return nested;
                }
                normalized = normalized.substring(0, open).trim();
            }
        }

        if ("now".equals(normalized) || "active".equals(normalized)) {
            return OptionalLong.of(0L);
        }

        if (isUnavailableStatus(normalized)) {
            return OptionalLong.empty();
        }

        if (normalized.chars().allMatch(Character::isDigit)) {
            try {
                return OptionalLong.of(Long.parseLong(normalized));
            } catch (NumberFormatException ignored) {
                return OptionalLong.empty();
            }
        }

        Matcher matcher = DURATION_PART_PATTERN.matcher(normalized);
        long totalSeconds = 0L;
        boolean foundPart = false;

        while (matcher.find()) {
            foundPart = true;
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return OptionalLong.empty();
            }

            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            long multiplier =
                    switch (unit) {
                        case 'd' -> 86_400L;
                        case 'h' -> 3_600L;
                        case 'm' -> 60L;
                        case 's' -> 1L;
                        default -> 0L;
                    };

            if (multiplier <= 0L) {
                continue;
            }

            if (amount > Long.MAX_VALUE / multiplier) {
                return OptionalLong.of(Long.MAX_VALUE);
            }

            long seconds = amount * multiplier;
            if (Long.MAX_VALUE - totalSeconds < seconds) {
                return OptionalLong.of(Long.MAX_VALUE);
            }

            totalSeconds += seconds;
        }

        if (!foundPart) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(totalSeconds);
    }

    private static boolean isInactiveStatus(String status) {
        return "now".equals(status)
                || "0s".equals(status)
                || "ready".equals(status)
                || "available".equals(status)
                || isUnavailableStatus(status);
    }

    private static boolean isUnavailableStatus(String status) {
        return "unavailable".equals(status)
                || "not scheduled".equals(status)
                || "max day".equals(status)
                || "n/a".equals(status);
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String primaryStatusSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int metadataSeparator = value.indexOf('|');
        String primary = metadataSeparator >= 0 ? value.substring(0, metadataSeparator) : value;
        return primary.trim();
    }

    private static Map<String, WidgetDescriptor> buildWidgetsById(List<WidgetDescriptor> widgets) {
        Map<String, WidgetDescriptor> byId = new LinkedHashMap<>();
        for (WidgetDescriptor widget : widgets) {
            byId.put(normalizeToken(widget.widgetId()), widget);
        }
        return Map.copyOf(byId);
    }

    private static Map<String, EventDescriptor> buildEventByKey(List<EventDescriptor> events) {
        Map<String, EventDescriptor> byKey = new LinkedHashMap<>();
        for (EventDescriptor event : events) {
            byKey.put(normalizeToken(event.key()), event);
        }
        return Map.copyOf(byKey);
    }

    private static Map<String, EventDescriptor> buildEventByLabel(List<EventDescriptor> events) {
        Map<String, EventDescriptor> byLabel = new LinkedHashMap<>();
        for (EventDescriptor event : events) {
            byLabel.put(normalizeToken(event.label()), event);
        }
        return Map.copyOf(byLabel);
    }

    private record ParsedEventLine(ParsedLine parsedLine, long sortSeconds, int originalIndex) {}
}
