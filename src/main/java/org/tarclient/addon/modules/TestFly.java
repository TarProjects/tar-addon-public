package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.util.math.*;
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


    int timer = 0;

    public TestFly() {
        super(TarAddon.CATEGORY, "test", "");
    }

    @Override
    public void onActivate() {
        timer = 0;
        // send stop break just incase
        //sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, mc.player.getBlockPos(), Direction.UP));
        //sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, mc.player.getBlockPos(), Direction.UP));
    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
    }

    @EventHandler
    private void onBlockChange(BlockUpdateEvent event) {

    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ClickSlotC2SPacket packet) {
            //System.out.println("CLICK PACKET");
            //System.out.println(packet.slot());
            //mc.interactionManager.click
        }
    }
}
