package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class ItemUse extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How many ticks to wait between batch")
        .defaultValue(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> batch = sgGeneral.add(new IntSetting.Builder()
        .name("batch")
        .description("How many item use packets to send")
        .defaultValue(5)
        .sliderRange(0, 10)
        .build()
    );

    int cooldown = 0;

    public ItemUse() {
        super(TarAddon.CATEGORY, "item-use", "Spams item uses. Useful for e.g. farming xp at spawn on cc");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        for (int i = 0; i < batch.get(); i++) {
            sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        }

        cooldown = delay.get();
    }
}
