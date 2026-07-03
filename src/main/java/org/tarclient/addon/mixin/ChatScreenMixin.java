package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.events.SendTypedMessageEvent;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Redirect(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;sendMessage(Ljava/lang/String;Z)V"))
    private void onSendTypedMessage(ChatScreen instance, String chatText, boolean addToHistory) {
        if (!MeteorClient.EVENT_BUS.post(SendTypedMessageEvent.get(chatText)).isCancelled()) {
            instance.sendMessage(chatText, addToHistory);
        }
    }
}
