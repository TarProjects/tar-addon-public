package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldView;
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

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant")
        .description("Catch at sound event instead of tick thread")
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

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay after placing")
        .defaultValue(1)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Double> timeout = sgRender.add(new DoubleSetting.Builder()
        .name("timeout")
        .description("How many seconds before removing from chorus positions? Fixes issues with low ping")
        .defaultValue(5)
        .sliderRange(1, 10)
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

    private final List<ChorusPosition> chorusPositions = new ArrayList<>();
    private final List<BlockPos> placePositions = new ArrayList<>();
    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    private long lastTeleport;
    private Vec3d lastTeleportPosition;

    private Vec3d preTeleportPos;

    private int cooldown;

    public ChorusTrap() {
        super(TarAddon.CATEGORY, "chorus-trap", "AutoTraps chorused peoples head hitbox");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        cooldown = 0;
        chorusPositions.clear();
        placePositions.clear();
        renderQueue.clear();

        lastTeleport = 0;
        lastTeleportPosition = null;
        preTeleportPos = mc.player.getEntityPos();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            preTeleportPos = mc.player.getEntityPos();
            lastTeleport = System.currentTimeMillis();
            lastTeleportPosition = packet.change().position();
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!event.sound.getId().equals(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT.id())) return;
        if (!mc.isOnThread()) return; // never happens, safety tho

        long now = System.currentTimeMillis();

        Vec3d vec = new Vec3d(event.sound.getX(), event.sound.getY(), event.sound.getZ());
        Box targetBox = getBox(vec);
        if (now - lastTeleport < 1000 && lastTeleportPosition != null) { // hardcoded 1 second!
            if (vec.squaredDistanceTo(lastTeleportPosition) < 1) {
                // < 1 block distance, probably own teleport!
                return;
            }
        }

        boolean intersects = !mc.world.getEntitiesByClass(LivingEntity.class, targetBox.expand(0.25), (entity) -> true).isEmpty();
        if (intersects) return;
        if (lastTickBB(mc.player).intersects(targetBox.expand(0.25))) return; // own tp

        chorusPositions.add(new ChorusPosition(vec, now));

        if (!instant.get()) return; // instant logic from here on out

        if (mc.player.getEyePos().squaredDistanceTo(vec) > targetRange.get() * targetRange.get()) return; // cheap distance

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obby.found()) return;
        if (onlyWhenSelfInHole.get() && !PlayerUtils.isInHole(true)) return;

        placePositions.clear();
        addPlacePositions(mc.world, BlockPos.ofFloored(vec), targetBox, mc.player.getEyePos());

        if (!placePositions.isEmpty())
            handlePlaceLogic(obby, true); // instant -> true
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        placePositions.clear();

        FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obby.found()) return;
        if (onlyWhenSelfInHole.get() && !PlayerUtils.isInHole(true)) return;

        Iterator<ChorusPosition> iterator = chorusPositions.iterator();

        long now = System.currentTimeMillis();
        while (iterator.hasNext()) {
            ChorusPosition position = iterator.next();
            Vec3d vec = position.pos;

            if (now - position.timeOfTeleport > timeout.get() * 1000) {
                // timeout
                iterator.remove();
                continue;
            }

            Box targetBox = getBox(vec);
            BlockPos feetPos = BlockPos.ofFloored(vec);

            boolean intersects = !mc.world.getEntitiesByClass(LivingEntity.class, targetBox.expand(0.25), (entity) -> true).isEmpty();
            if (intersects) {
                iterator.remove();
                continue;
            }

            // already filled
            if (!mc.world.getBlockState(feetPos.up()).isReplaceable()) {
                iterator.remove();
                continue;
            }

            if (mc.player.getEyePos().squaredDistanceTo(vec) > targetRange.get() * targetRange.get()) continue; // cheap distance check

            addPlacePositions(mc.world, feetPos, targetBox, mc.player.getEyePos());
        }

        if (!placePositions.isEmpty())
            handlePlaceLogic(obby, false);
    }

    private void handlePlaceLogic(FindItemResult obby, boolean ignoreBpt) {
        if (mc.player == null) return;
        int placed = 0;

        for (BlockPos blockPos : placePositions) {
            if (mc.player.getEyePos().squaredDistanceTo(blockPos.toCenterPos()) > range.get() * range.get()) continue; // cheap distance check
            if (obby.count() - placed <= 0) break;
            if (!ignoreBpt && placed >= blocksPerTick.get()) break;

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

    private void addPlacePositions(WorldView world, BlockPos feetPos, Box excludeBox, Vec3d selfPosition) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos offsetPos = feetPos.offset(dir);

            if (excludeBox.intersects(new Box(offsetPos))) {
                continue;
            }

            if (world.getBlockState(offsetPos).isReplaceable()) {
                if (!BlockUtils.canPlace(offsetPos)) continue;
                if (selfPosition.squaredDistanceTo(offsetPos.toCenterPos()) > range.get() * range.get()) continue;
            }

            BlockPos offsetDown = offsetPos.down();
            if (world.getBlockState(offsetDown).isReplaceable()) {
                if (!BlockUtils.canPlace(offsetDown)) continue;
                if (selfPosition.squaredDistanceTo(offsetDown.toCenterPos()) > range.get() * range.get()) continue;
                if (BlockUtils.getClosestPlaceSide(offsetDown) == null) continue;
            }

            BlockPos pos2 = offsetPos.up();
            if (world.getBlockState(pos2).isReplaceable()) {
                if (!BlockUtils.canPlace(pos2)) continue;
                if (selfPosition.squaredDistanceTo(pos2.toCenterPos()) > range.get() * range.get()) continue;
            }

            BlockPos pos3 = feetPos.up();
            if (world.getBlockState(pos3).isReplaceable()) {
                if (!BlockUtils.canPlace(pos3)) continue;
                if (selfPosition.squaredDistanceTo(pos3.toCenterPos()) > range.get() * range.get()) continue;
            }

            placePositions.add(offsetPos);
            placePositions.add(pos2);
            placePositions.add(pos3);
            return;
        }
    }

    private Box lastTickBB(PlayerEntity player) {
        Box currentBox = player.getBoundingBox();
        double width = currentBox.getLengthX();
        double height = currentBox.getLengthY();
        double depth = currentBox.getLengthZ();

        double centerX = preTeleportPos.getX();
        double centerY = preTeleportPos.getY();
        double centerZ = preTeleportPos.getZ();

        return Box.of(new Vec3d(centerX, centerY, centerZ), width, height, depth);
    }

    private Box getBox(Vec3d vec) {
        double min_x = vec.x - 0.6 / 2;
        double max_x = vec.x + 0.6 / 2;

        double max_y = vec.y + 1.6;

        double min_z = vec.z - 0.6 / 2;
        double max_z = vec.z + 0.6 / 2;

        return new Box(min_x, vec.y, min_z, max_x, max_y, max_z);
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

    public record ChorusPosition(Vec3d pos, long timeOfTeleport) {}
}
