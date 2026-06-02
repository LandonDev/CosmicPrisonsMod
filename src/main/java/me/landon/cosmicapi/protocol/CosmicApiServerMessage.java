package me.landon.cosmicapi.protocol;

import java.util.List;

public record CosmicApiServerMessage(
        String type,
        String event,
        boolean allowed,
        String sessionId,
        String requestId,
        String reason,
        String appName,
        List<String> allowedScopes,
        List<String> allowedHooks) {}
