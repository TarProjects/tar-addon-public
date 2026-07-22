package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

public class ItemExplosionCalculator {

    public static boolean willCrystalDestroyItem(Vec3d crystalPos, Vec3d itemPos) {
        // vanilla crystal power
        float power = 6.0f;

        double half = 0.125;
        Box itemBox = new Box(
            itemPos.x - half, itemPos.y - half, itemPos.z - half,
            itemPos.x + half, itemPos.y + half, itemPos.z + half
        );

        double distance = itemPos.distanceTo(crystalPos);
        if (distance > 2.0 * power) {
            return false;
        }

        double exposure = computeExposure(crystalPos, itemBox);

        double impact = (1.0 - distance / (2.0 * power)) * exposure;
        if (impact < 0) impact = 0;

        float rawDamage = (float) (7.0 * power * (impact * impact + impact) + 1.0);

        return rawDamage >= 5.0f;
    }

    private static double computeExposure(Vec3d source, Box box) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1.0 / (xDiff * 2.0 + 1.0);
        double yStep = 1.0 / (yDiff * 2.0 + 1.0);
        double zStep = 1.0 / (zDiff * 2.0 + 1.0);

        if (xStep <= 0 || yStep <= 0 || zStep <= 0) {
            return 0.0;
        }

        int misses = 0;
        int hits = 0;

        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) * 0.5;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) * 0.5;

        xStep = xStep * xDiff;
        yStep = yStep * yDiff;
        zStep = zStep * zDiff;

        double startX = box.minX + xOffset;
        double startY = box.minY;
        double startZ = box.minZ + zOffset;
        double endX = box.maxX + xOffset;
        double endY = box.maxY;
        double endZ = box.maxZ + zOffset;

        for (double x = startX; x <= endX; x += xStep) {
            for (double y = startY; y <= endY; y += yStep) {
                for (double z = startZ; z <= endZ; z += zStep) {
                    Vec3d point = new Vec3d(x, y, z);

                    if (raycast(point, source, DamageUtils.HIT_FACTORY) == null) {
                        misses++;
                    }
                    hits++;
                }
            }
        }

        return (double) misses / hits;
    }


    private static BlockHitResult raycast(Vec3d start, Vec3d end, DamageUtils.RaycastFactory factory) {
        DamageUtils.ExposureRaycastContext context = new DamageUtils.ExposureRaycastContext(start, end);

        return BlockView.raycast(start, end, context, factory, (exposureRaycastContext -> null));
    }
}
