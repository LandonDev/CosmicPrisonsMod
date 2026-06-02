package me.landon.client;

import me.landon.client.runtime.CosmicApiClientRuntime;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint.
 *
 * <p>The active network runtime is centralized in {@link CosmicApiClientRuntime}; legacy companion
 * networking is intentionally not initialized.
 */
public final class CosmicPrisonsModClient implements ClientModInitializer {
    /** Bootstraps the singleton client runtime and registers all client-side event hooks. */
    @Override
    public void onInitializeClient() {
        CosmicApiClientRuntime.getInstance().initializeClient();
    }
}
