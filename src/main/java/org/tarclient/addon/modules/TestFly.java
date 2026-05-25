package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

import java.util.List;


public class TestFly extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between teleporting")
        .defaultValue(1)
        .sliderRange(0, 100)
        .build()
    );


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
        info(mc.world.getRegistryKey().getValue().getPath());
    }


    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
            if (getPlayerSpeed().horizontalLength() > 4) {
                info("TIMER");
            }

    }

    public Vec3d getPlayerSpeed() {
        if (mc.player == null) return Vec3d.ZERO;

        double tX = mc.player.getX() - mc.player.lastX;
        double tY = mc.player.getY() - mc.player.lastY;
        double tZ = mc.player.getZ() - mc.player.lastZ;

        tX *= 20;
        tY *= 20;
        tZ *= 20;

        return new Vec3d(tX, tY, tZ);
    }
}
