package me.landon.client;

import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.client.runtime.CosmicApiClientRuntime;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint.
 *
 * <p>The active server network path is centralized in {@link CosmicApiClientRuntime}. Local
 * settings, keybinds, and rendering hooks remain owned by {@link CompanionClientRuntime}.
 */
public final class CosmicPrisonsModClient implements ClientModInitializer {
    /** Bootstraps the singleton client runtime and registers all client-side event hooks. */
    @Override
    public void onInitializeClient() {
        CompanionClientRuntime.getInstance().initializeClient();
        CosmicApiClientRuntime.getInstance().initializeClient();
    }
}
