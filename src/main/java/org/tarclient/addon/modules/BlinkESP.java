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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BlinkESP extends TarModule {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> cycles = sgGeneral.add(new IntSetting.Builder()
        .name("cycles")
        .description("Cycles skipped before lagging")
        .defaultValue(1)
        .sliderRange(1, 10)
        .min(1)
        .build()
    );

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

    private final Map<UUID, Long> lastUpdated = new ConcurrentHashMap<>();
    private final Set<UUID> lagging = Sets.newConcurrentHashSet();

    private final AtomicLong currentCycle = new AtomicLong(0);

    public BlinkESP() {
        super(TarAddon.CATEGORY, "blink-esp", "Highlights blinking players");
    }

    @Override
    public void onActivate() {
        currentCycle.set(0);
        lastUpdated.clear();
        lagging.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!Utils.canUpdate()) return;
        if (!(event.packet instanceof PlayerListS2CPacket packet)) return;

        if (packet.getActions().size() == 1 && packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LATENCY)) {
            long current = currentCycle.incrementAndGet();

            for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
                lastUpdated.put(entry.profileId(), current);
            }

            for (UUID uuid : lastUpdated.keySet()) {
                if (current - lastUpdated.getOrDefault(uuid, current) >= cycles.get()) {
                    lagging.add(uuid);
                } else {
                    lagging.remove(uuid);
                }
            }
        }
    }

    @EventHandler
    private void onRemove(EntityRemovedEvent event) {
        UUID uuid = event.entity.getUuid();
        lagging.remove(uuid);
        lastUpdated.remove(uuid);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!lagging.contains(player.getUuid())) continue;

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
