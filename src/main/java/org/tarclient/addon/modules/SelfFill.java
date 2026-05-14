package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.BurrowUtils;

import java.util.List;

import static org.tarclient.addon.utils.BurrowUtils.checkHead;
import static org.tarclient.addon.utils.BurrowUtils.getCeiledBlockPos;

public class SelfFill extends TarModule {
    // Le china
    // TODO: make this work with math instead of meth, other blocks as well?
    // Note: these values make a jump reach a height of 1 - 1e-7 from the starting block, so its pretty precise
    private final static double gravity = 0.98;
    private final static double minus = 0.08;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAttack = this.settings.createGroup("Attack");
    private final SettingGroup sgBypass = this.settings.createGroup("Bypass");
    private final SettingGroup sgBlocks = this.settings.createGroup("Blocks");


    private final Setting<Boolean> autodisable = sgGeneral.add(new BoolSetting.Builder()
        .name("autodisable")
        .description("This module disables itself if it is unable to burrow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("To not burrow multiple times")
        .defaultValue(3)
        .sliderRange(0, 20)
        .visible(() -> !autodisable.get())
        .build()
    );

    /* --- Attack --- */

    private final Setting<Boolean> attack = sgAttack.add(new BoolSetting.Builder()
        .name("attack")
        .description("Attacks crystals if they are in the way")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgAttack.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates for attacking")
        .defaultValue(true)
        .build()
    );


    private final Setting<Integer> attackCooldown = sgAttack.add(new IntSetting.Builder()
        .name("attack-cooldown")
        .description("Prevents attacking multiple times")
        .defaultValue(1)
        .sliderRange(0, 20)
        .build()
    );

    /* --- Bypass --- */
    private final Setting<Boolean> onground = sgBypass.add(new BoolSetting.Builder()
        .name("on-ground")
        .description("Spoofs on-ground value. Set this to whichever you want the onground value to be.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ongroundtwo = sgBypass.add(new BoolSetting.Builder()
        .name("on-ground-two")
        .description("Spoofs on-ground value. Set this to whichever you want the onground value to be.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> iterations = sgBypass.add(new IntSetting.Builder()
        .name("iterations")
        .description("How many iterations to do. Keep as low as possible without flagging")
        .defaultValue(3)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> offset = sgBypass.add(new DoubleSetting.Builder()
        .name("offset")
        .description("Offset which is used while burrowing. Leave as 10 if unsure.")
        .defaultValue(10)
        .sliderRange(-10, 10)
        .build()
    );

    /* --- Blocks --- */
    private final Setting<List<Block>> blocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Primary blocks to use")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );
    private final Setting<List<Block>> backupBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("backup-blocks")
        .description("Secondary blocks")
        .defaultValue(Blocks.ENDER_CHEST)
        .build()
    );

    BlockPos start;
    int ticks;
    int attackTicks;

    public SelfFill() {
        super(TarAddon.CATEGORY, "self-fill", "Sets you inside a block. This module is currently designed for crystalpvp.cc!");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) {
            this.toggle();
            return;
        }
        start = getCeiledBlockPos();
        ticks = 0;
        attackTicks = 0;

        if (autodisable.get()) {
            FindItemResult block = findPlaceable();
            if (!block.found()) {
                error("No valid blocks found in hotbar, disabling!");
                this.toggle();
                return;
            }

            if (canBurrow() && checkHead()) {
                tryBurrow(block.slot());
            }
            this.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || autodisable.get() || !getCeiledBlockPos().equals(start)) {
            this.toggle();
            return;
        }

        FindItemResult block = findPlaceable();
        if (!block.found()) {
            error("No valid blocks found in hotbar, disabling!");
            this.toggle();
            return;
        }

        if (attack.get()) {
            if (attackTicks > 0) {
                attackTicks--;
            } else {
                boolean attacked = false;
                for (Entity entity : mc.world.getEntities()) {
                    if (!(entity instanceof EndCrystalEntity)) continue;
                    if (Box.from(new BlockBox(getCeiledBlockPos())).intersects(entity.getBoundingBox())) {
                        if (rotate.get()) {
                            Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity),
                                () -> attack(entity));
                        } else {
                            attack(entity);
                        }

                        attacked = true;
                    }
                }

                if (attacked) {
                    attackTicks = attackCooldown.get();
                    return;
                }
            }
        }

        if (ticks > 0) {
            ticks--;
            return;
        }

        if (!mc.player.isOnGround() || !canBurrow() || !checkHead()) {
            return;
        }

        tryBurrow(block.slot());

        ticks = cooldown.get();
    }


    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.player == null) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public void tryBurrow(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.getItem() instanceof BlockItem blockItem) {
            int iterations = this.iterations.get();
            double height = BurrowUtils.findBlockHeight(blockItem);
            double remainder = Math.ceil(mc.player.getY()) - mc.player.getY();
            double velocity = findBurrowVelocity(height + remainder - 1e-7, iterations); // magic, burrow into blockHeight - 1e-7 to bypass collision
            burrow(slot, velocity, iterations);
            info("Burrowed!");
        } else {
            throw new IllegalStateException("Slot mismatch!");
        }
    }

    public FindItemResult findPlaceable() {
        FindItemResult block = InvUtils.findInHotbar(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem) {
                Block itemBlock = ((BlockItem) itemStack.getItem()).getBlock();
                return blocks.get().contains(itemBlock);
            }
            return false;
        });
        if (!block.found()) {
            // backup
            block = InvUtils.findInHotbar(itemStack -> {
                if (itemStack.getItem() instanceof BlockItem) {
                    Block itemBlock = ((BlockItem) itemStack.getItem()).getBlock();
                    return backupBlocks.get().contains(itemBlock);
                }
                return false;
            });

            if (!block.found()) {
                return new FindItemResult(-1, 0);
            }
        }

        return block;
    }

    public void burrow(int slot, double velocity, int iterations) {
        double y = 0.0;

        for (int i = 0; i < iterations; i++) {
            y = y + velocity;
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + y, mc.player.getZ(), mc.player.getYaw(), 90, onground.get(), mc.player.horizontalCollision));
            velocity = (velocity - minus) * gravity;
        }

        InvUtils.swap(slot, true);
        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(getCeiledBlockPos().down().toCenterPos(), Direction.UP, getCeiledBlockPos().down(), false), 0));
        InvUtils.swapBack();

        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + y + offset.get(), mc.player.getZ(), ongroundtwo.get(), mc.player.horizontalCollision));
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private double findBurrowVelocity(double targetHeight, int iterations) {
        double A = 0.08;
        double B = 0.98;
        double C = targetHeight;
        double N = iterations;

        double BN = Math.pow(B, N);
        double numerator = C * (1 - B) * (1 - B) + A * B * (BN - N * B + N - 1);
        double denominator = (1 - B) * (1 - BN);
        return numerator / denominator;
    }

    private boolean canBurrow() {
        BlockPos pos = getCeiledBlockPos();
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent())) return false;
        if (mc.world.getBlockState(pos.down()).isReplaceable()) return false;
        return mc.player.isOnGround();
    }

    private boolean canPlace(BlockState state, BlockPos pos, ShapeContext context) {
        VoxelShape voxelShape = state.getCollisionShape(mc.world, pos, context);
        return voxelShape.isEmpty() || mc.world.doesNotIntersectEntities(mc.player, voxelShape.offset(pos.getX(), pos.getY(), pos.getZ()));
    }
}
