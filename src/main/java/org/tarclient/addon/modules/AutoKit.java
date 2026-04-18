package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;

public class AutoKit extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("What height triggers this module")
        .defaultValue(120)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<String> kit = sgGeneral.add(new StringSetting.Builder()
        .name("kit")
        .description("What is the name of the kit you want to use")
        .defaultValue("kit")
        .build()
    );

    private final Setting<List<Item>> xcarry = sgGeneral.add(new ItemListSetting.Builder()
        .name("x-carry")
        .description("Which items to put in X-Carry. Leave empty if you don't want to use this")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("When to trigger X-Carry")
        .defaultValue(20)
        .sliderRange(0, 20)
        .visible(() -> !xcarry.get().isEmpty())
        .build()
    );

    boolean shouldKit;
    boolean shouldXcarry = false;
    int ticks = 0;

    public AutoKit() {
        super(TarAddon.CATEGORY, "auto-kit", "Module to automatically call /kit. Designed for crystalpvp.cc, so might not work for other servers.");
    }

    @Override
    public void onActivate() {
        shouldKit = true;
        shouldXcarry = false;
        ticks = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerRespawnS2CPacket) {
            shouldKit = true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            this.toggle();
            return;
        }

        if (shouldXcarry) {
            ticks++;
            if (ticks >= delay.get()) {
                if (!xcarry.get().isEmpty()) {
                    info("Moving items into XCarry");
                    // Only 4 slots exist...
                    int count = 1;
                    // Loop through main inventory, excluding armor, offhand, crafting
                    for (int i = 9; i < 45; i++) {
                        if (xcarry.get().contains(mc.player.getInventory().getStack(i).getItem())) {
                            InvUtils.move().fromId(i).toId(count);
                            count++;
                            if (count >= 5) {
                                break;
                            }
                        }
                    }
                }
                shouldXcarry = false;
            }
        }

        if (mc.player.getY() < height.get() && shouldKit) {
            mc.getNetworkHandler().sendChatCommand("kit " + kit.get());
            shouldKit = false;
            shouldXcarry = true;
            ticks = 0;
        }
    }
}
