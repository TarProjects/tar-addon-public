package org.tarclient.addon.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class RenderUtils {
    @SuppressWarnings("DuplicateBranchesInSwitch")
    public static Box getBox(BlockPos pos, double progress, BreakAnimation animation) {
        double minX, minY, minZ, maxX, maxY, maxZ;

        switch (animation) {
            case Static:
                minX = pos.getX();
                minY = pos.getY();
                minZ = pos.getZ();
                maxX = pos.getX() + 1;
                maxY = pos.getY() + 1;
                maxZ = pos.getZ() + 1;
                break;

            case Grow:
                double centerX = pos.getX() + 0.5;
                double centerY = pos.getY() + 0.5;
                double centerZ = pos.getZ() + 0.5;
                double halfSize = 0.5 * progress;
                minX = centerX - halfSize;
                minY = centerY - halfSize;
                minZ = centerZ - halfSize;
                maxX = centerX + halfSize;
                maxY = centerY + halfSize;
                maxZ = centerZ + halfSize;
                break;

            case Shrink:
                double shrink = 0.5 * (1 - progress);
                minX = pos.getX() + shrink;
                minY = pos.getY() + shrink;
                minZ = pos.getZ() + shrink;
                maxX = pos.getX() + 1 - shrink;
                maxY = pos.getY() + 1 - shrink;
                maxZ = pos.getZ() + 1 - shrink;
                break;

            case Up:
                minX = pos.getX();
                minZ = pos.getZ();
                maxX = pos.getX() + 1;
                maxZ = pos.getZ() + 1;
                minY = pos.getY();
                maxY = pos.getY() + progress;
                break;

            default:
                minX = pos.getX();
                minY = pos.getY();
                minZ = pos.getZ();
                maxX = pos.getX() + 1;
                maxY = pos.getY() + 1;
                maxZ = pos.getZ() + 1;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public enum BreakAnimation {
        Static,
        Grow,
        Shrink,
        Up
    }
}
