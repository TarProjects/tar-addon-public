package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.HoleUtils;
import org.tarclient.addon.utils.PredictUtils;
import org.tarclient.addon.utils.SimulationState;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.*;

public class Flattener extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgConditions = settings.createGroup("Conditions");
    private final SettingGroup sgPredict = settings.createGroup("Predict");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(6)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick (max)")
        .defaultValue(2)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Boolean> assumeStep = sgGeneral.add(new BoolSetting.Builder()
        .name("assume-step")
        .description("Assumes step if player is NOT in hole")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> stepHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("step-height")
        .description("Max step height to extrapolate")
        .defaultValue(2)
        .sliderRange(0.5, 3)
        .build()
    );

    /* --- Conditions */
    private final Setting<Double> minHP = sgConditions.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health of this module")
        .defaultValue(10)
        .sliderRange(5, 30)
        .build()
    );

    private final Setting<Integer> minimumObsidian = sgConditions.add(new IntSetting.Builder()
        .name("minimum-obsidian")
        .description("Minimum amount of obsidian in inventory to activate")
        .defaultValue(32)
        .sliderRange(0, 128)
        .build()
    );

    private final Setting<Boolean> onlyInHole = sgConditions.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only activates in hole")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> maxPositionChange = sgPredict.add(new DoubleSetting.Builder()
        .name("max-position-change")
        .description("Judges position changes to not waste blocks on blink")
        .defaultValue(0.5)
        .sliderRange(0, 5)
        .build()
    );


    /* --- Predict --- */
    private final Setting<Integer> predict = sgPredict.add(new IntSetting.Builder()
        .name("predict")
        .description("How many ticks to predict")
        .defaultValue(6)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> direction = sgPredict.add(new BoolSetting.Builder()
        .name("direction")
        .description("Only enables when player is running towards you")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> angle = sgPredict.add(new DoubleSetting.Builder()
        .name("angle")
        .description("Angle of the direction check")
        .defaultValue(45)
        .sliderRange(15, 180)
        .visible(direction::get)
        .build()
    );

    private final Setting<Double> expandBox = sgPredict.add(new DoubleSetting.Builder()
        .name("expand-box")
        .description("Expands the prediction")
        .defaultValue(-0.12)
        .sliderRange(-0.3, 2)
        .range(-0.3, 2)
        .build()
    );

    /* --- Render --- */
    private final Setting<Boolean> debug = sgRender.add(new BoolSetting.Builder()
        .name("debug")
        .description("Renders the entire path constantly")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How many seconds should rendering take?")
        .defaultValue(0.2)
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

    private PlayerEntity target;

    private final List<BlockPos> placePositions = new ArrayList<>();
    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    public Flattener() {
        super(TarAddon.CATEGORY, "flattener", "Places blocks below opponents feet");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

        target = null;
        placePositions.clear();
        renderQueue.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (debug.get()) {
            // debug handling
            for (BlockPos placePosition : placePositions) {
                event.renderer.box(placePosition, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }

            return;
        }

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
        if (mc.player == null) return;

        // clearing here to prevent useless rendering
        placePositions.clear();

        if (mc.player.getHealth() < minHP.get()) return;
        if (!PlayerUtils.isInHole(true) && onlyInHole.get()) return;

        if (InvUtils.find(Items.OBSIDIAN).count() < minimumObsidian.get()) return;

        FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obby.found()) return;

        target = getTarget(targetRange.get(), priority.get());
        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = null;
            return;
        }

        Vec3d delta = new Vec3d(target.getX() - target.lastX, target.getY() - target.lastY, target.getZ() - target.lastZ);
        if (delta.horizontalLengthSquared() > maxPositionChange.get() * maxPositionChange.get()) {
            // ignore blink
            return;
        }

        fetchPlacePositions();

        if (placePositions.isEmpty()) return;

        int placed = 0;

        for (BlockPos blockPos : placePositions) {
            if (mc.player.squaredDistanceTo(blockPos.toCenterPos()) > range.get() * range.get()) continue;
            if (!BlockUtils.canPlace(blockPos)) continue;

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
            }
        }

        InvUtils.swapBack();
    }

    private PlayerEntity getTarget(double range, SortPriority priority) {
        if (!Utils.canUpdate() || mc.player == null) return null;
        return (PlayerEntity) TargetUtils.get(entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isDead() || player.getHealth() <= 0) return false;
            if (!PlayerUtils.isWithin(entity, range)) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer) return !fakePlayer.noHit;
            if (direction.get() && !isComingTowards(mc.player, player, angle.get())) return false;
            return EntityUtils.getGameMode(player) == GameMode.SURVIVAL;
        }, priority);
    }

    public static boolean isComingTowards(PlayerEntity player, PlayerEntity enemy, double thresholdDegrees) {
        double moveX = enemy.getX() - enemy.lastX;
        double moveZ = enemy.getZ() - enemy.lastZ;

        double toYouX = player.getX() - enemy.getX();
        double toYouZ = player.getZ() - enemy.getZ();

        double lenMove = Math.hypot(moveX, moveZ);
        double lenToYou = Math.hypot(toYouX, toYouZ);

        if (lenMove < 1e-6 || lenToYou < 1e-6) {
            return false;
        }

        double dot = moveX * toYouX + moveZ * toYouZ;

        double cosAngle = dot / (lenMove * lenToYou);
        cosAngle = Math.clamp(cosAngle, -1.0, 1.0);

        double angleDeg = Math.toDegrees(Math.acos(cosAngle));

        return angleDeg <= thresholdDegrees;
    }

    private final Map<BlockPos, BlockState> blocksToRestore = new HashMap<>();

    private void fetchPlacePositions() {
        if (mc.world == null) return;
        blocksToRestore.clear();
        Vec3d velocity = target.getEntityPos().subtract(target.lastX, target.lastY, target.lastZ);
        SimulationState state = new SimulationState(target.getEntityPos(), velocity);

        boolean shouldStep = assumeStep.get() && !HoleUtils.isInHole(target.getBlockPos(), true);

        try {
            double expand = Math.max(expandBox.get(), -0.2999);
            Box baseBox = target.getBoundingBox().expand(expand, 0, expand);

            for (int i = 0; i < predict.get(); i++) {
                PredictUtils.advanceOneTick(target, state, shouldStep ? stepHeight.get() : 0);
                Box predictBox = baseBox.offset(state.pos.subtract(target.getEntityPos()));

                int minX = (int) Math.floor(predictBox.minX);
                int maxX = (int) Math.floor(predictBox.maxX);
                int minZ = (int) Math.floor(predictBox.minZ);
                int maxZ = (int) Math.floor(predictBox.maxZ);

                int y = (int) Math.floor(state.pos.getY()) - 1;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState blockState = mc.world.getBlockState(pos);
                        if (!placePositions.contains(pos)) {
                            placePositions.add(pos);

                            blocksToRestore.put(pos, blockState);
                            mc.world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
                        }
                    }
                }
            }

            placePositions.sort(Comparator.comparingDouble(pos -> target.squaredDistanceTo(pos.toCenterPos())));
        } finally {
            for (BlockPos blockPos : blocksToRestore.keySet()) {
                BlockState blockState = blocksToRestore.get(blockPos);
                mc.world.setBlockState(blockPos, blockState);
            }
        }
    }
}
