package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BurrowUtility {

    public static boolean checkHead() {
        BlockState blockState1 = mc.world.getBlockState(new BlockPos.Mutable(mc.player.getX() + 0.3, mc.player.getY() + 2.3, mc.player.getZ() + 0.3));
        BlockState blockState2 = mc.world.getBlockState(new BlockPos.Mutable(mc.player.getX() + 0.3, mc.player.getY() + 2.3, mc.player.getZ() - 0.3));
        BlockState blockState3 = mc.world.getBlockState(new BlockPos.Mutable(mc.player.getX() - 0.3, mc.player.getY() + 2.3, mc.player.getZ() - 0.3));
        BlockState blockState4 = mc.world.getBlockState(new BlockPos.Mutable(mc.player.getX() - 0.3, mc.player.getY() + 2.3, mc.player.getZ() + 0.3));
        boolean air1 = blockState1.isReplaceable();
        boolean air2 = blockState2.isReplaceable();
        boolean air3 = blockState3.isReplaceable();
        boolean air4 = blockState4.isReplaceable();
        return air1 && air2 && air3 && air4;
    }

    public static boolean burrowedObsidian() {
        return mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.OBSIDIAN;
    }
}
