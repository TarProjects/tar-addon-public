package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.settings.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;

public class MioCompatibility extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Global toggle")
        .defaultValue(true)
        .build()
    );

    public final Setting<String> mioPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("mio-prefix")
        .description("Mio prefix")
        .defaultValue(";")
        .build()
    );

    public final Setting<String> togglePacketMine = sgGeneral.add(new StringSetting.Builder()
        .name("toggle-packetmine")
        .description("How to reset mining progress when needed for compatibility")
        .defaultValue("SpeedMine")
        .wide()
        .build()
    );

    public final Setting<Boolean> ignoreNotif = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-notifications")
        .description("Ignores notifications sent to the player")
        .defaultValue(true)
        .build()
    );

    public final Setting<String> ignoreNotifPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("ignore-notifications-prefix")
        .description("What is the pattern on where to ignore notifications")
        .defaultValue("[Mio] [!] Notifications Modules:")
        .visible(ignoreNotif::get)
        .wide()
        .build()
    );

    public final Setting<String> toggleNotificationsMessage = sgGeneral.add(new StringSetting.Builder()
        .name("toggle-notifications-message")
        .description("How to toggle notifications")
        .defaultValue("notifications Modules toggle")
        .wide()
        .build()
    );


    public final Setting<List<String>> toggleAttackingModules = sgGeneral.add(new StringListSetting.Builder()
        .name("toggle-attacking-modules")
        .description("Modules to toggle in each their own line. Leave as empty to disable")
        .defaultValue("Aura", "CrystalAura", "HoleFill")
        .build()
    );

    public MioCompatibility() {
        super(TarAddon.CATEGORY, "mio-compatibility", "Required for some features");
    }

    // disable toggling
    @Override
    public void toggle() {
    }
}
