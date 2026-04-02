package me.landon.companion.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CompanionConfig {
    public static final String OFFICIAL_ALLOWED_SERVER_ID = "cosmicprisons.com";
    private static final String SIGNATURE_POLICY_OFF = "OFF";
    private static final String SIGNATURE_POLICY_LOG_ONLY = "LOG_ONLY";
    private static final String SIGNATURE_POLICY_ENFORCE = "ENFORCE";
    public static final String HUD_WIDGET_EVENTS_ID = "events";
    public static final String HUD_WIDGET_COOLDOWNS_ID = "cooldowns";
    public static final String HUD_WIDGET_PETS_ID = "pets";
    public static final String HUD_WIDGET_SATCHELS_ID = "satchels";
    public static final String HUD_WIDGET_GANG_ID = "gang";
    public static final String HUD_WIDGET_LEADERBOARD_GIFT_ID = "leaderboard_gift";
    public static final String HUD_WIDGET_LEADERBOARD_GANG_ID = "leaderboard_gang";
    public static final String HUD_WIDGET_LEADERBOARD_BLOCKS_ID = "leaderboard_blocks";
    public static final String HUD_WIDGET_LEADERBOARD_LEVEL_ID = "leaderboard_level";
    public static final String HUD_WIDGET_LEADERBOARD_CYCLE_ID = "leaderboard_cycle";
    public static final String HUD_EVENT_METEORITE = "meteorite";
    public static final String HUD_EVENT_METEOR = "meteor";
    public static final String HUD_EVENT_ALTAR_SPAWN = "altar_spawn";
    public static final String HUD_EVENT_KOTH = "koth";
    public static final String HUD_EVENT_CREDIT_SHOP_RESET = "credit_shop_reset";
    public static final String HUD_EVENT_JACKPOT = "jackpot";
    public static final String HUD_EVENT_FLASH_SALE = "flash_sale";
    public static final String HUD_EVENT_MERCHANT = "merchant";
    public static final String HUD_EVENT_NEXT_REBOOT = "next_reboot";
    public static final String HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK = "next_level_cap_day_unlock";
    public static final List<String> HUD_EVENT_KEYS =
            List.of(
                    HUD_EVENT_METEORITE,
                    HUD_EVENT_METEOR,
                    HUD_EVENT_ALTAR_SPAWN,
                    HUD_EVENT_KOTH,
                    HUD_EVENT_CREDIT_SHOP_RESET,
                    HUD_EVENT_JACKPOT,
                    HUD_EVENT_FLASH_SALE,
                    HUD_EVENT_MERCHANT,
                    HUD_EVENT_NEXT_REBOOT,
                    HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK);
    public static final List<String> HUD_LEADERBOARD_WIDGET_IDS =
            List.of(
                    HUD_WIDGET_LEADERBOARD_GIFT_ID,
                    HUD_WIDGET_LEADERBOARD_GANG_ID,
                    HUD_WIDGET_LEADERBOARD_BLOCKS_ID,
                    HUD_WIDGET_LEADERBOARD_LEVEL_ID);
    public static final List<String> HUD_WIDGET_IDS =
            List.of(
                    HUD_WIDGET_EVENTS_ID,
                    HUD_WIDGET_COOLDOWNS_ID,
                    HUD_WIDGET_PETS_ID,
                    HUD_WIDGET_SATCHELS_ID,
                    HUD_WIDGET_GANG_ID,
                    HUD_WIDGET_LEADERBOARD_GIFT_ID,
                    HUD_WIDGET_LEADERBOARD_GANG_ID,
                    HUD_WIDGET_LEADERBOARD_BLOCKS_ID,
                    HUD_WIDGET_LEADERBOARD_LEVEL_ID,
                    HUD_WIDGET_LEADERBOARD_CYCLE_ID);
    public static final double HUD_WIDGET_SCALE_MIN = 0.55D;
    public static final double HUD_WIDGET_SCALE_MAX = 1.65D;
    public static final double HUD_WIDGET_WIDTH_MULTIPLIER_MIN = 0.55D;
    public static final double HUD_WIDGET_WIDTH_MULTIPLIER_MAX = 1.35D;
    public static final int PING_VISUAL_DURATION_SECONDS_MIN = 2;
    public static final int PING_VISUAL_DURATION_SECONDS_MAX = 15;
    public static final int PING_VISUAL_DURATION_SECONDS_DEFAULT = 5;
    public static final String PING_VISUAL_MODE_FOLLOW = "follow";
    public static final String PING_VISUAL_MODE_STATIC = "static";
    public static final String PING_VISUAL_MODE_DEFAULT = PING_VISUAL_MODE_FOLLOW;

    public List<String> allowedServerIds = new ArrayList<>();
    public boolean enablePayloadCodecFallback = false;
    public Map<String, Boolean> featureToggles = new LinkedHashMap<>();
    public Map<String, HudWidgetPosition> hudWidgetPositions = new LinkedHashMap<>();
    public Map<String, Double> hudWidgetScales = new LinkedHashMap<>();
    public Map<String, Double> hudWidgetWidthMultipliers = new LinkedHashMap<>();
    public boolean hudEventsCompactMode = false;
    public boolean hudSatchelsCompactMode = false;
    public boolean hudLeaderboardsCompactMode = false;
    public boolean hudLeaderboardsCycleMode = true;
    public Map<String, Boolean> hudEventVisibility = new LinkedHashMap<>();
    public Map<String, Boolean> hudLeaderboardVisibility = new LinkedHashMap<>();
    public String serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
    public boolean requireServerSignature;
    public List<String> serverSignaturePublicKeys = new ArrayList<>();
    public boolean logMalformedOncePerConnection = true;
    public int pingVisualDurationSeconds = PING_VISUAL_DURATION_SECONDS_DEFAULT;
    public String pingVisualMode = PING_VISUAL_MODE_DEFAULT;
    public boolean pingParticlesEnabled = true;

    public static CompanionConfig defaults() {
        CompanionConfig config = new CompanionConfig();
        config.serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
        config.requireServerSignature = false;
        config.allowedServerIds = List.of(OFFICIAL_ALLOWED_SERVER_ID);
        config.hudWidgetPositions = defaultHudWidgetPositions();
        config.hudWidgetScales = defaultHudWidgetScales();
        config.hudWidgetWidthMultipliers = defaultHudWidgetWidthMultipliers();
        config.hudEventsCompactMode = false;
        config.hudSatchelsCompactMode = false;
        config.hudLeaderboardsCompactMode = false;
        config.hudLeaderboardsCycleMode = true;
        config.hudEventVisibility = defaultHudEventVisibility();
        config.hudLeaderboardVisibility = defaultHudLeaderboardVisibility();
        config.pingVisualDurationSeconds = PING_VISUAL_DURATION_SECONDS_DEFAULT;
        config.pingVisualMode = PING_VISUAL_MODE_DEFAULT;
        config.pingParticlesEnabled = true;
        return config;
    }

    public void sanitize() {
        if (allowedServerIds == null) {
            allowedServerIds = new ArrayList<>();
        }

        if (serverSignaturePublicKeys == null) {
            serverSignaturePublicKeys = new ArrayList<>();
        }

        if (featureToggles == null) {
            featureToggles = new LinkedHashMap<>();
        }

        if (hudWidgetPositions == null) {
            hudWidgetPositions = new LinkedHashMap<>();
        }

        if (hudWidgetScales == null) {
            hudWidgetScales = new LinkedHashMap<>();
        }

        if (hudWidgetWidthMultipliers == null) {
            hudWidgetWidthMultipliers = new LinkedHashMap<>();
        }

        if (hudEventVisibility == null) {
            hudEventVisibility = new LinkedHashMap<>();
        }

        if (hudLeaderboardVisibility == null) {
            hudLeaderboardVisibility = new LinkedHashMap<>();
        }

        allowedServerIds = List.of(OFFICIAL_ALLOWED_SERVER_ID);

        if (serverSignaturePolicy == null || serverSignaturePolicy.isBlank()) {
            serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
        } else {
            serverSignaturePolicy = serverSignaturePolicy.trim().toUpperCase(Locale.ROOT);
        }

        if (!SIGNATURE_POLICY_OFF.equals(serverSignaturePolicy)
                && !SIGNATURE_POLICY_LOG_ONLY.equals(serverSignaturePolicy)
                && !SIGNATURE_POLICY_ENFORCE.equals(serverSignaturePolicy)) {
            serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
        }

        requireServerSignature = SIGNATURE_POLICY_ENFORCE.equals(serverSignaturePolicy);

        serverSignaturePublicKeys =
                serverSignaturePublicKeys.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();

        Map<String, Boolean> cleanedFeatureToggles = new LinkedHashMap<>();

        for (Map.Entry<String, Boolean> entry : featureToggles.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String key = entry.getKey().trim();

            if (!key.isEmpty()) {
                cleanedFeatureToggles.put(key, entry.getValue());
            }
        }

        featureToggles = cleanedFeatureToggles;

        Map<String, HudWidgetPosition> cleanedWidgetPositions = defaultHudWidgetPositions();
        for (Map.Entry<String, HudWidgetPosition> entry : hudWidgetPositions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedId = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_WIDGET_IDS.contains(normalizedId)) {
                continue;
            }

            cleanedWidgetPositions.put(normalizedId, entry.getValue().clamped());
        }
        hudWidgetPositions = cleanedWidgetPositions;

        Map<String, Double> cleanedWidgetScales = defaultHudWidgetScales();
        for (Map.Entry<String, Double> entry : hudWidgetScales.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedId = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_WIDGET_IDS.contains(normalizedId)) {
                continue;
            }

            cleanedWidgetScales.put(normalizedId, clampHudWidgetScale(entry.getValue()));
        }
        hudWidgetScales = cleanedWidgetScales;

        Map<String, Double> cleanedWidthMultipliers = defaultHudWidgetWidthMultipliers();
        for (Map.Entry<String, Double> entry : hudWidgetWidthMultipliers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedId = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_WIDGET_IDS.contains(normalizedId)) {
                continue;
            }

            cleanedWidthMultipliers.put(
                    normalizedId, clampHudWidgetWidthMultiplier(entry.getValue()));
        }
        hudWidgetWidthMultipliers = cleanedWidthMultipliers;

        Map<String, Boolean> cleanedHudEventVisibility = defaultHudEventVisibility();
        for (Map.Entry<String, Boolean> entry : hudEventVisibility.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedKey = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_EVENT_KEYS.contains(normalizedKey)) {
                continue;
            }

            cleanedHudEventVisibility.put(normalizedKey, entry.getValue());
        }
        hudEventVisibility = cleanedHudEventVisibility;

        Map<String, Boolean> cleanedHudLeaderboardVisibility = defaultHudLeaderboardVisibility();
        for (Map.Entry<String, Boolean> entry : hudLeaderboardVisibility.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedKey = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_LEADERBOARD_WIDGET_IDS.contains(normalizedKey)) {
                continue;
            }

            cleanedHudLeaderboardVisibility.put(normalizedKey, entry.getValue());
        }
        hudLeaderboardVisibility = cleanedHudLeaderboardVisibility;
        pingVisualDurationSeconds = clampPingVisualDurationSeconds(pingVisualDurationSeconds);
        pingVisualMode = normalizePingVisualMode(pingVisualMode);
    }

    public boolean isSignatureVerificationEnforced() {
        return SIGNATURE_POLICY_ENFORCE.equals(serverSignaturePolicy);
    }

    public boolean isSignatureVerificationLogOnly() {
        return SIGNATURE_POLICY_LOG_ONLY.equals(serverSignaturePolicy);
    }

    public boolean isSignatureVerificationOff() {
        return SIGNATURE_POLICY_OFF.equals(serverSignaturePolicy);
    }

    public static final class HudWidgetPosition {
        public double x;
        public double y;

        public HudWidgetPosition() {}

        public HudWidgetPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }

        private HudWidgetPosition clamped() {
            return new HudWidgetPosition(clamp01(x), clamp01(y));
        }

        private static double clamp01(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }

    private static Map<String, HudWidgetPosition> defaultHudWidgetPositions() {
        Map<String, HudWidgetPosition> defaults = new LinkedHashMap<>();
        defaults.put(HUD_WIDGET_EVENTS_ID, new HudWidgetPosition(0.03D, 0.08D));
        defaults.put(HUD_WIDGET_COOLDOWNS_ID, new HudWidgetPosition(0.70D, 0.08D));
        defaults.put(HUD_WIDGET_PETS_ID, new HudWidgetPosition(0.43D, 0.68D));
        defaults.put(HUD_WIDGET_SATCHELS_ID, new HudWidgetPosition(0.70D, 0.68D));
        defaults.put(HUD_WIDGET_GANG_ID, new HudWidgetPosition(0.03D, 0.68D));
        defaults.put(HUD_WIDGET_LEADERBOARD_GIFT_ID, new HudWidgetPosition(0.43D, 0.08D));
        defaults.put(HUD_WIDGET_LEADERBOARD_GANG_ID, new HudWidgetPosition(0.03D, 0.38D));
        defaults.put(HUD_WIDGET_LEADERBOARD_BLOCKS_ID, new HudWidgetPosition(0.70D, 0.38D));
        defaults.put(HUD_WIDGET_LEADERBOARD_LEVEL_ID, new HudWidgetPosition(0.43D, 0.38D));
        defaults.put(HUD_WIDGET_LEADERBOARD_CYCLE_ID, new HudWidgetPosition(0.43D, 0.08D));
        return defaults;
    }

    private static Map<String, Double> defaultHudWidgetScales() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put(HUD_WIDGET_EVENTS_ID, 0.78D);
        defaults.put(HUD_WIDGET_COOLDOWNS_ID, 0.80D);
        defaults.put(HUD_WIDGET_PETS_ID, 0.78D);
        defaults.put(HUD_WIDGET_SATCHELS_ID, 0.78D);
        defaults.put(HUD_WIDGET_GANG_ID, 0.76D);
        defaults.put(HUD_WIDGET_LEADERBOARD_GIFT_ID, 0.74D);
        defaults.put(HUD_WIDGET_LEADERBOARD_GANG_ID, 0.74D);
        defaults.put(HUD_WIDGET_LEADERBOARD_BLOCKS_ID, 0.74D);
        defaults.put(HUD_WIDGET_LEADERBOARD_LEVEL_ID, 0.74D);
        defaults.put(HUD_WIDGET_LEADERBOARD_CYCLE_ID, 0.74D);
        return defaults;
    }

    private static Map<String, Double> defaultHudWidgetWidthMultipliers() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put(HUD_WIDGET_EVENTS_ID, 0.86D);
        defaults.put(HUD_WIDGET_COOLDOWNS_ID, 1.0D);
        defaults.put(HUD_WIDGET_PETS_ID, 1.08D);
        defaults.put(HUD_WIDGET_SATCHELS_ID, 1.0D);
        defaults.put(HUD_WIDGET_GANG_ID, 0.92D);
        defaults.put(HUD_WIDGET_LEADERBOARD_GIFT_ID, 0.92D);
        defaults.put(HUD_WIDGET_LEADERBOARD_GANG_ID, 0.92D);
        defaults.put(HUD_WIDGET_LEADERBOARD_BLOCKS_ID, 0.92D);
        defaults.put(HUD_WIDGET_LEADERBOARD_LEVEL_ID, 0.92D);
        defaults.put(HUD_WIDGET_LEADERBOARD_CYCLE_ID, 0.92D);
        return defaults;
    }

    public static double clampHudWidgetScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0D;
        }
        return Math.max(HUD_WIDGET_SCALE_MIN, Math.min(HUD_WIDGET_SCALE_MAX, value));
    }

    public static double clampHudWidgetWidthMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0D;
        }
        return Math.max(
                HUD_WIDGET_WIDTH_MULTIPLIER_MIN, Math.min(HUD_WIDGET_WIDTH_MULTIPLIER_MAX, value));
    }

    public static int clampPingVisualDurationSeconds(int value) {
        return Math.max(
                PING_VISUAL_DURATION_SECONDS_MIN,
                Math.min(PING_VISUAL_DURATION_SECONDS_MAX, value));
    }

    public static String normalizePingVisualMode(String value) {
        if (value == null) {
            return PING_VISUAL_MODE_DEFAULT;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (PING_VISUAL_MODE_STATIC.equals(normalized)) {
            return PING_VISUAL_MODE_STATIC;
        }

        return PING_VISUAL_MODE_FOLLOW;
    }

    private static Map<String, Boolean> defaultHudEventVisibility() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (String key : HUD_EVENT_KEYS) {
            defaults.put(key, true);
        }
        return defaults;
    }

    private static Map<String, Boolean> defaultHudLeaderboardVisibility() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (String key : HUD_LEADERBOARD_WIDGET_IDS) {
            defaults.put(key, true);
        }
        return defaults;
    }
}
