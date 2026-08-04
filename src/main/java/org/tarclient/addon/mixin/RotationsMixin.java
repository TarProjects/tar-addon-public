package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tarclient.addon.events.ModifyRotationCameraPosEvent;

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

    // --- SPOOF PLAYER POSITION FOR ROTATION COMPATIBILITY ---
    // getYaw(Entity)
    @Inject(method = "getYaw(Lnet/minecraft/entity/Entity;)D", at = @At("HEAD"))
    private static void head$onGetYawEntity(Entity entity, CallbackInfoReturnable<Double> cir) {
        startSpoof(mc.player);
    }

    @Inject(method = "getYaw(Lnet/minecraft/entity/Entity;)D", at = @At("RETURN"))
    private static void return$onGetYawEntity(Entity entity, CallbackInfoReturnable<Double> cir) {
        endSpoof(mc.player);
    }

    // getYaw(Vec3d)
    @Inject(method = "getYaw(Lnet/minecraft/util/math/Vec3d;)D", at = @At("HEAD"))
    private static void head$onGetYawVec(Vec3d pos, CallbackInfoReturnable<Double> cir) {
        startSpoof(mc.player);
    }

    @Inject(method = "getYaw(Lnet/minecraft/util/math/Vec3d;)D", at = @At("RETURN"))
    private static void return$onGetYawVec(Vec3d pos, CallbackInfoReturnable<Double> cir) {
        endSpoof(mc.player);
    }

    // getYaw(BlockPos)
    @Inject(method = "getYaw(Lnet/minecraft/util/math/BlockPos;)D", at = @At("HEAD"))
    private static void head$onGetYawBlockPos(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        startSpoof(mc.player);
    }

    @Inject(method = "getYaw(Lnet/minecraft/util/math/BlockPos;)D", at = @At("RETURN"))
    private static void return$onGetYawBlockPos(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        endSpoof(mc.player);
    }

    // getPitch(BlockPos)
    @Inject(method = "getPitch(Lnet/minecraft/util/math/BlockPos;)D", at = @At("HEAD"))
    private static void head$onGetPitchBlockPos(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        startSpoof(mc.player);
    }

    @Inject(method = "getPitch(Lnet/minecraft/util/math/BlockPos;)D", at = @At("RETURN"))
    private static void return$onGetPitchBlockPos(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        endSpoof(mc.player);
    }

    // getPitch(Vec3d)
    @Inject(method = "getPitch(Lnet/minecraft/util/math/Vec3d;)D", at = @At("HEAD"))
    private static void head$onGetPitchVec(Vec3d pos, CallbackInfoReturnable<Double> cir) {
        startSpoof(mc.player);
    }

    @Inject(method = "getPitch(Lnet/minecraft/util/math/Vec3d;)D", at = @At("RETURN"))
    private static void return$onGetPitchVec(Vec3d pos, CallbackInfoReturnable<Double> cir) {
        endSpoof(mc.player);
    }

    // getPitch(Entity, Target)
    @Inject(method = "getPitch(Lnet/minecraft/entity/Entity;Lmeteordevelopment/meteorclient/utils/entity/Target;)D", at = @At("HEAD"))
    private static void head$onGetPitchTarget(Entity entity, Target target, CallbackInfoReturnable<Double> cir) {
        startSpoof(mc.player);
    }

    @Inject(method = "getPitch(Lnet/minecraft/entity/Entity;Lmeteordevelopment/meteorclient/utils/entity/Target;)D", at = @At("RETURN"))
    private static void return$onGetPitchTarget(Entity entity, Target target, CallbackInfoReturnable<Double> cir) {
        endSpoof(mc.player);
    }

    @Unique
    private static double origX, origY, origZ;
    @Unique
    private static boolean active = false;

    @Unique
    private static void startSpoof(PlayerEntity player) {
        if (player == null) return;
        Vec3d pos = player.getEntityPos();
        origX = pos.x;
        origY = pos.y;
        origZ = pos.z;
        active = true;

        MeteorClient.EVENT_BUS.post(ModifyRotationCameraPosEvent.get(pos));
    }

    @Unique
    private static void endSpoof(PlayerEntity player) {
        if (!active || player == null) return;
        Vec3d pos = player.getEntityPos();
        ((IVec3d) pos).meteor$set(origX, origY, origZ);
        active = false;
    }
}
