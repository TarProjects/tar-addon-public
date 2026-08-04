package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
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
import org.tarclient.addon.events.ModifyRotationCameraPosEvent;

import java.util.List;

/**
 * @concept levent
 * @author nullable
 */
public class CrystalWaster extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

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

    private final Setting<Boolean> spoofRotation = sgRender.add(new BoolSetting.Builder()
        .name("spoof-rotation")
        .description("Spoofs rotations to be relative to the real position")
        .defaultValue(true)
        .build()
    );

    /* --- Render --- */
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Should we render the box?")
        .defaultValue(false)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> safeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("safe-side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> safeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("safe-line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> unsafeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("unsafe-side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> unsafeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("unsafe-line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(render::get)
        .build()
    );


    Vec3d spoofedPosition = null;
    Stage stage = Stage.Wait;
    int stageTicks = 0;

    Box renderBox;

    public CrystalWaster() {
        super(TarAddon.CATEGORY, "crystal-waster", "Wastes opponents crystals. Disables on vertical move");
    }

    @EventHandler
    private void onModifyRotationCameraPos(ModifyRotationCameraPosEvent event) {
        if (spoofedPosition != null && spoofRotation.get()) {
            // spoofing, should spoof rot
            ((IVec3d) event.pos).meteor$set(spoofedPosition);
        }
    }

    @Override
    public void onActivate() {
        if (mc.player == null || !mc.player.isOnGround()) return;

        spoofedPosition = null;
        stage = Stage.Wait;
        stageTicks = 0;

        renderBox = mc.player.getBoundingBox();
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

        if (Math.abs(mc.player.getY() - mc.player.lastY) > 1e-5) {
            // vertical
            spoofedPosition = null;

            sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);
            renderBox = mc.player.getBoundingBox();

            stage = Stage.Wait;
            stageTicks = 0;
            return;
        }

        switch (stage) {
            case Wait -> {
                renderBox = mc.player.getBoundingBox();
                if (PlayerUtils.isMoving() || !mc.player.isOnGround()) {
                    return;
                }

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
                    renderBox = mc.player.getBoundingBox();
                    return;
                }

                spoofedPosition = clipPos;

                // sends move packet to sync if player is not moving... (then only on-ground will be sent every 20 ticks)
                sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);

                // move render box into clip position
                // we could also create a new box by using width height and depth on
                // a 3d vec as the center
                renderBox = mc.player.getBoundingBox().offset(clipPos.subtract(mc.player.getEntityPos()));

                stage = Stage.Down;
                stageTicks = 0;
            }
            case Down -> {
                spoofedPosition = null;

                sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);
                // set rendering back
                renderBox = mc.player.getBoundingBox();

                stage = Stage.Wait;
                stageTicks = 0;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderBox == null) return;
        boolean isWasting = spoofedPosition != null;
        Color side = isWasting ? unsafeSideColor.get() : safeSideColor.get();
        Color line = isWasting ? unsafeLineColor.get() : safeLineColor.get();

        event.renderer.box(renderBox, side, line, shapeMode.get(), 0);
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
