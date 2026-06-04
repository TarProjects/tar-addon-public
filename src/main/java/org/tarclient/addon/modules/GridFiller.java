package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.DuelChangeUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.*;

import static meteordevelopment.meteorclient.utils.world.BlockUtils.getClosestPlaceSide;

public class GridFiller extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Integer> scanRange = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Scan range for placing")
        .defaultValue(7)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
        .name("y-level")
        .description("Y level to place at")
        .defaultValue(10)
        .sliderRange(0, 128)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick (max)")
        .defaultValue(1)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("The global cooldown")
        .defaultValue(2)
        .sliderRange(0, 10)
        .build()
    );

    /* --- Render --- */
    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How many seconds should rendering take?")
        .defaultValue(1.5)
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
    private int globalCooldown;

    public GridFiller() {
        super(TarAddon.CATEGORY, "grid-filler", "Fills using a grid");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

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
        if (!Utils.canUpdate()) return;
        if (mc.world == null || mc.player == null) return;

        String arena = mc.world.getRegistryKey().getValue().getPath();
        if (arena == null || arena.isEmpty()) {
            this.toggle();
            return;
        }

        if (!InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
            error("No obsidian!");
            this.toggle();
            return;
        }

        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }

        HashSet<BlockPos> placeQueue = new HashSet<>();

        for (int dx = -scanRange.get(); dx <= scanRange.get(); dx++) {
            for (int dz = -scanRange.get(); dz <= scanRange.get(); dz++) {
                if (placeQueue.size() >= blocksPerTick.get()) break;

                BlockPos pos = new BlockPos(mc.player.getBlockX() + dx, y.get(), mc.player.getBlockZ() + dz);

                if (pos.getY() != y.get()) continue;

                Direction placeSide = getClosestPlaceSide(pos);
                if (placeSide == null) continue;

                // allat for distance :sob:
                BlockPos neighbour = pos.offset(placeSide);
                Vec3d vec = neighbour.toCenterPos().add(placeSide.getOffsetX() * 0.5, placeSide.getOffsetY() * 0.5, placeSide.getOffsetZ() * 0.5);

                double distance = mc.player.getEyePos().squaredDistanceTo(vec);
                if (distance > range.get() * range.get()) continue;

                if (!isValidPlacement(arena, placeQueue, pos)) continue;

                placeQueue.add(pos);
            }

            if (placeQueue.size() >= blocksPerTick.get()) break;
        }

        for (BlockPos pos : placeQueue) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
                TarBlockUtils.place(pos, false, true, Blocks.OBSIDIAN, (blockHitResult) -> {
                    FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
                    if (!obby.found()) return;
                    InvUtils.swap(obby.slot(), true);
                    BlockUtils.interact(blockHitResult, obby.getHand(), true);
                    InvUtils.swapBack();

                    renderQueue.put(pos, fadeTime.get());

                    // since we placed, apply cooldown to next tick
                    globalCooldown = cooldown.get();
                });
            });
        }
    }

    private boolean isValidPlacement(String arena, Set<BlockPos> placedThisBatch, BlockPos pos) {
        if (!BlockUtils.canPlace(pos)) return false;

        // Adjacent to persistent tracked changes?
        Set<BlockPos> tracked = DuelChangeUtils.getArenaBlocks(arena);
        if (placedThisBatch.contains(pos) || tracked.contains(pos)) return false;

        return !TarBlockUtils.isAdjacentToAny(pos, placedThisBatch) && !TarBlockUtils.isAdjacentToAny(pos, tracked);
    }
}
