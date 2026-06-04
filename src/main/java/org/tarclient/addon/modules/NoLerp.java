package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class NoLerp extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
        .name("self")
        .defaultValue(false)
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
