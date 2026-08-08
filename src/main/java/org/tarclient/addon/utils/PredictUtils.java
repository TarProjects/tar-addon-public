package org.tarclient.addon.utils;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class PredictUtils {
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    private static final List<VoxelShape> EMPTY_SHAPE_LIST = List.of();

    public static void advanceOneTick(PlayerEntity player, SimulationState state, double stepHeight) {
        Vec3d vel = state.vel.add(0, -GRAVITY, 0);

        Vec3d newPos = simulateTick(player, state.pos, vel, stepHeight);
        Vec3d adjusted = newPos.subtract(state.pos);

        boolean collidedX = Math.abs(adjusted.x - vel.x) > 1e-6;
        boolean collidedY = Math.abs(adjusted.y - vel.y) > 1e-6;
        boolean collidedZ = Math.abs(adjusted.z - vel.z) > 1e-6;

        vel = new Vec3d(
            collidedX ? 0 : vel.x,
            collidedY ? 0 : vel.y,
            collidedZ ? 0 : vel.z
        );

        state.vel = new Vec3d(vel.x, vel.y * DRAG, vel.z);
        state.pos = newPos;
    }

    public static List<Vec3d> predict(PlayerEntity player, int ticks, double stepHeight) {
        Vec3d velocity = player.getEntityPos().subtract(player.lastX, player.lastY, player.lastZ);
        return predict(player, player.getEntityPos(), velocity, ticks, stepHeight);
    }

    public static List<Vec3d> predict(PlayerEntity player, Vec3d startPos, Vec3d initialVelocity, int ticks, double stepHeight) {
        List<Vec3d> positions = new ArrayList<>();
        Vec3d pos = startPos;
        Vec3d vel = initialVelocity;

        for (int i = 0; i < ticks; i++) {
            vel = vel.add(0, -GRAVITY, 0);

            Vec3d newPos = simulateTick(player, pos, vel, stepHeight);
            Vec3d adjusted = newPos.subtract(pos);

            boolean collidedX = Math.abs(adjusted.x - vel.x) > 1e-6;
            boolean collidedY = Math.abs(adjusted.y - vel.y) > 1e-6;
            boolean collidedZ = Math.abs(adjusted.z - vel.z) > 1e-6;

            boolean onGround = collidedY && vel.y < 0;

            vel = new Vec3d(
                collidedX ? 0 : vel.x,
                collidedY ? 0 : vel.y,
                collidedZ ? 0 : vel.z
            );

            float friction = onGround ? (0.91f * 0.6f) : 0.91f;
            vel = new Vec3d(vel.x * friction, vel.y * DRAG, vel.z * friction);

            positions.add(newPos);
            pos = newPos;
        }
        return positions;
    }

    private static Vec3d simulateTick(PlayerEntity player, Vec3d pos, Vec3d velocity, double stepHeight) {
        World world = player.getEntityWorld();

        Box box = player.getBoundingBox().offset(pos.subtract(player.getEntityPos()));
        Vec3d adjusted = Entity.adjustMovementForCollisions(player, velocity, box, world, EMPTY_SHAPE_LIST);

        boolean horizontalBlocked = Math.abs(adjusted.x - velocity.x) > 1e-6 || Math.abs(adjusted.z - velocity.z) > 1e-6;
        boolean verticalBlocked = Math.abs(adjusted.y - velocity.y) > 1e-6;
        boolean onGround = velocity.y < 0 && verticalBlocked;

        if (stepHeight > 0 && onGround && horizontalBlocked) {
            Box groundBox = box.offset(0, adjusted.y, 0);
            Box stepBox = groundBox.stretch(velocity.x, stepHeight, velocity.z);
            List<VoxelShape> collisions = Entity.findCollisions(player, world, stepBox);

            if (collisions.isEmpty()) {
                return pos.add(adjusted);
            }

            float[] stepHeights = collectStepHeights(groundBox, collisions, (float)adjusted.y, (float)stepHeight);
            Vec3d bestStep = adjusted;

            for (float yOffset : stepHeights) {
                Vec3d stepMovement = new Vec3d(velocity.x, yOffset, velocity.z);
                Vec3d stepAdjusted = Entity.adjustMovementForCollisions(player, stepMovement, groundBox, world, collisions);

                if (stepAdjusted.horizontalLengthSquared() > bestStep.horizontalLengthSquared()) {
                    bestStep = stepAdjusted.add(0, adjusted.y, 0);
                }
            }
            adjusted = bestStep;
        }

        return pos.add(adjusted);
    }

    private static float[] collectStepHeights(Box box, List<VoxelShape> collisions, float currentY, float stepHeight) {
        FloatSet floatSet = new FloatArraySet(4);
        for (VoxelShape shape : collisions) {
            DoubleList yPoints = shape.getPointPositions(Direction.Axis.Y);
            for (double y : yPoints) {
                float g = (float)(y - box.minY);
                if (g >= 0 && g != currentY && g <= stepHeight) {
                    floatSet.add(g);
                }
            }
        }
        float[] fs = floatSet.toFloatArray();
        FloatArrays.unstableSort(fs);
        return fs;
    }

    public static Vec3d predictLandingPoint(ProjectileEntity projectile, int maxSteps) {
        Vec3d pos = projectile.getEntityPos();
        Vec3d vel = projectile.getVelocity();
        World world = projectile.getEntityWorld();

        for (int i = 0; i < maxSteps; i++) {
            vel = vel.add(0, -0.03, 0);

            vel = vel.multiply(0.99);

            Vec3d newPos = pos.add(vel);

            HitResult hitResult = world.raycast(new RaycastContext(
                pos, newPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                projectile
            ));

            if (hitResult.getType() != HitResult.Type.MISS) {
                return hitResult.getPos();
            }

            pos = newPos;
        }

        return null;
    }
}
