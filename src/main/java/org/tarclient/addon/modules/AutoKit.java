package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
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

    private final Setting<String> worldName = sgGeneral.add(new StringSetting.Builder()
        .name("world-name")
        .description("Which world should we /kit in")
        .defaultValue("kit")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How much time to wait before respawn to kit")
        .defaultValue(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<List<Item>> xCarry = sgGeneral.add(new ItemListSetting.Builder()
        .name("x-carry")
        .description("Which items to put in X-Carry. Leave empty if you don't want to use this")
        .build()
    );

    private final Setting<Integer> xCarryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("x-carry-delay")
        .description("When to trigger X-Carry")
        .defaultValue(20)
        .sliderRange(0, 20)
        .visible(() -> !xCarry.get().isEmpty())
        .build()
    );

    private Stage stage;
    int ticks = 0;

    public AutoKit() {
        super(TarAddon.CATEGORY, "auto-kit", "Module to automatically call /kit. Designed for crystalpvp.cc, so might not work for other servers.");
    }

    @Override
    public void onActivate() {
        stage = Stage.Wait;
        ticks = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Send event) {
        if (event.packet instanceof ClientStatusC2SPacket packet) {
            if (packet.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN) {
                stage = Stage.Wait;
                ticks = delay.get();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        switch (stage) {
            case Wait -> {
                // pre-death -> already kitted
                if (ticks <= -1) return;
                if (ticks > 0) { // countdown to 0
                    ticks--;
                    return;
                }

                // here checks out :P
                stage = Stage.Kit;
            }
            case Kit -> {
                String arena = mc.world.getRegistryKey().getValue().getPath();
                if (!arena.equals(worldName.get())) return;

                boolean goingDown = mc.player.getY() < mc.player.lastY;
                if (goingDown && mc.player.getY() < height.get()) {
                    mc.getNetworkHandler().sendChatCommand("kit " + kit.get());
                    ticks = xCarryDelay.get();
                    stage = Stage.XCarry;
                }
            }
            case XCarry -> {
                if (ticks > 0) {
                    ticks--;
                    return;
                }

                if (!xCarry.get().isEmpty()) {
                    info("Moving items into XCarry");
                    // Only 4 slots exist...
                    int count = 0;
                    // Loop through main inventory, excluding armor, offhand, crafting
                    for (int i = 9; i < 45; i++) {
                        if (xCarry.get().contains(mc.player.getInventory().getStack(i).getItem())) {
                            InvUtils.move().fromId(i).toId(count + 1);
                            count++;
                            if (count >= 4) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private enum Stage {
        Wait,
        Kit,
        XCarry
    }
}
