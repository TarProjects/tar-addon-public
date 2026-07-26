package org.tarclient.addon.modules;


import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AnvilSuicide extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Height on where to place anvils at")
        .defaultValue(6)
        .sliderRange(2, 8)
        .build()
    );

    private final Setting<Boolean> onlyInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only suicides if you are in a hole")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-offhand")
        .description("Disables offhand")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dropArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-armor")
        .description("Drops armor")
        .defaultValue(false)
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

    private BlockPos startPos;
    private int dropCounter;

    public AnvilSuicide() {
        super(TarAddon.CATEGORY, "anvil-suicide", "Makes the players death message \"was squashed by a falling anvil\", since this is not broadcasted on some servers.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            this.toggle();
            return;
        }

        startPos = BurrowUtils.getCeiledBlockPos();
        dropCounter = 0;

        if (disableOffhand.get()) {
            MioUtils.toggleOffhand(false);
        }

        MiningUtils.attackWithCompatibility(startPos, Direction.UP);
    }

    @Override
    public void onDeactivate() {
        if (disableOffhand.get()) {
            MioUtils.toggleOffhand(true);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (onlyInHole.get() && !HoleUtils.isInHole(startPos, true)) {
            error("Not in hole, disabling!");
            this.toggle();
            return;
        }

        if (!BurrowUtils.getCeiledBlockPos().equals(startPos)) {
            error("Moved, disabling!");
            this.toggle();
            return;
        }

        FindItemResult anvil = findAnvil();
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!anvil.found() || !obsidian.found()) {
            error("No anvil/obsidian in hotbar!");
            this.toggle();
            return;
        }

        if (!hasAirAbove(startPos, height.get())) {
            error("No air above!");
            this.toggle();
            return;
        }

        Direction best = findBestDirection(startPos, height.get());

        if (best == null) {
            error("How did we get here? No valid horizontals...");
            this.toggle();
            return;
        }

        BlockPos pillarBase = startPos.offset(best);

        BlockPos target = getLowestMissingBlock(pillarBase, height.get());

        if (target == null) {
            // no missing blocks
            // dropping anvils: stage == placing

            if (dropArmor.get() && dropCounter < 8) {
                // drop every 2 ticks
                if (dropCounter % 2 == 0) {
                    int slot = SlotUtils.ARMOR_START + dropCounter / 2;
                    if (!mc.player.getInventory().getStack(slot).isEmpty()) {
                        InvUtils.drop().slotArmor(dropCounter / 2);
                    }
                }
                dropCounter++;
            }

            TarBlockUtils.InteractRunnable callback = (bhr -> {
                double yaw = Rotations.getYaw(bhr.getPos());
                double pitch = Rotations.getPitch(bhr.getPos());

                sendRotatePacket(yaw, pitch, RotationPacket.Full);

                InvUtils.swap(anvil.slot(), true);
                BlockUtils.interact(bhr, anvil.getHand(), true);
                InvUtils.swapBack();

                renderQueue.put(bhr.getBlockPos().offset(bhr.getSide()), fadeTime.get());
            });

            TarBlockUtils.place(startPos.up(height.get()), false, true, Blocks.ANVIL, callback);
        } else {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity)) continue;
                if (Box.from(new BlockBox(target)).intersects(entity.getBoundingBox())) {
                    double yaw = Rotations.getYaw(entity);
                    double pitch = Rotations.getPitch(entity);
                    sendRotatePacket(yaw, pitch, RotationPacket.Full);
                    attack(entity);
                }
            }

            TarBlockUtils.InteractRunnable callback = (bhr -> {
                double yaw = Rotations.getYaw(bhr.getPos());
                double pitch = Rotations.getPitch(bhr.getPos());

                sendRotatePacket(yaw, pitch, RotationPacket.Full);

                InvUtils.swap(obsidian.slot(), true);
                BlockUtils.interact(bhr, obsidian.getHand(), true);
                InvUtils.swapBack();

                renderQueue.put(bhr.getBlockPos().offset(bhr.getSide()), fadeTime.get());
            });

            TarBlockUtils.place(target, false, true, Blocks.OBSIDIAN, callback);
        }
    }

    private BlockPos getLowestMissingBlock(BlockPos pillarBase, int n) {
        if (mc.world == null) return null;
        for (int i = 1; i <= n; i++) {
            BlockPos check = pillarBase.up(i);
            if (mc.world.getBlockState(check).isReplaceable()) {
                return check;
            }
        }
        return null;
    }

    private Direction findBestDirection(BlockPos pos, int n) {
        if (mc.world == null || mc.player == null) return null;

        Direction best = null;
        int minAir = Integer.MAX_VALUE;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos base = pos.offset(dir);
            int airCount = 0;
            for (int i = 1; i <= n; i++) {
                if (mc.world.getBlockState(base.up(i)).isReplaceable()) airCount++;
            }
            if (airCount < minAir) {
                minAir = airCount;
                best = dir;
            }
        }
        return best;
    }

    private boolean hasAirAbove(BlockPos pos, int n) {
        if (mc.world == null || mc.player == null) return false;

        for (int i = 1; i <= n; i++) {
            BlockState state = mc.world.getBlockState(pos.up(i));
            // not anvil and not replaceable
            if (!(state.getBlock() instanceof AnvilBlock) && !state.isReplaceable()) {
                return false;
            }
        }
        return true;
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

    private FindItemResult findAnvil() {
        return InvUtils.findInHotbar(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock() instanceof AnvilBlock;
            }
            return false;
        });
    }

    private void attack(Entity entity) {
        if (mc.interactionManager == null || mc.player == null || entity.isRemoved() || !entity.isAlive()) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        entity.setRemoved(Entity.RemovalReason.KILLED);
    }
}
