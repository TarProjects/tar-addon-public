package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class InventoryFixes extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();


    public final Setting<Boolean> insertSlot = sgGeneral.add(new BoolSetting.Builder()
        .name("bypass-insert-slot")
        .description("Should let you place on restricted slots")
        .defaultValue(true)
        .build()
    );

    public InventoryFixes() {
        super(TarAddon.CATEGORY, "inventory-fixes", "Modifies inventory behaviour");
    }
}
