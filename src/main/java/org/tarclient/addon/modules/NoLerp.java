package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.settings.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class NoLerp extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
        .name("self")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> checkDistanceMoved = sgGeneral.add(new BoolSetting.Builder()
        .name("check-distance-moved")
        .description("Only triggers when specific distance moved")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> distanceToMove = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance-to-move")
        .description("Distance to move before not lerping")
        .defaultValue(5)
        .sliderRange(0, 10)
        .visible(checkDistanceMoved::get)
        .build()
    );

    public final Setting<LerpMode> lerpMode = sgGeneral.add(new EnumSetting.Builder<LerpMode>()
        .name("lerp-mode")
        .defaultValue(LerpMode.Instant)
        .build()
    );

    public NoLerp() {
        super(TarAddon.CATEGORY, "no-lerp", "Modifies the vanilla entity lerping");
    }


    public enum LerpMode {
        Instant,
        Fast,
        Normal
    }
}
