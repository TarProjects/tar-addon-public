package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ViaVersionPacketCancel {
    public static Hand shouldCancelExtraMovePacket = null;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(ViaVersionPacketCancel.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onPacketSend(PacketEvent.Send event) {
        if (!MioUtils.mioCompatibility.patchViaVersion.get()) return;
        if (shouldCancelExtraMovePacket == null) return;
        // cancels extra sent on 1.20.5/6
        if (event.packet instanceof PlayerMoveC2SPacket.Full) {
            if (mc.player != null && mc.player.getStackInHand(shouldCancelExtraMovePacket).getComponents().contains(DataComponentTypes.FOOD)) {
                // edible, cancel
                event.cancel();
            }
        }
    }
}
