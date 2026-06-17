package org.tarclient.addon.modules;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
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

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.getNetworkHandler() == null) return;
        if (event.packet instanceof PlayerListS2CPacket packet) {
            info(packet.getActions().toString());

            if (packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LATENCY)) {
                for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
                    info(String.valueOf(entry.profileId()));
                    mc.getNetworkHandler().getPlayerList().forEach((playerListEntry) -> {
                        GameProfile profile = playerListEntry.getProfile();
                        if (profile == null) return;
                        if (!Objects.equals(profile.name(), "FreedomForSkids1")) return;
                        if (profile.id() == entry.profileId()) {
                            if (packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LATENCY)) {
                                info(String.valueOf(entry.latency()));
                            }
                        }
                    });
                }
            }


        }
    }


}
