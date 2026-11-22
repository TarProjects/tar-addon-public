package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tarclient.addon.events.ClickSlotEvent;
import org.tarclient.addon.events.InteractionManagerTickEvent;
import org.tarclient.addon.modules.Disabler;
import org.tarclient.addon.utils.ViaVersionPacketCancel;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void tar$onClickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        ClickSlotEvent event = ClickSlotEvent.get(syncId, slotId, button, actionType, player);
        if (MeteorClient.EVENT_BUS.post(event).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tar$interactionTick(CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(InteractionManagerTickEvent.get());
    }


    // Block Cache for disabler
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void tar$onInteractBlockHead(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (mc.world == null) return;
        Disabler disabler = Modules.get().get(Disabler.class);
        if (disabler == null || !disabler.isActive()) return;
        disabler.store(hitResult.getBlockPos(), mc.world.getBlockState(hitResult.getBlockPos()));
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void tar$onInteractBlockReturn(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        Disabler disabler = Modules.get().get(Disabler.class);
        if (disabler == null || !disabler.isActive()) return;
        disabler.clear();
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
