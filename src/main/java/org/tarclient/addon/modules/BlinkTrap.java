package org.tarclient.addon.modules;

import com.google.common.collect.Sets;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.HoleUtils;
import org.tarclient.addon.utils.LagDetection;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @concept levent
 * @author nullable
 */
public class BlinkTrap extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
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

    private final Setting<Boolean> onlyWhenTargetInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-target-in-hole")
        .description("Only places if target is in hole")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhenSelfInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-self-in-hole")
        .description("Only places if you is in hole")
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

    /* --- Detection --- */
    private final Setting<LagDetection> lagDetectionMethod = sgDetection.add(new EnumSetting.Builder<LagDetection>()
        .name("lag-detection")
        .description("Lag detection method. Most reliable one is Ping")
        .defaultValue(LagDetection.Ping)
        .build()
    );

    private final Setting<Integer> pingCycles = sgDetection.add(new IntSetting.Builder()
        .name("ping-cycles")
        .description("Tab ping cycles skipped before lagging")
        .defaultValue(1)
        .sliderRange(1, 10)
        .min(1)
        .visible(() -> lagDetectionMethod.get().ping())
        .build()
    );

    private final Setting<Integer> sprintCycles = sgDetection.add(new IntSetting.Builder()
        .name("sprint-cycles")
        .description("Sprint move/rotation packet cycles skipped before lagging")
        .defaultValue(3)
        .sliderRange(1, 20)
        .min(1)
        .visible(() -> lagDetectionMethod.get().sprint())
        .build()
    );
    private final Setting<Boolean> resetOnCorner = sgDetection.add(new BoolSetting.Builder()
        .name("reset-on-corner")
        .description("Less detection and less false detections")
        .defaultValue(true)
        .visible(() -> lagDetectionMethod.get().sprint())
        .build()
    );

    private final Setting<Boolean> resetCounter = sgDetection.add(new BoolSetting.Builder()
        .name("reset-counter")
        .description("If target isn't sprinting for exactly N ticks, reset counter")
        .defaultValue(false)
        .visible(() -> lagDetectionMethod.get().sprint())
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
    private final List<BlockPos> placePositions = new ArrayList<>();
    private final Map<BlockPos, Double> renderQueue = new HashMap<>();
    // uuid -> sprinting & move state
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    /* all the lag detection stuff */
    private final Map<UUID, Long> lastUpdated = new ConcurrentHashMap<>();
    private final Set<UUID> lagging = Sets.newConcurrentHashSet();
    private final AtomicLong currentCycle = new AtomicLong(0);
    private int cooldown;

    public BlinkTrap() {
        super(TarAddon.CATEGORY, "blink-trap", "AutoTraps blinking players head (fire)");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
        placePositions.clear();
        renderQueue.clear();

        states.clear();
        currentCycle.set(0);
        lastUpdated.clear();
        lagging.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!Utils.canUpdate()) return;

        if (lagDetectionMethod.get().ping() && event.packet instanceof PlayerListS2CPacket packet) {
            handlePlayerList(packet);
        }

        if (lagDetectionMethod.get().sprint()) {
            handleSprintPackets(event.packet);
        }
    }

    private void handlePlayerList(PlayerListS2CPacket packet) {
        if (packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LATENCY)) {
            long current = currentCycle.get();
            // size() == 1 means that the only action is UPDATE_LATENCY, which corresponds
            // to a normal cycle. only increment cycles on those
            if (packet.getActions().size() == 1) current = currentCycle.incrementAndGet();

            for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
                lastUpdated.put(entry.profileId(), current);
            }

            for (UUID uuid : lastUpdated.keySet()) {
                if (current - lastUpdated.getOrDefault(uuid, current) >= pingCycles.get()) {
                    lagging.add(uuid);
                } else {
                    lagging.remove(uuid);
                }
            }
        }
    }

    private void handleSprintPackets(Packet<?> packet) {
        if (packet instanceof EntityPositionSyncS2CPacket(int id, EntityPosition values, boolean onGround)) {
            mc.execute(() -> {
                if (mc.world == null) return;

                Entity entity = mc.world.getEntityById(id);
                if (!(entity instanceof PlayerEntity player)) return;

                PlayerState state = getState(entity.getUuid());

                double x = values.position().x;
                double y = values.position().y;
                double z = values.position().z;

                float yaw = values.yaw();
                float pitch = values.pitch();

                boolean moved = x != state.x || y != state.y || z != state.z;
                boolean rotated = yaw != state.yaw || pitch != state.pitch;

                state.x = x;
                state.y = y;
                state.z = z;
                state.yaw = yaw;
                state.pitch = pitch;

                if (moved || rotated) {
                    state.stationarySprintCycles = 0;
                    state.blinking = false;
                } else {
                    if (state.sprinting) {
                        if (!resetOnCorner.get() || !isAgainstWall(player)) {
                            state.stationarySprintCycles++;
                        }
                    } else if (resetCounter.get()){
                        state.stationarySprintCycles = 0;
                    }

                    if (state.stationarySprintCycles >= sprintCycles.get()) {
                        state.blinking = true;
                    }
                }
            });
        }
        if (packet instanceof EntityTrackerUpdateS2CPacket(
            int id, java.util.List<DataTracker.SerializedEntry<?>> trackedValues
        )) {
            mc.execute(() -> {
                if (mc.world == null) return;

                Entity entity = mc.world.getEntityById(id);
                if (!(entity instanceof PlayerEntity)) return;

                PlayerState state = getState(entity.getUuid());

                for (DataTracker.SerializedEntry<?> entry : trackedValues) {
                    if (entry.id() == 0 && entry.value() instanceof Byte flags) {
                        boolean wasSneaking = state.sneaking;
                        boolean wasSprinting = state.sprinting;

                        boolean newSneaking = (flags & 0x02) != 0;
                        boolean newSprinting = (flags & 0x08) != 0;

                        // fix: player sprinting against a wall -> start sneak -> stop sneak will
                        // make the player send a sprint instead. ignore this by using this
                        // weird logic...

                        // works for now I guess
                        if (wasSneaking && !newSneaking && !wasSprinting && newSprinting) {
                            // ignore sprint state change
                        } else {
                            state.sprinting = newSprinting;
                        }

                        state.sneaking = newSneaking;
                        break;
                    }
                }
            });
        }
    }

    @EventHandler
    private void onRemove(EntityRemovedEvent event) {
        UUID uuid = event.entity.getUuid();
        lagging.remove(uuid);
        lastUpdated.remove(uuid);
        states.remove(uuid);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        placePositions.clear();

        FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obby.found()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (onlyWhenSelfInHole.get() && !PlayerUtils.isInHole(true)) return;

        for (PlayerEntity target : mc.world.getPlayers()) {
            if (target == mc.player) continue;
            if (!isBlinking(target)) continue;

            // each blinking player here
            if (!Friends.get().shouldAttack(target)) continue;
            if (mc.player.squaredDistanceTo(target) > targetRange.get() * targetRange.get()) continue;
            if (onlyWhenTargetInHole.get() && !HoleUtils.isInHole(target.getBlockPos(), true)) continue;
            findPlacePositions(target);
        }

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

                // we placed, cooldown
            });

            if (didPlace) {
                placed++;
                cooldown = delay.get();
            }
        }

        InvUtils.swapBack();
    }


    public void findPlacePositions(PlayerEntity entity) {
        if (mc.player == null || mc.world == null) return;

        BlockPos target = entity.getBlockPos().up(2);
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

    private boolean isAgainstWall(PlayerEntity player) {
        if (mc.world == null) return false;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        final double TOLERANCE = 0.005;
        double fracX = x - Math.floor(x);
        double fracZ = z - Math.floor(z);

        int xOffset = 0;
        int zOffset = 0;
        boolean huggingX = false;
        boolean huggingZ = false;

        if (Math.abs(fracX - 0.3) < TOLERANCE) {
            xOffset = -1;
            huggingX = true;
        } else if (Math.abs(fracX - 0.7) < TOLERANCE) {
            xOffset = 1;
            huggingX = true;
        }

        if (Math.abs(fracZ - 0.3) < TOLERANCE) {
            zOffset = -1;
            huggingZ = true;
        } else if (Math.abs(fracZ - 0.7) < TOLERANCE) {
            zOffset = 1;
            huggingZ = true;
        }

        if (!huggingX || !huggingZ) return false;

        int baseX = (int) Math.floor(x);
        int baseZ = (int) Math.floor(z);
        int feetY = (int) Math.floor(y);
        int headY = (int) Math.floor(y + 1.0);

        BlockPos xWallFeet = new BlockPos(baseX + xOffset, feetY, baseZ);
        BlockPos xWallHead = new BlockPos(baseX + xOffset, headY, baseZ);
        boolean xBlocked = mc.world.getBlockState(xWallFeet).isSolidBlock(mc.world, xWallFeet) ||
            mc.world.getBlockState(xWallHead).isSolidBlock(mc.world, xWallHead);

        BlockPos zWallFeet = new BlockPos(baseX, feetY, baseZ + zOffset);
        BlockPos zWallHead = new BlockPos(baseX, headY, baseZ + zOffset);
        boolean zBlocked = mc.world.getBlockState(zWallFeet).isSolidBlock(mc.world, zWallFeet) ||
            mc.world.getBlockState(zWallHead).isSolidBlock(mc.world, zWallHead);

        return xBlocked && zBlocked;
    }

    private boolean isBlinking(PlayerEntity player) {
        UUID uuid = player.getUuid();

        PlayerState state = getState(uuid);

        if (lagDetectionMethod.get().sprint() && state.blinking) {
            return true;
        }

        return lagDetectionMethod.get().ping() && lagging.contains(uuid);
    }

    private PlayerState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, u -> new PlayerState());
    }

    private static class PlayerState {
        double x, y, z;
        float yaw, pitch;
        boolean sprinting;
        boolean sneaking;

        int stationarySprintCycles;
        boolean blinking;
    }
}
