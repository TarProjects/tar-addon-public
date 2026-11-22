package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.modules.Packets;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "startGliding", at = @At("HEAD"), cancellable = true)
    private void tar$onStartGliding(CallbackInfo ci) {
        Packets packets = Modules.get().get(Packets.class);
        if (packets != null && packets.shouldCancelGliding()) ci.cancel();
    }
}
