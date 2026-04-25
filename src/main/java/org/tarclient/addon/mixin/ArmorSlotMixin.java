package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tarclient.addon.modules.InventoryFixes;

@Mixin(targets = "net/minecraft/screen/slot/ArmorSlot")
public abstract class ArmorSlotMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void canInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        InventoryFixes inventoryFixes = Modules.get().get(InventoryFixes.class);
        if (inventoryFixes != null && inventoryFixes.isActive() && inventoryFixes.insertSlot.get()) {
            cir.setReturnValue(true);
        }
    }

}
