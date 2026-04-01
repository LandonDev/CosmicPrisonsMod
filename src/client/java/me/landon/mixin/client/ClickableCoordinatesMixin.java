package me.landon.mixin.client;

import me.landon.client.runtime.ClientChatInteraction;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ClickableCoordinatesMixin {
    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Text cosmicprisonsmod$onAddMessage(Text value) {
        return ClientChatInteraction.modifyChatMessage(value);
    }
}
