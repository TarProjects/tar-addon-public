package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.tarclient.addon.utils.MioUtils.getPacketMineDamage;

public class MiningUtils {
    private static Breaking breaking = null;
    private static Breaking queueBreak = null;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(MiningUtils.class);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
                breaking = new Breaking(packet.getPos(), packet.getDirection());
            } else if (packet.getAction() == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
                breaking = null;
            }
        }
    }

    @EventHandler
    private static void onTickPre(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        if (breaking != null) {
            breaking.ticks++;

            double damage = getPacketMineDamage();
            if (damage <= 0) damage = 1;

            BlockState state = mc.world.getBlockState(breaking.blockPos);

            if (state.isAir())
                state = breaking.lastState != null && !breaking.lastState.isAir() ? breaking.lastState : Blocks.OBSIDIAN.getDefaultState();

            if (state.getHardness(mc.world, breaking.blockPos) >= 0) {
                FindItemResult fir = InvUtils.findFastestTool(state);
                int slot = fir.found() ? fir.slot() : mc.player.getInventory().getSelectedSlot();

                double raw = TarBlockUtils.getBlockBreakingSpeed(slot, state);
                double adjustedRaw = raw / damage;


                breaking.deltaRunningCount = adjustedRaw;
                breaking.runningCount += adjustedRaw;
            } else {
                breaking.deltaRunningCount = 0;
            }

            breaking.lastState = state;
        }

        if (queueBreak != null) {
            internalAttackBlock(queueBreak.blockPos, queueBreak.direction);
            queueBreak = null;
        }
    }


    @EventHandler
    private static void onJoin(GameJoinedEvent event) {
        breaking = null;
    }

    public static void attackWithCompatibility(BlockPos target, Direction side) {
        MioUtils.resetPacketMine();
        internalAttackBlock(target, side);
    }

    private static void internalAttackBlock(BlockPos target, Direction side) {
        if (mc.world == null || mc.interactionManager == null) return;
        BlockState old = mc.world.getBlockState(target);
        mc.world.setBlockState(target, Blocks.OBSIDIAN.getDefaultState());

        mc.interactionManager.attackBlock(target, side);

        mc.world.setBlockState(target, old);
    }

    public static void resetMiningProgress() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        Breaking breaking = getBreaking();
        if (breaking == null) return;
        // stupid i know but i dont have a better way to do this
        queueBreak = breaking;
        MioUtils.resetPacketMine();
    }

    public static Breaking getBreaking() {
        return breaking;
    }

    public static BlockPos getBreakingBlockPos() {
        return breaking == null ? null : breaking.blockPos;
    }

    public static int getBreakingTicks() {
        return breaking == null ? 0 : breaking.ticks;
    }

    public static double getBreakingProgress(BlockState state) {
        if (breaking == null || state == null || state.isAir()) return 0;

        double required = state.getHardness(mc.world, breaking.blockPos) * 30.0;
        if (required <= 0) required = Double.POSITIVE_INFINITY;

        double newProgress = breaking.runningCount / required;
        return Math.clamp(newProgress, 0.0, 1.0);
    }

    public static double getBreakingProgress() {
        if (mc.world == null || breaking == null) return 0;
        BlockState state = mc.world.getBlockState(breaking.blockPos);
        if (state == null) return 0;

        if (state.isAir()) {
            state = breaking.lastState != null && !breaking.lastState.isAir() ? breaking.lastState : Blocks.OBSIDIAN.getDefaultState();
        }

        double required = state.getHardness(mc.world, breaking.blockPos) * 30.0;
        if (required <= 0) required = Double.POSITIVE_INFINITY;

        double newProgress = breaking.runningCount / required;
        return Math.clamp(newProgress, 0.0, 1.0);
    }

    public static Direction getBreakingDirection() {
        return breaking == null ? null : breaking.direction;
    }

    public static class Breaking {
        public BlockPos blockPos;
        public Direction direction;
        public int ticks;
        public double runningCount;
        public double deltaRunningCount;
        public BlockState lastState;

        public Breaking(BlockPos blockPos, Direction direction) {
            this.blockPos = blockPos;
            this.direction = direction;
            this.ticks = 0;
            this.runningCount = 0;
            this.deltaRunningCount = 0.0;
            this.lastState = null;
        }
    }

}
