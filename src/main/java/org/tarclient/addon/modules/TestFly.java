package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

import java.util.List;
import java.util.Objects;


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

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {

    }
    // setheadyaw positionsync rotateandmoverelative

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;

        if (event.packet instanceof EntityPositionSyncS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.id());
            if (entity == null || entity.getName() == null || !Objects.equals(entity.getName().getString(), "FreedomForSkids"))
                return;

            mc.execute(() -> info(packet.values().toString()));
        }
    }


}
