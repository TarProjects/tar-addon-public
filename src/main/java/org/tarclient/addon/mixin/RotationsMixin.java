package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.utils.player.Rotations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Rotations.class)
public class RotationsMixin {

    @Shadow(remap = false)
    public static float serverYaw;

    @Shadow(remap = false)
    public static float serverPitch;

    @Inject(method = "onSendMovementPacketsPre", at = @At("HEAD"), remap = false)
    private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event, CallbackInfo ci) {
        if (mc.getCameraEntity() == mc.player && mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
    }
}
