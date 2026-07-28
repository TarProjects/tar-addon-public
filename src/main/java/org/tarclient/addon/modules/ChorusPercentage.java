package org.tarclient.addon.modules;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.joml.Vector3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.ChorusUtils;

import java.util.UUID;

public class ChorusPercentage extends TarModule {
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the nametag")
        .defaultValue(1.1)
        .min(0.1)
        .build()
    );

    private final Setting<Integer> range = sgRender.add(new IntSetting.Builder()
        .name("range")
        .description("Range of checking")
        .defaultValue(6)
        .range(1, 8)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of text")
        .defaultValue(new SettingColor(232, 185, 35))
        .build()
    );


    /*private final Setting<Boolean> self = sgRender.add(new BoolSetting.Builder()
        .name("self")
        .description("Message to self")
        .defaultValue(true)
        .build()
    );

     */
    private final Vector3d pos = new Vector3d();
    private final Object2FloatMap<UUID> percentages = new Object2FloatOpenHashMap<>();

    public ChorusPercentage() {
        super(TarAddon.CATEGORY, "chorus-percentage", "Renders chance of tp");
    }

    @Override
    public void onActivate() {
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
        percentages.clear();
        for (PlayerEntity playerEntity : mc.world.getPlayers()) {
            if (playerEntity.getInventory().getSelectedStack().getItem() == Items.CHORUS_FRUIT) {
                float chance = ChorusUtils.landingPercentage(mc.world, playerEntity.getEntityPos(), mc.player.getEntityPos(), range.get());
                percentages.put(playerEntity.getUuid(), chance);
            }
        }
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;
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
}
