package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.ColorUtils;
import org.tarclient.addon.utils.MiningUtils;
import org.tarclient.addon.utils.RenderUtils;

import static org.tarclient.addon.utils.MiningUtils.Breaking;
import static org.tarclient.addon.utils.MiningUtils.getBreaking;

public class BreakESP extends TarModule {
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<RenderUtils.BreakAnimation> animation = sgRender.add(new EnumSetting.Builder<RenderUtils.BreakAnimation>()
        .name("animation")
        .description("What animation to use on render")
        .defaultValue(RenderUtils.BreakAnimation.Grow)
        .build()
    );

    private final Setting<SettingColor> startSideColor = sgRender.add(new ColorSetting.Builder()
        .name("start-side-color")
        .description("The side color of the target box rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> startLineColor = sgRender.add(new ColorSetting.Builder()
        .name("start-line-color")
        .description("The line color of the target box rendering.")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );

    private final Setting<SettingColor> endSideColor = sgRender.add(new ColorSetting.Builder()
        .name("end-side-color")
        .description("The side color of the target box rendering.")
        .defaultValue(new SettingColor(0, 255, 0, 70))
        .build()
    );

    private final Setting<SettingColor> endLineColor = sgRender.add(new ColorSetting.Builder()
        .name("end-line-color")
        .description("The line color of the target box rendering.")
        .defaultValue(new SettingColor(0, 255, 0))
        .build()
    );

    private double animationProgress = 0;
    private double lastAnimationProgress = 0;
    private BlockPos pos = null;

    public BreakESP() {
        super(TarAddon.CATEGORY, "break-esp", "Renders currently breaking block");
    }

    @Override
    public void onActivate() {
        animationProgress = 0;
        lastAnimationProgress = 0;
        pos = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        Breaking breaking = getBreaking();
        if (mc.world == null || breaking == null) {
            animationProgress = 0;
            lastAnimationProgress = 0;
            pos = null;
            return;
        }

        if (breaking.blockPos != pos) {
            animationProgress = 0;
            lastAnimationProgress = 0;
        }

        pos = breaking.blockPos;

        lastAnimationProgress = animationProgress;

        animationProgress = MiningUtils.getBreakingProgress();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (pos == null) return;

        double progress = MathHelper.lerp(animationProgress - lastAnimationProgress, lastAnimationProgress, event.tickDelta);

        Color line = ColorUtils.lerp(startLineColor.get(), endLineColor.get(), progress);
        Color side = ColorUtils.lerp(startSideColor.get(), endSideColor.get(), progress);


        event.renderer.box(RenderUtils.getBreakingAnimationBox(pos, progress, animation.get()), side, line, shapeMode.get(), 0);
    }
}
