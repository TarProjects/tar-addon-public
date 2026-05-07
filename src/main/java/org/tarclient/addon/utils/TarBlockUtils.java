package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.Block;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.*;

public class TarBlockUtils {
    private TarBlockUtils() {
    }

    public static boolean place(BlockPos blockPos, Hand hand, boolean rotate, boolean swing, boolean airPlace, boolean checkEntities, Block toPlace) {
        if (rotate) {
            return place(blockPos, airPlace, checkEntities, toPlace, bhr -> {
                Rotations.rotate((float) Rotations.getYaw(bhr.getPos()), (float) Rotations.getPitch(bhr.getPos()), 100, () -> {
                    interact(bhr, hand, swing);
                });
            });
        } else {
            return place(blockPos, airPlace, checkEntities, toPlace, bhr -> interact(bhr, hand, swing));
        }
    }

    public static boolean place(BlockPos blockPos, boolean airPlace, boolean checkEntities, Block toPlace, InteractRunnable callback) {
        if (!canPlaceBlock(blockPos, checkEntities, toPlace)) return false;
        Vec3d hitPos = Vec3d.ofCenter(blockPos);

        BlockPos neighbour;
        Direction side = getPlaceSide(blockPos);


        if (side == null) {
            if (!airPlace) return false;
            side = Direction.UP;
            neighbour = blockPos;
        } else {
            neighbour = blockPos.offset(side);
            hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        }

        BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);


        callback.run(bhr);

        System.out.println(hitPos);

        return true;
    }

    public static HitResult raycastBlocks(double distance) {
        if (mc.getCameraEntity() == null || mc.world == null) return null;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();

        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        float f = MathHelper.cos(-yaw * ((float) Math.PI / 180) - (float) Math.PI);
        float g = MathHelper.sin(-yaw * ((float) Math.PI / 180) - (float) Math.PI);
        float h = -MathHelper.cos(-pitch * ((float) Math.PI / 180));
        float i = MathHelper.sin(-pitch * ((float) Math.PI / 180));
        Vec3d lookVec = new Vec3d(g * h, i, f * h);

        Vec3d endPos = cameraPos.add(lookVec.multiply(distance));


        RaycastContext context = new RaycastContext(
            cameraPos,
            endPos,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.getCameraEntity()
        );

        return mc.world.raycast(context);
    }


    @FunctionalInterface
    public interface InteractRunnable {
        void run(BlockHitResult bhr);
    }
}
