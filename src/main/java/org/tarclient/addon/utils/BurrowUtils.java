package org.tarclient.addon.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BurrowUtils {
    public static Set<Block> BURROW_BLOCKS = new HashSet<>(Arrays.asList(
        Blocks.BEDROCK,
        Blocks.OBSIDIAN,
        Blocks.ENDER_CHEST,
        Blocks.ANVIL,
        Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL
    ));

    public static double findBlockHeight(BlockItem blockItem) {
        return findBlockHeight(blockItem.getBlock());
    }

    public static double findBlockHeight(Block block) {
        BlockState blockState = block.getDefaultState();
        VoxelShape collision = blockState.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
        return collision.getMax(Direction.Axis.Y);
    }

    public static boolean checkHead() {
        if (mc.world == null || mc.player == null) return false;
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

    public static boolean isBurrowed() {
        if (mc.world == null || mc.player == null) return false;

        Block current = mc.world.getBlockState(getCeiledBlockPos()).getBlock();
        return BURROW_BLOCKS.contains(current);
    }

    public static BlockPos getCeiledBlockPos() {
        if (mc.player == null) return BlockPos.ORIGIN;
        int x = MathHelper.floor(mc.player.getX());
        int y = MathHelper.ceil(mc.player.getY());
        int z = MathHelper.floor(mc.player.getZ());
        return new BlockPos(x, y, z);
    }
}
