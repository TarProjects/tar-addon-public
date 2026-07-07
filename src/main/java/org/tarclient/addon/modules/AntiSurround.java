package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.tarclient.addon.utils.MiningUtils.*;

public class AntiSurround extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Primary blocks to use")
        .defaultValue(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)
        .build()
    );

    private final Setting<Double> minHP = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health of this module")
        .defaultValue(10)
        .sliderRange(5, 30)
        .build()
    );

    /* --- Delay --- */
    private final Setting<Integer> cooldown = sgDelay.add(new IntSetting.Builder()
        .name("cooldown")
        .description("The global cooldown")
        .defaultValue(10)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> placeCondition = sgDelay.add(new DoubleSetting.Builder()
        .name("place-condition")
        .description("How much damage to block before place")
        .defaultValue(0.8)
        .sliderRange(0, 1)
        .build()
    );

    /* --- Render --- */
    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How many seconds should rendering take?")
        .defaultValue(0.5)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );

    private final Map<BlockPos, Double> renderQueue = new HashMap<>();
    private int globalCooldown = 0;

    public AntiSurround() {
        super(TarAddon.CATEGORY, "anti-surround", "Replaces surround blocks with blocks that are easier to break");
    }

    @Override
    public void onActivate() {
        renderQueue.clear();
        globalCooldown = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Iterator<Map.Entry<BlockPos, Double>> it = renderQueue.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, Double> entry = it.next();
            double remaining = entry.getValue();

            if (remaining <= 0) {
                it.remove();
                continue;
            }

            double alphaMultip = Math.clamp(remaining / fadeTime.get(), 0, 1);

            // uhh multiply alpha ig?
            Color side = sideColor.get().copy().a((int) (sideColor.get().a * alphaMultip));
            Color line = lineColor.get().copy().a((int) (lineColor.get().a * alphaMultip));

            event.renderer.box(entry.getKey(), side, line, shapeMode.get(), 0);

            entry.setValue(remaining - (float) event.frameTime);
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null) return;

        FindItemResult placeable = findPlaceable();
        if (!placeable.found()) { return; }

        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }

        if (mc.player.getHealth() < minHP.get()) return;

        if (getBreakingProgress() < placeCondition.get()) return;

        BlockPos breaking = getBreakingBlockPos();
        if (breaking == null || !isValidSurroundPos(breaking)) {
            return;
        }

        // pos = obby/surround, pos.up = air
        if (!mc.world.getBlockState(breaking).isReplaceable() && mc.world.getBlockState(breaking.up()).isReplaceable()) {
            handleAnvilPlace(placeable, breaking.up());
        }
    }

    public void handleAnvilPlace(FindItemResult placeable, BlockPos toPlace) {
        if (mc.world == null || mc.player == null) return;

        if (!placeable.found()) return;


        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (Box.from(new BlockBox(toPlace)).intersects(entity.getBoundingBox())) {
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), () -> attack(entity));
            }
        }

        TarBlockUtils.InteractRunnable callback = (bhr -> {
            Rotations.rotate(Rotations.getYaw(bhr.getPos()), Rotations.getPitch(bhr.getPos()), () -> {
                swap(placeable.slot());
                BlockUtils.interact(bhr, Hand.MAIN_HAND, true);
                swap(placeable.slot());
                globalCooldown = cooldown.get();
            });
        });

        TarBlockUtils.place(toPlace, false, true, Blocks.ANVIL, callback);
    }

    public FindItemResult findPlaceable() {
        return InvUtils.find(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem) {
                Block itemBlock = ((BlockItem) itemStack.getItem()).getBlock();
                return blocks.get().contains(itemBlock);
            }
            return false;
        });
    }

    private boolean isValidSurroundPos(BlockPos pos) {
        if (mc.world == null) return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (TargetUtils.isBadTarget(player, targetRange.get()) || !Friends.get().shouldAttack(player)) continue;
            Direction dir = getSurroundDirection(pos, player.getBlockPos());
            if (dir != null && isValidSurroundPos(pos, dir)) return true;
        }
        return false;
    }

    private Direction getSurroundDirection(BlockPos surroundPos, BlockPos feet) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (feet.offset(dir).equals(surroundPos)) return dir;
        }
        return null;
    }

    private boolean isValidSurroundPos(BlockPos pos, Direction outwardDir) {
        if (mc.world == null || mc.player == null) return false;
        BlockState state = mc.world.getBlockState(pos);

        if (state.getBlock() == Blocks.BEDROCK) return false;
        if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) return false;
        // we need to place anvil, make sure no solid above
        if (!mc.world.getBlockState(pos.up()).isReplaceable()) return false;

        BlockPos crystalBase = pos.offset(outwardDir).down();
        return isValidCrystalPos(crystalBase);
    }

    private boolean isValidCrystalPos(BlockPos base) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(base);
        if (state.getBlock() != Blocks.OBSIDIAN && state.getBlock() != Blocks.BEDROCK) return false;
        return mc.world.getBlockState(base.up()).isAir();
    }

    private void attack(Entity entity) {
        if (mc.interactionManager == null || mc.player == null || entity.isRemoved() || !entity.isAlive()) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        entity.setRemoved(Entity.RemovalReason.KILLED);
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }
}
