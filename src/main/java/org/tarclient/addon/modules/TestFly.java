package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.text.Text;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


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
        if (mc.player == null) return;
        mc.player.setOnGround(true);
        mc.player.noClip = false;
    }

    @EventHandler
    private void onResourcePackResponse(PacketEvent.Send event) {
        if (event.packet instanceof ResourcePackStatusC2SPacket(
            java.util.UUID id, ResourcePackStatusC2SPacket.Status status
        )) {
            System.out.println(id);
            System.out.println(status);
        }
    }

    @EventHandler
    private void onResourcePackReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ResourcePackSendS2CPacket(
            UUID id, String url, String hash, boolean required, Optional<Text> prompt
        )) {
            System.out.println(url);
            System.out.println(required);
            System.out.println(prompt);
        }
    }
}
