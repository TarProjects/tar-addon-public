package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChorusESP extends TarModule {

    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> ticks = sgRender.add(new IntSetting.Builder()
        .name("ticks")
        .description("The amount of ticks to render each teleport")
        .defaultValue(60)
        .sliderRange(0, 100)
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
        .description("The side color of the target sound rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the target sound rendering.")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );
    final CopyOnWriteArrayList<SoundDelay> renderq = new CopyOnWriteArrayList<>();

    public ChorusESP() {
        super(TarAddon.CATEGORY, "chorus-esp", "Renders chorus fruit sounds");
    }

    @Override
    public void onActivate() {
        renderq.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (SoundDelay delay : renderq) {
            delay.ticks--;
            if (delay.ticks <= 0) {
                renderq.remove(delay);
            }
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (event.sound.getId().equals(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT.id())) {
            renderq.add(new SoundDelay(new Vec3d(event.sound.getX(), event.sound.getY(), event.sound.getZ()), ticks.get()));
            event.cancel();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        renderq.forEach(pos -> {
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
