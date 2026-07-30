package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.*;

public class ChorusTrap extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    /* --- General --- */
    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(6)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Boolean> onlyWhenSelfInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-self-in-hole")
        .description("Only places if you are in hole")
        .defaultValue(true)
        .build()
    );


    private final Setting<Boolean> avoidFeet = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-feet")
        .description("Avoids placing inside the feet blockposition")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick (max)")
        .defaultValue(2)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> maxDepth = sgGeneral.add(new IntSetting.Builder()
        .name("max-depth")
        .description("Depth of support search tree")
        .defaultValue(5)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay after placing")
        .defaultValue(1)
        .sliderRange(0, 10)
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

    private final List<Vec3d> nonFilledChorusPositions = new ArrayList<>();
    private final List<BlockPos> placePositions = new ArrayList<>();
    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    private int cooldown;

    public ChorusTrap() {
        super(TarAddon.CATEGORY, "chorus-trap", "AutoTraps chorused peoples head hitbox");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
        nonFilledChorusPositions.clear();
        placePositions.clear();
        renderQueue.clear();
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (mc.player == null) return;

        if (event.sound.getId().equals(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT.id())) {
            Vec3d vec = new Vec3d(event.sound.getX(), event.sound.getY(), event.sound.getZ());
            nonFilledChorusPositions.add(vec);
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        placePositions.clear();

        // remove here, otherwise issues with cooldown race condition
        nonFilledChorusPositions.removeIf((vec3d -> {
            BlockPos blockPos = BlockPos.ofFloored(vec3d);
            BlockState head = mc.world.getBlockState(blockPos.up());
            if (!head.isReplaceable())
                return true;

            double min_x = vec3d.x - 0.6 / 2;
            double max_x = vec3d.x + 0.6 / 2;

            double max_y = vec3d.y + 1.6;

            double min_z = vec3d.z - 0.6 / 2;
            double max_z = vec3d.z + 0.6 / 2;
            return !mc.world.getEntitiesByClass(LivingEntity.class, new Box(min_x, vec3d.y, min_z, max_x, max_y, max_z), (entity) -> true).isEmpty();
        }));

        FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obby.found()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (onlyWhenSelfInHole.get() && !PlayerUtils.isInHole(true)) return;

        findPlacePositions();

        if (placePositions.isEmpty()) return;

        int placed = 0;

        for (BlockPos blockPos : placePositions) {
            if (placed >= blocksPerTick.get() || obby.count() - placed <= 0) break;

            boolean didPlace = TarBlockUtils.place(blockPos, false, true, Blocks.OBSIDIAN, (blockHitResult) -> {
                double yaw = Rotations.getYaw(blockHitResult.getPos());
                double pitch = Rotations.getPitch(blockHitResult.getPos());

                sendRotatePacket(yaw, pitch, RotationPacket.Full);

                // only swaps once so we don't have to spam swaps
                InvUtils.swap(obby.slot(), true);
                BlockUtils.interact(blockHitResult, obby.getHand(), true);

                // rendering
                renderQueue.put(blockPos, fadeTime.get());
            });

            if (didPlace) {
                placed++;
                cooldown = delay.get();
            }
        }

        InvUtils.swapBack();
    }

    public void findPlacePositions() {
        for (Vec3d vec3d : nonFilledChorusPositions) {
            BlockPos head = BlockPos.ofFloored(vec3d.add(0,1,0));
            findPlacePositions(head);
        }
    }

    public void findPlacePositions(BlockPos target) {
        if (mc.player == null || mc.world == null) return;

        double maxRangeSq = range.get() * range.get();

        // something already placed/cant place at all, don't support
        if (!BlockUtils.canPlace(target)) return;

        if (isValidPlacePosition(target)) {
            placePositions.add(target);
            return;
        }

        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(target);
        visited.add(target);
        if (avoidFeet.get()) visited.add(target.down());
        parent.put(target, null);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();

            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > maxRangeSq) {
                continue;
            }

            if (isValidPlacePosition(pos)) {
                List<BlockPos> path = new ArrayList<>();
                BlockPos current = pos;
                while (current != null) {
                    path.add(current);
                    current = parent.get(current);
                }
                placePositions.addAll(path);
                return;
            }

            if (Math.abs(pos.getX() - target.getX()) +
                Math.abs(pos.getY() - target.getY()) +
                Math.abs(pos.getZ() - target.getZ()) >= maxDepth.get()) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.offset(dir);
                if (visited.contains(neighbor)) continue;

                if (!BlockUtils.canPlace(neighbor)) continue;

                visited.add(neighbor);
                parent.put(neighbor, pos);
                queue.add(neighbor);
            }
        }
    }

    private boolean isValidPlacePosition(BlockPos pos) {
        if (mc.player == null) return false;

        Direction placeSide = BlockUtils.getClosestPlaceSide(pos);
        if (placeSide == null) return false;

        if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return false;
        return BlockUtils.canPlace(pos);
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
}
