package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.modules.VirtualHotbar;
import org.tarclient.addon.utils.VirtualHotbarUtils;

// mixin moment, have to suppress warnings...
@SuppressWarnings("ConstantConditions")
@Mixin(HandledScreen.class)
public class HandledScreenMixin<T extends ScreenHandler> {
    @Shadow
    @Final
    protected T handler;

    @Redirect(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack modifyItemStack(Slot instance) {
        if (!((Object) this instanceof InventoryScreen)) return instance.getStack();
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
        if (!((Object) this instanceof InventoryScreen) || !(this.handler instanceof PlayerScreenHandler)) {
            instance.clickSlot(syncId, slotId, button, actionType, player);
            return;
        }

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

    @Redirect(method = "drawMouseoverTooltip", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;focusedSlot:Lnet/minecraft/screen/slot/Slot;", opcode = Opcodes.GETFIELD))
    private Slot redirectFocusedSlot(HandledScreen<?> screen) {
        Slot original = ((HandledScreenAccessor) screen).getFocusedSlot();
        // dont check null objects for anything, outside of screen scope
        if (original == null) return null;

        if (!((Object) this instanceof InventoryScreen)) return original;
        if (!(original.inventory instanceof PlayerInventory)) return original;

        VirtualHotbar virtualHotbar = Modules.get().get(VirtualHotbar.class);
        if (virtualHotbar != null && virtualHotbar.isActive()) {
            if (original.id >= 36 && original.id < 45) {
                int virtual = VirtualHotbarUtils.realToVisual(original.id - 36);
                return screen.getScreenHandler().getSlot(virtual + 36);
            }
        }

        return original;
    }
}
