package org.tarclient.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.tarclient.addon.events.MovementInputEvent;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput onMovementInput(PlayerInput original) {
        MovementInputEvent event = MovementInputEvent.get(original);
        return MeteorClient.EVENT_BUS.post(event).input;
    }
}
