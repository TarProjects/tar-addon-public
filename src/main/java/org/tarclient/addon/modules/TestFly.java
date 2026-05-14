package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

import java.util.List;


public class TestFly extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<IntRange>> test = sgGeneral.add(new IntRangeListSetting.Builder()
            .name("test")
            .description("test")
            .min(0)
            .max(10)
        .build()
    );

    public TestFly() {
        super(TarAddon.CATEGORY, "test", "");
    }

    @Override
    public void onActivate() {
        System.out.println(test.get());
    }

    @EventHandler
    private void onBreakBlock(final StartBreakingBlockEvent event) {
        System.out.println(event.blockPos);
        System.out.println(event.direction);
    }

}
