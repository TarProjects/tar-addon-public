package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.modules.InventoryFixes;

@Mixin(Element.class)
public interface ElementMixin {
    @Inject(method = "mouseMoved", at = @At("HEAD"))
    private void onMouseMoved(double mouseX, double mouseY, CallbackInfo ci) {
        InventoryFixes inventoryFixes = Modules.get().get(InventoryFixes.class);
        if (inventoryFixes == null || !inventoryFixes.isActive()) return;

        if (this instanceof InventoryScreen screen) {
            Slot current = ((HandledScreenAccessor) screen).tar$getSlotAt(mouseX, mouseY);

            inventoryFixes.onMouseMovement(screen, current);
        }
    }
}
