package org.tarclient.addon.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.List;

public class TarPlayerUtils {
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
}
