package org.tarclient.addon.mixin;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InventoryScreen.class)
public interface InventoryScreenAccessor {
    @Accessor("mouseX")
    float tar$getMouseX();

    @Accessor("mouseY")
    float tar$getMouseY();
}
