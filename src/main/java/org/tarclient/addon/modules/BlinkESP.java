package org.tarclient.addon.modules;

import com.google.common.collect.Sets;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.LagDetection;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BlinkESP extends TarModule {
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");

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
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
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

    // uuid -> sprinting & move state
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    // we need to do this in such a way where we don't accidentally skip anything...
    // this is why everything is done in network & rendering and NOT on tick
    private final Map<UUID, Long> lastUpdated = new ConcurrentHashMap<>();
    private final Set<UUID> lagging = Sets.newConcurrentHashSet();

    private final AtomicLong currentCycle = new AtomicLong(0);

    public BlinkESP() {
        super(TarAddon.CATEGORY, "blink-esp", "Highlights blinking players");
    }

    @Override
    public void onActivate() {
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

    // TODO: fragile, use events instead of this
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
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            if (isBlinking(player)) {
                Vec3d pos = player.getLerpedPos(event.tickDelta);
                double x = pos.x, y = pos.y, z = pos.z;
                double width = 0.6, height = 1.6;
                double minX = x - width / 2, maxX = x + width / 2;
                double minZ = z - width / 2, maxZ = z + width / 2;
                double maxY = y + height;

                event.renderer.box(minX, y, minZ, maxX, maxY, maxZ, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
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
