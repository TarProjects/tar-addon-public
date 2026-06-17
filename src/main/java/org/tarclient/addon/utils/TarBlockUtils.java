package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Set;

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
        Direction side = getClosestPlaceSide(blockPos);


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

        return true;
    }

    public static double getClosestYaw(BlockPos pos) {
        Direction side = getClosestPlaceSide(pos);
        if (side == null) return Rotations.getYaw(pos);
        Vec3d hitPos = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return Rotations.getYaw(hitPos);
    }

    public static double getClosestPitch(BlockPos pos) {
        Direction side = getClosestPlaceSide(pos);
        if (side == null) return Rotations.getPitch(pos);
        Vec3d hitPos = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return Rotations.getPitch(hitPos);
    }

    public static double getSquaredDistanceClosest(BlockPos pos) {
        if (mc.player == null) return 0;
        Direction side = getClosestPlaceSide(pos);
        if (side == null) return mc.player.squaredDistanceTo(pos.toCenterPos());
        Vec3d hitPos = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return mc.player.squaredDistanceTo(hitPos);
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

    public static double getBlockBreakingSpeed(int slot, BlockState block) {
        double speed = mc.player.getInventory().getMainStacks().get(slot).getMiningSpeedMultiplier(block);

        if (speed > 1) {
            ItemStack tool = mc.player.getInventory().getStack(slot);

            int efficiency = Utils.getEnchantmentLevel(tool, Enchantments.EFFICIENCY);

            if (efficiency > 0 && !tool.isEmpty()) speed += efficiency * efficiency + 1;
        }

        if (StatusEffectUtil.hasHaste(mc.player)) {
            speed *= 1 + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2F;
        }

        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float k = switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };

            speed *= k;
        }

        if (mc.player.isSubmergedIn(FluidTags.WATER)) {
            speed *= mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        }

        if (!mc.player.isOnGround()) {
            speed /= 5.0F;
        }

        return speed;
    }

    // 6 adjacency
    public static boolean isAdjacentToAny(BlockPos pos, Set<BlockPos> set) {
        for (Direction direction : Direction.values()) {
            if (set.contains(pos.offset(direction))) return true;
        }
        return false;
    }

    public static boolean isAdjacentToAny(BlockPos pos, List<BlockPos> list) {
        for (Direction direction : Direction.values()) {
            if (list.contains(pos.offset(direction))) return true;
        }
        return false;
    }


    @FunctionalInterface
    public interface InteractRunnable {
        void run(BlockHitResult bhr);
    }
}
