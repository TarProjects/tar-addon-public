package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.UUID;

public class SpectatorInterfere extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level of flying")
        .defaultValue(60)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> idleTicks = sgGeneral.add(new IntSetting.Builder()
        .name("idle-ticks")
        .description("Amount of ticks before snapping")
        .defaultValue(40)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> survivalOffgroundTicks = sgGeneral.add(new IntSetting.Builder()
        .name("survival-offground-ticks")
        .description("Amount of ticks before going back to spec")
        .defaultValue(40)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> verticalCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-cooldown")
        .description("Cooldown on vertical movement")
        .defaultValue(2)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal movement speed")
        .defaultValue(25)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Double> scrollAmount = sgGeneral.add(new DoubleSetting.Builder()
        .name("scroll-amount")
        .description("Scroll speed change")
        .defaultValue(1)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<Double> minSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("Min speed")
        .defaultValue(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Max speed")
        .defaultValue(50)
        .sliderRange(0, 100)
        .build()
    );

    private Stage stage;
    private int vclipCooldown;
    private double currentSpeed;
    private int stageTicks;

    public SpectatorInterfere() {
        super(TarAddon.CATEGORY, "spectator-interfere", "Uhh some funsies to interfere with duels");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        if (!mc.player.isSpectator()) {
            error("This module requires spectator mode!");
            this.toggle();
            return;
        }
        stage = Stage.Spectator;
        vclipCooldown = 0;
        mc.player.setPosition(mc.player.getX(), mc.player.getBlockY(), mc.player.getZ());
        currentSpeed = speed.get();
        stageTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (stage == Stage.Survival) {
            switchToSpectator();
        }
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (event.value == 0) return;
        double change = event.value * scrollAmount.get();
        currentSpeed += change;
        currentSpeed = Math.clamp(currentSpeed, minSpeed.get(), maxSpeed.get());
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (stage != Stage.Spectator) {
            ((IVec3d) event.movement).meteor$set(0, 0, 0);
            return;
        }

        Vec3d horizontal = PlayerUtils.getHorizontalVelocity(currentSpeed);
        if (horizontal.horizontalLengthSquared() < 1e-5) {
            stageTicks++;
        } else {
            stageTicks = 0;
        }

        ((IVec3d) event.movement).meteor$set(horizontal.x, 0, horizontal.z);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        switch (stage) {
            case Spectator -> {
                if (stageTicks >= idleTicks.get()) {
                    int x = Math.toIntExact(Math.round(mc.player.getX()));
                    int y = (int) Math.floor(mc.player.getY());
                    int z = Math.toIntExact(Math.round(mc.player.getZ()));
                    if (isGround(x, y, z)) {
                        mc.player.setPosition(x, y, z);
                        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false));
                        info("Switching to survival!");
                        stage = Stage.WaitAndSwitchForSurvival;
                        stageTicks = 0;
                        return;
                    }
                }
                int vertical = 0;

                if (mc.options.jumpKey.isPressed()) vertical++;
                if (mc.options.sneakKey.isPressed()) vertical--;

                if (vclipCooldown > 0) {
                    vclipCooldown--;
                } else if (vertical != 0) {
                    vclipCooldown = verticalCooldown.get();
                    int newY = mc.player.getBlockY() + vertical;
                    if (newY >= minY.get()) {
                        mc.player.setPosition(mc.player.getX(), newY, mc.player.getZ());
                        stageTicks = 0;
                    }
                }
            }
            // TODO: refactor
            case WaitAndSwitchForSurvival -> {
                if (stageTicks == 0) {
                    // switch next tick lol, dont send pos & switch
                    switchToSurvival();
                } else {
                    if (mc.player.getGameMode() == GameMode.SURVIVAL) {
                        stage = Stage.Survival;
                    }
                }
                stageTicks++;
            }
            case Survival -> {
                if (!isGround((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ())) {
                    // not ground!
                    stageTicks++;
                } else {
                    // safe...
                    stageTicks = 0;
                }

                if (stageTicks >= survivalOffgroundTicks.get()) {
                    info("Too long offground, switching to spectator!");
                    switchToSpectator();
                    stage = Stage.WaitForSpectator;
                    return;
                }

                KeyBinding[] keys = {mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sneakKey};
                for (KeyBinding key : keys) {
                    if (key.isPressed()) {
                        info("Switching to spectator!");
                        switchToSpectator();
                        stage = Stage.WaitForSpectator;
                        break;
                    }
                }
            }
            case WaitForSpectator -> {
                if (mc.player.getGameMode() == GameMode.SPECTATOR) {
                    stage = Stage.Spectator;
                }
            }
        }
    }

    private void switchToSurvival() {
        if (mc.player == null) return;
        sendPacket(new SpectatorTeleportC2SPacket(UUID.fromString("d5de0000-b63b-23ed-98cf-758b15c9cf31")));
    }

    private void switchToSpectator() {
        if (mc.player == null) return;
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 10, mc.player.getZ(), false, false));
    }

    private boolean isGround(int x, int y, int z) {
        if (mc.world == null) return false;

        int groundY = y - 1;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(x - dx, groundY, z - dz);
                BlockState state = mc.world.getBlockState(pos);
                if (state.isFullCube(mc.world, pos)) {
                    return true;
                }
            }
        }
        return false;
    }


    enum Stage {
        Spectator,
        WaitAndSwitchForSurvival,
        Survival,
        WaitForSpectator
    }
}
