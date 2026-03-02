package me.landon.mixin.client;

import me.landon.client.runtime.ClientChatInteraction;
import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class CoordsClickHandlingMixin {
    @Inject(method = "handleClickEvent", at = @At("HEAD"), cancellable = true)
    private void handleClickEvent(
            Style style, boolean insert, CallbackInfoReturnable<Boolean> cir) {
        if (style == null) return;
        ClickEvent ce = style.getClickEvent();
        if (ce == null) return;

        if (ce.getAction() == ClickEvent.Action.RUN_COMMAND
                && ce instanceof ClickEvent.RunCommand(String command)) {
            if (command.startsWith(ClientChatInteraction.COORDS_CLICK_PREFIX)) {
                // Get the command payload and split into the xyz coords
                String payload =
                        command.substring(ClientChatInteraction.COORDS_CLICK_PREFIX.length());
                String[] parts = payload.split(" ", 3);
                if (parts.length == 3) {
                    try {
                        // Get the coordinates from the args
                        double x = Double.parseDouble(parts[0]);
                        double y = Double.parseDouble(parts[1]);
                        double z = Double.parseDouble(parts[2]);

                        // Add or remove the coordinate waypoint
                        CompanionClientRuntime.getInstance()
                                .addOrRemoveCoordsPing(new Vec3d(x, y, z));

                        // Tell the caller the click has been handled
                        cir.setReturnValue(true);
                        cir.cancel();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }
}
