package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
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

    PlayerPositionLookS2CPacket packet = null;

    @Override
    public void onActivate() {
        packet = null;
        info(mc.world.getRegistryKey().getValue().getPath());
    }

    @Override
    public void onDeactivate() {
        if (true) return;
       /* if (packet != null) {
            packet.apply(mc.getNetworkHandler());
        }

        */
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        BlockState state = mc.world.getBlockState(mc.player.getBlockPos());
        System.out.println(state.isAir() || state.getBlock() == Blocks.LIGHT);
    }

    @EventHandler
    private void onResourcePackResponse(PacketEvent.Send event) {
        if (event.packet instanceof ResourcePackStatusC2SPacket(
            java.util.UUID id, ResourcePackStatusC2SPacket.Status status
        )) {
            //System.out.println(id);
            //System.out.println(status);
        }
    }

    @EventHandler
    private void onResourcePackReceive(PacketEvent.Receive event) {
        if (true) return;
        if (event.packet instanceof EntitiesDestroyS2CPacket packet) {
            info(packet.getEntityIds().toString());
        }
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            info("TELEPORT");
            System.out.println("--TELEPORT--");
            System.out.println(packet.teleportId());
            System.out.println(packet.change().position().x);
            System.out.println(packet.change().position().z);
            System.out.println("------------");
            if (this.packet == null) {
                this.packet = packet;
            }


            System.out.println("cancelled");

            event.cancel();
        }
    }


}
