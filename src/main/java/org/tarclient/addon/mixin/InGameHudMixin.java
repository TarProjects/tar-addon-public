package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.modules.VirtualHotbar;
import org.tarclient.addon.utils.VirtualHotbarUtils;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getStack(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack noswap$renderVirtualHotbar(PlayerInventory instance, int slot) {
        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            ItemStack visualStack = VirtualHotbarUtils.getVisualStack(instance, slot);
            if (visualStack == null) visualStack = ItemStack.EMPTY;
            return visualStack;
        }
        return instance.getStack(slot);
    }

    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedSlot()I"))
    private int noswap$virtualSelectedSlot(PlayerInventory instance) {
        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            return VirtualHotbarUtils.getVirtualSelectedSlot();
        }
        return instance.getSelectedSlot();
    }
}
