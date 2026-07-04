package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;


public class CrystalWaster extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between stepping")
        .defaultValue(5)
        .sliderRange(0, 30)
        .build()
    );

    private final Setting<Double> stepHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("step-height")
        .description("Step height")
        .defaultValue(2)
        .sliderRange(0, 3)
        .build()
    );

    Vec3d spoofedPosition = null;
    Stage stage = Stage.Wait;
    int stageTicks = 0;

    double startY;

    public CrystalWaster() {
        super(TarAddon.CATEGORY, "crystal-waster", "Wastes opponents crystals. Disables on vertical move");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || !mc.player.isOnGround()) return;

        spoofedPosition = null;
        stage = Stage.Wait;
        stageTicks = 0;

        startY = mc.player.getY();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;
        // send rot in desync cases
        sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (Math.abs(mc.player.getY() - startY) > 1e-5) {
            // moved up/down by 1e-5, toggle
            error("Disabled due to vertical movement");
            this.toggle();
            return;
        }

        if ((PlayerUtils.isMoving() || !mc.player.isOnGround()) && stage == Stage.Wait) {
            return;
        }

        switch (stage) {
            case Wait -> {
                stageTicks++;

                if (stageTicks >= delay.get()) {
                    stage = Stage.Up;
                    stageTicks = 0;
                }
            }
            case Up -> {
                Vec3d clipPos = findStepPosition(mc.player, mc.world, stepHeight.get());

                if (clipPos == null) {
                    stage = Stage.Wait;
                    stageTicks = 0;
                    return;
                }

                spoofedPosition = clipPos;

                // sends move packet to sync if player is not moving... (then only on-ground will be sent every 20 ticks)
                sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);

                stage = Stage.Down;
                stageTicks = 0;
            }
            case Down -> {
                spoofedPosition = null;

                sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);

                stage = Stage.Wait;
                stageTicks = 0;
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;

        if (event.packet instanceof PlayerMoveC2SPacket packet && spoofedPosition != null) {
            // spoof pos
            if (packet.changesPosition()) {
                event.cancel();

                if (!packet.changesLook()) {
                    event.sendSilently(new PlayerMoveC2SPacket.PositionAndOnGround(spoofedPosition, mc.player.isOnGround(), mc.player.horizontalCollision));
                } else {
                    event.sendSilently(new PlayerMoveC2SPacket.Full(spoofedPosition, packet.getYaw(mc.player.getYaw()), packet.getPitch(mc.player.getPitch()), mc.player.isOnGround(), mc.player.horizontalCollision));
                }
            }
        }
    }

    /**
     * returns null when position not found,
     * otherwise it will return the highest {@code Vec3d}
     * AFTER stepping has happened
     *
     * @return Vec3d
     */
    public static Vec3d findStepPosition(PlayerEntity player, World world, double stepHeight) {
        Box originalBox = player.getBoundingBox();
        Vec3d originalPos = player.getEntityPos();

        List<VoxelShape> emptyCollisions = List.of();

        double bestY = -1;
        Vec3d bestPos = null;

        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI / 4) * i;
            Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));

            Vec3d touchIntent = dir.multiply(0.1);
            Vec3d touchMove = Entity.adjustMovementForCollisions(player, touchIntent, originalBox, world, emptyCollisions);

            if (touchMove.squaredDistanceTo(touchIntent) > 1e-7) {

                Vec3d upIntent = new Vec3d(0, stepHeight, 0);
                Vec3d upMove = Entity.adjustMovementForCollisions(player, upIntent, originalBox, world, emptyCollisions);

                if (upMove.y > 0) {
                    Box raisedBox = originalBox.offset(upMove);

                    Vec3d forwardIntent = dir.multiply(0.2);
                    Vec3d forwardMove = Entity.adjustMovementForCollisions(player, forwardIntent, raisedBox, world, emptyCollisions);

                    Box forwardedBox = raisedBox.offset(forwardMove);

                    Vec3d downIntent = new Vec3d(0, -stepHeight, 0);
                    Vec3d downMove = Entity.adjustMovementForCollisions(player, downIntent, forwardedBox, world, emptyCollisions);

                    double netY = upMove.y + downMove.y;

                    if (netY > 1e-5) {
                        Vec3d targetPos = originalPos.add(upMove).add(forwardMove).add(downMove);

                        if (targetPos.y > bestY) {
                            bestY = targetPos.y;
                            bestPos = targetPos;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private enum Stage {
        Wait,
        Up,
        Down
    }
}
