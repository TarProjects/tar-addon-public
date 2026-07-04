package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.ArrayList;
import java.util.List;


public class PearlCancel extends TarModule {
    public PearlCancel() {
        super(TarAddon.CATEGORY, "pearl-cancel", "Cancels pearl throw when shift is pressed");
    }

    private final List<PlayerPositionLookS2CPacket> packets = new ArrayList<>();
    private boolean cancelling;
    private int pearlId;

    @Override
    public void onActivate() {
        packets.clear();
        cancelling = false;
        pearlId = -999;
    }

    // TODO: refactor?
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        Entity entity = mc.world.getEntityById(pearlId);
        if (entity == null) {
            // find new pearl id
            pearlId = -999;
        }

        // just some basic checks, don't ask why .age
        boolean exists = entity != null && entity.isAlive() && entity.age > 0;


        if (mc.player.isOnGround() && mc.options.sneakKey.isPressed() && !cancelling && exists) {
            info("Started cancelling!");
            // start cancelling
            cancelling = true;
            // trigger ncp flag with y+9
            sendPacketSilent(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 9, mc.player.getZ(), false, mc.player.horizontalCollision));
        }

        if (!mc.options.sneakKey.isPressed() && cancelling) {
            cancelling = false;
            for (int i = 0; i < packets.size(); i++) {
                PlayerPositionLookS2CPacket packet = packets.get(i);
                if (i == packets.size() - 1) {
                    // last packet, fully handle
                    packet.apply(mc.getNetworkHandler());
                } else {
                    // normal packets: accept blah blah blah
                    sendPacketSilent(new TeleportConfirmC2SPacket(packet.teleportId()));
                    sendPacketSilent(new PlayerMoveC2SPacket.Full(packet.change().position().getX(), packet.change().position().getY(), packet.change().position().getZ(), packet.change().yaw(), packet.change().pitch(), false, false));
                }
            }
            packets.clear();
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (cancelling) {
            ((IVec3d) event.movement).meteor$set(0, 0, 0);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket && cancelling) {
            event.cancel();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;

        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            if (cancelling) {
                info("CANCELLED");

                packets.add(packet);
                event.cancel();
            }
        }

        if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (pearlId != -999) return;
            if (packet.getEntityType() != EntityType.ENDER_PEARL) return;
            if (packet.getEntityData() != mc.player.getId()) return;
            pearlId = packet.getEntityId();
        }
    }
}
