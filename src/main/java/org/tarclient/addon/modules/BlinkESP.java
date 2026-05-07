package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlinkESP extends TarModule {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout")
        .description("Ticks to wait before assuming blinking after sprint start.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
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

    private final Map<PlayerEntity, Integer> pending = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public BlinkESP() {
        super(TarAddon.CATEGORY, "blink-esp", "Highlights blinking players");
    }

    @Override
    public void onActivate() {
        pending.clear();
        tickCounter = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!Utils.canUpdate()) return;
        if (event.packet instanceof EntityTrackerUpdateS2CPacket(
            int id, java.util.List<DataTracker.SerializedEntry<?>> trackedValues
        )) {
            mc.execute(() -> {
                if (mc.world == null) return;
                Entity entity = mc.world.getEntityById(id);
                if (!(entity instanceof PlayerEntity player)) return;

                for (DataTracker.SerializedEntry<?> entry : trackedValues) {
                    if (entry.id() == 0 && entry.value() instanceof Byte flags) {
                        if (player != mc.player && (flags & 0x08) != 0 && !pending.containsKey(player)) {
                            pending.put(player, tickCounter);
                        }
                        break;
                    }
                }
            });
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        tickCounter++;

        pending.entrySet().removeIf(entry -> {
            PlayerEntity player = entry.getKey();
            if (player.isRemoved()) return true;

            return player.getX() != player.lastX ||
                player.getY() != player.lastY ||
                player.getZ() != player.lastZ;
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        for (Map.Entry<PlayerEntity, Integer> entry : pending.entrySet()) {
            PlayerEntity player = entry.getKey();

            if (tickCounter - entry.getValue() >= timeout.get()) {
                Vec3d pos = player.getEntityPos();
                double x = pos.x, y = pos.y, z = pos.z;
                double width = 0.6, height = 1.6;
                double minX = x - width / 2, maxX = x + width / 2;
                double minZ = z - width / 2, maxZ = z + width / 2;
                double maxY = y + height;

                event.renderer.box(minX, y, minZ, maxX, maxY, maxZ, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }
}
