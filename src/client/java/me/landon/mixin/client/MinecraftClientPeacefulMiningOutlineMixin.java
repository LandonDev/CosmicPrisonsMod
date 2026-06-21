package me.landon.mixin.client;

import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientPeacefulMiningOutlineMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void cosmicprisonsmod$forceOutlineForPeacefulMiningTarget(
            Entity entity, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (entity == null) {
            return;
        }

        CompanionClientRuntime runtime = CompanionClientRuntime.getInstance();
        if (runtime.isPeacefulMiningGhostedEntity(entity.getId())
                || runtime.isSameGangMarkedEntity(entity.getId())) {
            callbackInfo.setReturnValue(true);
        }
    }
}
