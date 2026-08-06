package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.*;

public class AntiGhostBlocks extends TarModule {
    private final SettingGroup sgRender = settings.createGroup("Render");

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

    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    public AntiGhostBlocks() {
        super(TarAddon.CATEGORY, "anti-ghost-blocks", "Disables weird breaking exploits!");
    }

    @Override
    public void onActivate() {
        renderQueue.clear();
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
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof WorldEventS2CPacket packet) {
            if (packet.getEventId() == 2001) {
                // hardcoded, block break
                mc.execute(() -> {
                    if (mc.world == null) return;
                    if (mc.world.getBlockState(packet.getPos()).isAir()) return; // isn't a ghost block, already air
                    mc.world.setBlockState(packet.getPos(), Blocks.AIR.getDefaultState());
                    renderQueue.put(packet.getPos(), fadeTime.get());
                });
            }
        }
    }
}
