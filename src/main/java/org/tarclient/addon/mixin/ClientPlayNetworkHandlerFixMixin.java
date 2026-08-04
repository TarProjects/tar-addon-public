package org.tarclient.addon.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPlayNetworkHandler.class, priority = 0) // low priority
public class ClientPlayNetworkHandlerFixMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void tar$onSendChatMessageWithprefix(String message, CallbackInfo ci) {
        if (message.startsWith(Config.get().prefix.get())) {
            // identical to meteor, but not adding into recent chat!
            // cancelling this means its not dispatched to rest of the
            // mixins
            try {
                Commands.dispatch(message.substring(Config.get().prefix.get().length()));
            } catch (CommandSyntaxException e) {
                ChatUtils.error(e.getMessage());
            }

            ci.cancel();
        }
    }
}
