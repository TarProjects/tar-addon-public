package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class RespawnMessage extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();


    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("message")
        .description("Message to send")
        .defaultValue("/kit 1")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("When to trigger the message")
        .defaultValue(20)
        .sliderRange(0, 20)
        .build()
    );
    int counter = 0;
    boolean active = false;

    public RespawnMessage() {
        super(TarAddon.CATEGORY, "respawn-message", "Sends a message on respawn");
    }

    @Override
    public void onActivate() {
        counter = 0;
        active = false;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (active) {
            if (counter == 0) {
                active = false;
                ChatUtils.sendPlayerMsg(message.get(), false);
                return;
            }

            counter--;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerRespawnS2CPacket && !active) {
            counter = delay.get();
            active = true;
        }
    }

}
