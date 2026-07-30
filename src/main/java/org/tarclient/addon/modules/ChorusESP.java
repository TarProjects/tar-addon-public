package org.tarclient.addon.modules;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.ChorusUtils;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChorusESP extends TarModule {
    private final SettingGroup sgTeleportESP = settings.createGroup("Teleport ESP");
    private final SettingGroup sgPercentage = settings.createGroup("Percentage");

    /* --- Teleport ESP --- */
    private final Setting<Boolean> teleportEsp = sgPercentage.add(new BoolSetting.Builder()
        .name("teleport-esp")
        .description("Display chorus teleports")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ticks = sgTeleportESP.add(new IntSetting.Builder()
        .name("ticks")
        .description("The amount of ticks to render each teleport")
        .defaultValue(60)
        .sliderRange(0, 100)
        .visible(teleportEsp::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgTeleportESP.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(teleportEsp::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgTeleportESP.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the target sound rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .visible(teleportEsp::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgTeleportESP.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the target sound rendering.")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(teleportEsp::get)
        .build()
    );

    /* --- Percentage --- */
    private final Setting<Boolean> percentage = sgPercentage.add(new BoolSetting.Builder()
        .name("percentage")
        .description("Show chances of chorus fruit uses")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgPercentage.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the nametag")
        .defaultValue(1.1)
        .min(0.1)
        .visible(percentage::get)
        .build()
    );

    private final Setting<Integer> range = sgPercentage.add(new IntSetting.Builder()
        .name("range")
        .description("Range of checking")
        .defaultValue(6)
        .range(1, 8)
        .visible(percentage::get)
        .build()
    );

    private final Setting<SettingColor> color = sgPercentage.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of text")
        .defaultValue(new SettingColor(232, 185, 35))
        .visible(percentage::get)
        .build()
    );

    /*private final Setting<Boolean> self = sgPercentage.add(new BoolSetting.Builder()
        .name("self")
        .description("Message to self")
        .defaultValue(true)
        .build()
    );
     */

    private final Vector3d pos = new Vector3d();
    private final Object2FloatMap<UUID> percentages = new Object2FloatOpenHashMap<>();

    final CopyOnWriteArrayList<SoundDelay> renderQueue = new CopyOnWriteArrayList<>();

    public ChorusESP() {
        super(TarAddon.CATEGORY, "chorus-esp", "Renders chorus fruit sounds");
    }

    @Override
    public void onActivate() {
        renderQueue.clear();
        percentages.clear();
    }

    @EventHandler
    private void onRemove(EntityRemovedEvent event) {
        UUID uuid = event.entity.getUuid();
        percentages.removeFloat(uuid);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        if (teleportEsp.get()) {
            for (SoundDelay delay : renderQueue) {
                delay.ticks--;
                if (delay.ticks <= 0) {
                    renderQueue.remove(delay);
                }
            }
        }

        if (percentage.get()) {
            // wasty
            percentages.clear();
            for (PlayerEntity playerEntity : mc.world.getPlayers()) {
                if (playerEntity.getInventory().getSelectedStack().getItem() == Items.CHORUS_FRUIT) {
                    float chance = ChorusUtils.landingPercentage(mc.world, playerEntity.getEntityPos(), mc.player.getEntityPos(), range.get());
                    percentages.put(playerEntity.getUuid(), chance);
                }
            }
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (!teleportEsp.get()) return;
        if (event.sound.getId().equals(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT.id())) {
            renderQueue.add(new SoundDelay(new Vec3d(event.sound.getX(), event.sound.getY(), event.sound.getZ()), ticks.get()));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!teleportEsp.get()) return;
        renderQueue.forEach(pos -> {
            double x = pos.pos.x;
            double y = pos.pos.y;
            double z = pos.pos.z;

            double min_x = x - 0.6 / 2;
            double max_x = x + 0.6 / 2;

            double max_y = y + 1.6;

            double min_z = z - 0.6 / 2;
            double max_z = z + 0.6 / 2;
            event.renderer.box(min_x, y, min_z, max_x, max_y, max_z, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        });

    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (!percentage.get()) return;
        boolean shadow = Config.get().customFont.get();
        TextRenderer text = TextRenderer.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            float percentage = percentages.getOrDefault(player.getUuid(), -1);
            if (percentage == -1) continue;

            Utils.set(pos, player, event.tickDelta);
            pos.y += player.getHeight() / 2;

            if (!NametagUtils.to2D(pos, scale.get())) continue;

            NametagUtils.begin(pos, event.drawContext);

            String textStr = String.format("%.0f%%", percentage * 100);

            double textWidth = text.getWidth(textStr, shadow);
            double textHeight = text.getHeight(shadow);
            text.beginBig(); // or use text.begin() for custom size
            text.render(textStr, -textWidth / 2, -textHeight / 2, color.get(), shadow);
            text.end();

            NametagUtils.end(event.drawContext);
        }
    }

    // 2d Map instead of weird data class?
    static class SoundDelay {
        final Vec3d pos;
        int ticks;

        public SoundDelay(Vec3d pos, int ticks) {
            this.pos = pos;
            this.ticks = ticks;
        }
    }

}
