package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.modules.VirtualHotbar;
import org.tarclient.addon.utils.VirtualHotbarUtils;

@Mixin(Mouse.class)
public class MouseMixin {
    @Redirect(method = "onMouseScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedSlot()I"))
    private int onMouseScrollSlot(PlayerInventory instance) {
        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            return VirtualHotbarUtils.virtualSelectedSlot;
        }
        return instance.getSelectedSlot();
    }

    @Redirect(method = "onMouseScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V"))
    private void onSetSelectedSlot(PlayerInventory instance, int slot) {
        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            VirtualHotbarUtils.select(slot);
        } else {
            instance.setSelectedSlot(slot);
        }
    }
}
