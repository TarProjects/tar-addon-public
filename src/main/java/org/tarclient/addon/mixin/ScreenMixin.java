package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "handleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void onHandleTextClick(ClickEvent clickEvent, MinecraftClient client, Screen screenAfterRun, CallbackInfo ci) {
        if (clickEvent == null) return;

        if (clickEvent.getAction() == ClickEvent.Action.CUSTOM && clickEvent instanceof ClickEvent.Custom(
            net.minecraft.util.Identifier id, java.util.Optional<NbtElement> payload
        )) {
            String identifier = id.toString();

            if (identifier.startsWith("tar")) {
                ci.cancel();

                if (identifier.equals("tar:send_message")) {
                    payload.flatMap(NbtElement::asString).ifPresent(message -> {
                        ChatUtils.sendPlayerMsg(message, false);
                    });
                }
            }
        }

    }
}
