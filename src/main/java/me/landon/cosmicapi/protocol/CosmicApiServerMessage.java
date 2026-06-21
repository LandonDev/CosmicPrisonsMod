package me.landon.cosmicapi.protocol;

import java.util.List;
import java.util.Map;

public record CosmicApiServerMessage(
        String type,
        String event,
        String actionType,
        boolean allowed,
        String sessionId,
        String requestId,
        String reason,
        String appName,
        String serverScope,
        List<String> allowedScopes,
        List<String> allowedHooks,
        Map<String, Object> payload) {}
