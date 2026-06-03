package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.HashSet;
import java.util.Set;

public class ModifyESP extends TarModule {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range where blocks will be rendered")
        .defaultValue(10)
        .min(0)
        .sliderMax(32)
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

    private final Set<BlockPos> blocks = new HashSet<>();


    public ModifyESP() {
        super(TarAddon.CATEGORY, "modify-esp", "Highlights modified blocks");
    }

    @Override
    public void onActivate() {
        blocks.clear();
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!event.oldState.equals(event.newState)) { // change state
            blocks.add(event.pos);
        }
    }


    @EventHandler
    private void onRender(Render3DEvent event) {
        blocks.forEach((pos) -> {
            if (mc.player == null) return;
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return;
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        });
    }
}
