package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MiningUtils {
    private static BlockPos lastBreaking = null;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(MiningUtils.class);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void onStartBlockBreak(StartBreakingBlockEvent event) {
        lastBreaking = event.blockPos;
    }

    @EventHandler
    private static void onJoin(GameJoinedEvent event) {
        lastBreaking = null;
    }

    public static void attackWithCompatibility(BlockPos target, Direction side) {
        if (mc.world == null || mc.interactionManager == null) return;

        MioUtils.resetPacketMine();

        BlockState old = mc.world.getBlockState(target);
        mc.world.setBlockState(target, Blocks.OBSIDIAN.getDefaultState());

        mc.interactionManager.attackBlock(target, side);

        mc.world.setBlockState(target, old);
    }

    public static BlockPos getLastBreaking() {
        return lastBreaking;
    }
}
