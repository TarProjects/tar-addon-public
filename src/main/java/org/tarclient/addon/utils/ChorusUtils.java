package org.tarclient.addon.utils;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ChorusUtils {
    public static float landingPercentage(ClientWorld world, Vec3d eater, Vec3d target, int targetDistance) {
        double fx = eater.x - Math.floor(eater.x);
        double fz = eater.z - Math.floor(eater.z);

        int eaterBlockX = (int) Math.floor(eater.x);
        int eaterBlockZ = (int) Math.floor(eater.z);
        int targetBlockX = (int) Math.floor(target.x);
        int targetBlockZ = (int) Math.floor(target.z);

        int bottomY = world.getBottomY();
        int topYInclusive = bottomY + world.getHeight() - 1;

        double[] pX = new double[17];
        double[] pZ = new double[17];
        for (int off = -8; off <= 8; off++) {
            pX[off + 8] = probOffset(off, fx);
            pZ[off + 8] = probOffset(off, fz);
        }

        double L = Math.max(eater.y - 8.0, bottomY);
        L = Math.min(L, topYInclusive);
        double R = Math.min(eater.y + 8.0, topYInclusive);
        R = Math.max(R, bottomY);
        double yRangeLength = R - L;
        if (yRangeLength <= 0.0) return 0.0f;

        double totalSuccessWeight = 0.0;
        double desiredWeight = 0.0;

        for (int k = -8; k <= 8; k++) {
            double pxk = pX[k + 8];
            if (pxk == 0.0) continue;

            for (int l = -8; l <= 8; l++) {
                double pzl = pZ[l + 8];
                if (pzl == 0.0) continue;

                int colX = eaterBlockX + k;
                int colZ = eaterBlockZ + l;

                List<Integer> platforms = findPlatforms(world, colX, colZ, bottomY, topYInclusive);
                if (platforms.isEmpty()) continue;

                double columnSuccessProb = 0.0;

                for (int idx = 0; idx < platforms.size(); idx++) {
                    int platformY = platforms.get(idx);
                    int nextHigher = (idx + 1 < platforms.size()) ? platforms.get(idx + 1) - 1 : topYInclusive;

                    double low = Math.max(platformY, L);
                    double high = Math.min(nextHigher, R);
                    if (high > low) {
                        double probYInInterval = (high - low) / yRangeLength;

                        if (isSpaceClear(world, colX, colZ, platformY, topYInclusive)) {
                            columnSuccessProb += probYInInterval;
                        }
                    }
                }

                if (columnSuccessProb == 0.0) continue;

                double columnWeight = pxk * pzl * columnSuccessProb;
                totalSuccessWeight += columnWeight;

                int dx = colX - targetBlockX;
                int dz = colZ - targetBlockZ;
                double euclidean = Math.sqrt(dx * dx + dz * dz);

                if (euclidean <= targetDistance) {
                    desiredWeight += columnWeight;
                }
            }
        }

        if (totalSuccessWeight == 0.0) return 0.0f;
        return (float) (desiredWeight / totalSuccessWeight);
    }

    private static List<Integer> findPlatforms(ClientWorld world, int x, int z, int bottomY, int topYInclusive) {
        List<Integer> platforms = new ArrayList<>();
        for (int y = bottomY; y <= topYInclusive; y++) {
            BlockPos pos = new BlockPos(x, y, z);

            BlockState state = world.getBlockState(pos);
            if (state.isSolidBlock(world, pos)) {
                if (isSpaceClear(world, x, z, y, topYInclusive)) {
                    platforms.add(y);
                }
            }
        }
        return platforms;
    }

    private static boolean isSpaceClear(ClientWorld world, int x, int z, int platformY, int topYInclusive) {
        BlockPos above1 = new BlockPos(x, platformY + 1, z);
        if (!world.isPosLoaded(above1)) return false;
        BlockState state1 = world.getBlockState(above1);
        FluidState fluid1 = world.getFluidState(above1);
        if (state1.isSolidBlock(world, above1) || !fluid1.isEmpty()) return false;

        if (platformY + 2 <= topYInclusive) {
            BlockPos above2 = new BlockPos(x, platformY + 2, z);
            if (!world.isPosLoaded(above2)) return false;
            BlockState state2 = world.getBlockState(above2);
            FluidState fluid2 = world.getFluidState(above2);
            return !state2.isSolidBlock(world, above2) && fluid2.isEmpty();
        }
        return true;
    }

    private static double probOffset(int offset, double frac) {
        double low = offset - frac;
        double high = offset + 1.0 - frac;
        double a = Math.max(low, -8.0);
        double b = Math.min(high, 8.0);
        return (b > a) ? (b - a) / 16.0 : 0.0;
    }
}
