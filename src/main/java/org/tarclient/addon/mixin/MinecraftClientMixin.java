package org.tarclient.addon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.tarclient.addon.events.ClickBlockEvent;
import org.tarclient.addon.modules.MioCompatibility;
import org.tarclient.addon.modules.VirtualHotbar;
import org.tarclient.addon.utils.VirtualHotbarUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    public @Nullable HitResult crosshairTarget;

    @Redirect(method = "doAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z"))
    private boolean onClickBlock(ClientPlayerInteractionManager instance, BlockPos pos, Direction direction) {
        if (MeteorClient.EVENT_BUS.post(ClickBlockEvent.get(pos, direction)).isCancelled()) {
            return true;
        }
        return instance.attackBlock(pos, direction);
    }

    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V"))
    private void onSetSelectedSlot(PlayerInventory instance, int slot) {
        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            VirtualHotbarUtils.select(slot);
        } else {
            instance.setSelectedSlot(slot);
        }
    }

    @Inject(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onInteractBlock(CallbackInfo ci, @Local(ordinal = 0) Hand hand) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        if (!(this.crosshairTarget instanceof BlockHitResult bhr)) return;

        MioCompatibility mioCompatibility = Modules.get().get(MioCompatibility.class);
        if (mioCompatibility != null && mioCompatibility.enabled.get() && mioCompatibility.blockPlaceRotation.get()) {
            // avoid non-block item
            ItemStack stack = mc.player.getStackInHand(hand);
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                double yaw = Rotations.getYaw(bhr.getPos());
                double pitch = Rotations.getPitch(bhr.getPos());

                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), (float) yaw, (float) pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            }
        }
    }
}
