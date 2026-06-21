package me.landon.cosmicapi.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class CosmicApiNetworkBootstrap {
    private CosmicApiNetworkBootstrap() {}

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(CosmicApiRawPayload.ID, CosmicApiRawPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CosmicApiRawPayload.ID, CosmicApiRawPayload.CODEC);
    }
}
