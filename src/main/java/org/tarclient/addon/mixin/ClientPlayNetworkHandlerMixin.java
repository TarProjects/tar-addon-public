package org.tarclient.addon.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.utils.VirtualHotbarUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void onApply(UpdateSelectedSlotS2CPacket packet, CallbackInfo ci) {
        if (mc.player == null) return;

        int serverSlot = packet.slot();

        // convert REAL -> VIRTUAL instead of letting vanilla apply it
        VirtualHotbarUtils.virtualSelectedSlot =
            VirtualHotbarUtils.realToVisual(serverSlot);

        // FORCE vanilla to still accept real slot internally
        ((PlayerInventoryAccessor) mc.player.getInventory())
            .tar$setSelectedSlot(serverSlot);

        ci.cancel(); // prevent vanilla overwrite logic
    }
}
