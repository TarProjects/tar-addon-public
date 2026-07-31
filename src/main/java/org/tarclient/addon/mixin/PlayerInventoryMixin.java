package org.tarclient.addon.mixin;

import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.utils.VirtualHotbarUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Inject(method = "setSelectedSlot", at = @At("HEAD"))
    private void onSelectSlot(int slot, CallbackInfo ci) {
        if (slot < 0 || slot > 8) return;
        // called from non-vanilla methods like other clients/modules
        mc.execute(() -> {
            // apply virtual here
            VirtualHotbarUtils.virtualSelectedSlot =
                VirtualHotbarUtils.realToVisual(slot);
        });
    }
}
