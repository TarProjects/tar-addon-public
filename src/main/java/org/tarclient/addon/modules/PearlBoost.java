package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.TarPlayerUtils;


public class PearlBoost extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> stepHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("step-height")
        .description("Step height")
        .defaultValue(2)
        .sliderRange(0, 3)
        .build()
    );

    public final Setting<Boolean> bow = sgGeneral.add(new BoolSetting.Builder()
        .name("bow")
        .defaultValue(false)
        .build()
    );


    public PearlBoost() {
        super(TarAddon.CATEGORY, "pearl-boost", "Boosts a pearl throw if possible through stepping");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!mc.isOnThread()) return;
        if (mc.player == null || mc.world == null) return;
        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            if (mc.player.getStackInHand(packet.getHand()).isOf(Items.ENDER_PEARL)) {
                if (doboost(mc.player, mc.world, packet.getYaw(), packet.getPitch())) {
                    event.cancel();
                    event.sendSilently(packet); // interact
                    sendRotatePacket(packet.getYaw(), packet.getPitch(), RotationPacket.Full); // step back
                }
            }
        }
        if (event.packet instanceof PlayerActionC2SPacket packet && packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            if (bow.get() && mc.player.getStackInHand(mc.player.getActiveHand()).isOf(Items.BOW)) {
                if (doboost(mc.player, mc.world, mc.player.getYaw(), mc.player.getPitch())) {
                    event.cancel();
                    event.sendSilently(packet); // interact
                    sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full); // step back
                }
            }
        }
    }

    private boolean doboost(PlayerEntity player, ClientWorld world, float yaw, float pitch) {
        if (!player.isOnGround()) return false; // cant clip not on ground
        // epearl, boost
        Vec3d clipPos = TarPlayerUtils.findStepPosition(player, world, stepHeight.get());
        if (clipPos == null) return false;

        sendPacket(new PlayerMoveC2SPacket.Full(clipPos, yaw, pitch,false, player.horizontalCollision));
        return true;
    }
}
