package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.modules.VirtualHotbar;
import org.tarclient.addon.utils.VirtualHotbarUtils;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Redirect(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack modifyItemStack(Slot instance) {
        if (!(instance.inventory instanceof PlayerInventory inventory)) return instance.getStack();

        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            if (instance.id >= 36 && instance.id < 45) {
                int realSlot = instance.id - 36;
                return VirtualHotbarUtils.getVisualStack(inventory, realSlot);
            }
        }

        return instance.getStack();
    }

    @Redirect(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickSlot(IIILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void modifyClickSlot(ClientPlayerInteractionManager instance, int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player) {
        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar == null || !virtualHotbar.isActive()) {
            instance.clickSlot(syncId, slotId, button, actionType, player);
            return;
        }

        if (slotId >= 36 && slotId < 45) {
            int visualSlot = slotId - 36;
            int realSlot = VirtualHotbarUtils.visualToReal(visualSlot);

            slotId = 36 + realSlot;
        }

        if (actionType == SlotActionType.SWAP) {
            if (button >= 0 && button < 9) {
                button = VirtualHotbarUtils.visualToReal(button);
            }
        }

        instance.clickSlot(
            syncId,
            slotId,
            button,
            actionType,
            player
        );
    }

    @Redirect(method = "drawMouseoverTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack onDrawMouseOverToolTip$getStack(Slot instance) {
        if (!(instance.inventory instanceof PlayerInventory inventory)) return instance.getStack();

        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            if (instance.id >= 36 && instance.id < 45) {
                int realSlot = instance.id - 36;
                return VirtualHotbarUtils.getVisualStack(inventory, realSlot);
            }
        }

        return instance.getStack();
    }
}
