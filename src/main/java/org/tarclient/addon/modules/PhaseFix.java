package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.PlayerJumpEvent;

import static org.tarclient.addon.utils.BurrowUtils.*;

public class PhaseFix extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();


    private final Setting<Boolean> onGround = sgGeneral.add(new BoolSetting.Builder()
        .name("on-ground")
        .description("Spoofs on-ground value. Set this to whichever you want the onground value to be.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fly = sgGeneral.add(new BoolSetting.Builder()
        .name("fly")
        .description("Flies inside blocks")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> flySpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("fly-speed")
        .description("The speed that you fly at")
        .defaultValue(0.03)
        .sliderRange(0, 10)
        .visible(fly::get)
        .build()
    );

    private final Setting<Boolean> wiggle = sgGeneral.add(new BoolSetting.Builder()
        .name("wiggle")
        .description("Wiggles you if you dont move")
        .defaultValue(true)
        .visible(fly::get)
        .build()
    );

    private final Setting<Boolean> vClipJump = sgGeneral.add(new BoolSetting.Builder()
        .name("v-clip-jump")
        .description("VClips when jumping inside phase.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> vClipJumpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("v-clip-jump-delay")
        .description("The delay on where to cancel jumps (after vclipping)")
        .defaultValue(10)
        .sliderRange(0, 20)
        .visible(vClipJump::get)
        .build()
    );

    int delay = 0;
    boolean wiggleBack = false;

    public PhaseFix() {
        super(TarAddon.CATEGORY, "phase-fix", "Fixes some issues regarding phase. For testing only!");
    }

    @Override
    public void onActivate() {
        delay = 0;
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (!Utils.canUpdate() || isNotSurvival()) return;

        if (isBurrowed() && fly.get()) {
            Vec3d vel = PlayerUtils.getHorizontalVelocity(flySpeed.get());

            if (vel.length() == 0 && wiggle.get()) {
                // NOT MOVING; WIGGLE
                Vec3d blockPos = getSpecialBlockPos().toCenterPos();

                double dx = blockPos.getX() - mc.player.getX();
                double dz = blockPos.getZ() - mc.player.getZ();

                // 2d length
                double len = Math.sqrt(dx * dx + dz * dz);
                double x;
                double z;
                if (len < 1e-5) {
                    x = 1;
                    z = 0;
                } else {
                    x = dx / len;
                    z = dz / len;
                }

                vel = new Vec3d(x, 0, z).multiply(flySpeed.get() / 20);

                if (wiggleBack) {
                    vel = vel.negate();
                }
            }

            ((IVec3d) event.movement).meteor$set(vel.x, event.movement.y, vel.z);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        wiggleBack = !wiggleBack;
        // stupid
        if (delay > 0) {
            delay--;
        }
    }


    @EventHandler
    private void onJump(PlayerJumpEvent event) {
        if (!Utils.canUpdate() || isNotSurvival() || !vClipJump.get()) return;

        // Prevent jumping instantly after vclipping
        if (delay > 0) {
            event.cancel();
            return;
        }

        if (isBurrowed() && checkHead()) {
            delay = vClipJumpDelay.get();
            event.cancel();

            BlockPos blockPos = getSpecialBlockPos();

            Block current = mc.world.getBlockState(blockPos).getBlock();
            double offset = findBlockHeight(current);
            double y = blockPos.getY() + offset;

            mc.player.setPosition(mc.player.getX(), y, mc.player.getZ());
            // Packet will be sent on tick anyways, stop flaggin with this?
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!Utils.canUpdate() || isNotSurvival()) return;

        if (event.packet instanceof PlayerMoveC2SPacket && isBurrowed()) {
            ((PlayerMoveC2SPacketAccessor) event.packet).meteor$setOnGround(onGround.get());
        }
    }

    private boolean isNotSurvival() {
        return mc.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL;
    }
}
