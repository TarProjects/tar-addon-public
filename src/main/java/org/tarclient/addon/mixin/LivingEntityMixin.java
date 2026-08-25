package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.events.MovementRotationEvent;
import org.tarclient.addon.events.PlayerJumpEvent;
import org.tarclient.addon.events.TarTickMovementEvent;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Unique
    private float oldYaw;
    @Unique
    private float oldPitch;

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        if ((Object) this == mc.player) {
            if (MeteorClient.EVENT_BUS.post(PlayerJumpEvent.get()).isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;tickMovement()V"), cancellable = true)
    private void onTickMovement(CallbackInfo ci) {
        if ((Object) this == mc.player) {
            if (MeteorClient.EVENT_BUS.post(TarTickMovementEvent.get()).isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void onTravel(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity entity = ((LivingEntity)((Object) this));
        if (entity == null) return;
        oldYaw = entity.getYaw();
        oldPitch = entity.getPitch();

        MovementRotationEvent event = MeteorClient.EVENT_BUS.post(MovementRotationEvent.get(oldYaw, oldPitch));

        entity.setYaw(event.yaw);
        entity.setPitch(event.pitch);
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity entity = ((LivingEntity)((Object) this));
        if (entity == null) return;
        entity.setYaw(oldYaw);
        entity.setPitch(oldPitch);
    }
}
