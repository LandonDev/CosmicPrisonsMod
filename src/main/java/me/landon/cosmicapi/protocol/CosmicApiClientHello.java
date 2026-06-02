package me.landon.cosmicapi.protocol;

import java.util.List;
import java.util.Objects;

public record CosmicApiClientHello(
        String clientId,
        String modId,
        String installId,
        String modLoader,
        String minecraftVersion,
        String modVersion,
        List<String> requestedScopes,
        List<String> requestedHooks) {
    public CosmicApiClientHello {
        clientId = requireNonBlank(clientId, "clientId");
        modId = requireNonBlank(modId, "modId");
        installId = normalizeOptional(installId);
        modLoader = requireNonBlank(modLoader, "modLoader");
        minecraftVersion = normalizeOptional(minecraftVersion);
        modVersion = normalizeOptional(modVersion);
        requestedScopes = List.copyOf(Objects.requireNonNull(requestedScopes, "requestedScopes"));
        requestedHooks = List.copyOf(Objects.requireNonNull(requestedHooks, "requestedHooks"));
    }

    public static CosmicApiClientHello official(
            String installId, String minecraftVersion, String modVersion) {
        return new CosmicApiClientHello(
                CosmicApiProtocolConstants.OFFICIAL_CLIENT_ID,
                CosmicApiProtocolConstants.OFFICIAL_MOD_ID,
                installId,
                CosmicApiProtocolConstants.MOD_LOADER,
                minecraftVersion,
                modVersion,
                CosmicApiProtocolConstants.OFFICIAL_REQUESTED_SCOPES,
                CosmicApiProtocolConstants.OFFICIAL_REQUESTED_HOOKS);
    }

    private static String requireNonBlank(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
