package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.events.PlayerJumpEvent;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        if ((Object) this == mc.player) {
            if (MeteorClient.EVENT_BUS.post(PlayerJumpEvent.get()).isCancelled()) {
                ci.cancel();
            }
        }
    }
}
