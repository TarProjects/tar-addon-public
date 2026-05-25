package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.events.ClickBlockEvent;
import org.tarclient.addon.modules.VirtualHotbar;
import org.tarclient.addon.utils.VirtualHotbarUtils;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
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
}
