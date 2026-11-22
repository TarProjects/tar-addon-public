package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.tarclient.addon.modules.Packets;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @ModifyVariable(method = "sendMovementPackets", at = @At("STORE"), ordinal = 1)
    private boolean tar$setRotating(boolean rotating) {
        // second bool
        Packets packets = Modules.get().get(Packets.class);
        if (packets != null && packets.isActive()) {
            if (packets.shouldSendStaleRotation()) {
                // remove stale rotation by sending here
                return true;
            }
        }
        return rotating;
    }
}
