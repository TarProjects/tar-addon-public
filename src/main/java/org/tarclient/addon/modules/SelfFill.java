package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
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

import static org.tarclient.addon.utils.BurrowUtility.checkHead;

public class SelfFill extends TarModule {
    // Le china
    // TODO: make this work with math instead of meth, other blocks as well?
    // Note: these values make a jump reach a height of 1 - 1e-7 from the starting block, so its pretty precise
    private final static double value = 0.41954563664807;
    private final static int loop = 3;
    private final static double gravity = 0.98;
    private final static double minus = 0.08;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> onground = sgGeneral.add(new BoolSetting.Builder()
        .name("on-ground")
        .description("Spoofs on-ground value. Set this to whichever you want the onground value to be.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ongroundtwo = sgGeneral.add(new BoolSetting.Builder()
        .name("on-ground-two")
        .description("Spoofs on-ground value. Set this to whichever you want the onground value to be.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
        .name("offset")
        .description("Offset which is used while burrowing. Leave as 10 if unsure.")
        .defaultValue(10)
        .sliderRange(-10, 10)
        .build()
    );

    private final Setting<Boolean> autodisable = sgGeneral.add(new BoolSetting.Builder()
        .name("autodisable")
        .description("This module disables itself if it is unable to burrow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> attack = sgGeneral.add(new BoolSetting.Builder()
        .name("attack")
        .description("Attacks crystals if they are in the way")
        .defaultValue(true)
        .visible(() -> !autodisable.get())
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates for attacking")
        .defaultValue(true)
        .visible(() -> !autodisable.get() && attack.get())
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("To not burrow multiple times")
        .defaultValue(15)
        .sliderRange(0, 20)
        .visible(() -> !autodisable.get())
        .build()
    );

    BlockPos start;
    int ticks;

    public SelfFill() {
        super(TarAddon.CATEGORY, "self-fill", "Sets you inside a block. This module is currently designed for crystalpvp.cc!");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) {
            this.toggle();
            return;
        }
        start = mc.player.getBlockPos();
        ticks = 0;

        if (autodisable.get()) {
            FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!obsidian.found()) {
                error("No obsidian found in hotbar, disabling");
                this.toggle();
                return;
            }

            if (canBurrow() && checkHead()) {
                burrow(obsidian.slot());
                info("Burrowed!");
            }
            this.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || autodisable.get() || !mc.player.getBlockPos().equals(start)) {
            this.toggle();
            return;
        }

        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) {
            error("No obsidian found in hotbar, disabling");
            this.toggle();
            return;
        }

        if (ticks > 0) {
            ticks--;
            return;
        }


        if (attack.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity)) continue;
                if (Box.from(new BlockBox(mc.player.getBlockPos())).intersects(entity.getBoundingBox())) {
                    if (rotate.get()) {
                        Rotations.rotate(Rotations.getPitch(entity), Rotations.getYaw(entity),
                            () -> sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking())));
                    } else {
                        sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                    }

                    sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                }
            }
        }

        if (!mc.player.isOnGround() || !canBurrow() || !checkHead()) {
            return;
        }

        burrow(obsidian.slot());
        info("Burrowed!");


        ticks = cooldown.get();
    }

    public void burrow(int slot) {
        double y = 0.0;
        double velocity = value;
        for (int i = 0; i < loop; i++) {
            y = y + velocity;
            // 0.3681288
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + y, mc.player.getZ(), mc.player.getYaw(), 90, onground.get(), mc.player.horizontalCollision));
            //msg(String.valueOf(y));
            velocity = (velocity - minus) * gravity;
        }

        InvUtils.swap(slot, true);
        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(mc.player.getBlockPos().down().toCenterPos(), Direction.UP, mc.player.getBlockPos().down(), false), 0));
        InvUtils.swapBack();

        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + y + offset.get(), mc.player.getZ(), ongroundtwo.get(), mc.player.horizontalCollision));
    }

    private boolean canBurrow() {
        if (!mc.player.getBlockStateAtPos().isReplaceable()) return false;
        if (!canPlace(Blocks.OBSIDIAN.getDefaultState(), mc.player.getBlockPos(), ShapeContext.absent())) return false;
        if (mc.world.getBlockState(mc.player.getBlockPos().down()).isReplaceable()) return false;
        return mc.player.isOnGround();
    }

    private boolean canPlace(BlockState state, BlockPos pos, ShapeContext context) {
        VoxelShape voxelShape = state.getCollisionShape(mc.world, pos, context);
        return voxelShape.isEmpty() || mc.world.doesNotIntersectEntities(mc.player, voxelShape.offset(pos.getX(), pos.getY(), pos.getZ()));
    }
}
