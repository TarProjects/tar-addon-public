package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tarclient.addon.events.ClickSlotEvent;
import org.tarclient.addon.utils.ViaVersionPacketCancel;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void tar$onClickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        ClickSlotEvent event = ClickSlotEvent.get(syncId, slotId, button, actionType, player);
        if (MeteorClient.EVENT_BUS.post(event).isCancelled()) {
            ci.cancel();
        }
    }

    // ViaFabric patch, cannot mixin into mixin so fragile stuff here :)
    @Inject(method = "interactItem", at = @At("HEAD"))
    private void tar$overrideViaMixinPre(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ViaVersionPacketCancel.shouldCancelExtraMovePacket = hand;
    }

    @Inject(method = "interactItem", at = @At("TAIL"))
    private void tar$overrideViaMixinPost(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ViaVersionPacketCancel.shouldCancelExtraMovePacket = null;
    }
}
