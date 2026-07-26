package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
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

    Vec3d oldPos = null;
    public TestFly() {
        super(TarAddon.CATEGORY, "test", "");
    }

    @Override
    public void onActivate() {
        timer = 0;
        oldPos = null;
    }

    @Override
    public void onDeactivate() {

    }

    boolean wasReplaceable = false;
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        BlockPos base = mc.player.getBlockPos().up().offset(Direction.NORTH);
        boolean replaceable = mc.world.getBlockState(base).isReplaceable();
        if (!wasReplaceable && replaceable) {

            System.out.println("Switch from pre at " + System.currentTimeMillis());
            BlockHitResult result = new BlockHitResult(mc.player.getBlockPos().offset(Direction.NORTH).up().toBottomCenterPos(), Direction.UP, mc.player.getBlockPos().offset(Direction.NORTH), false);
            sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, 0));
        }

        wasReplaceable = replaceable;
    }

    @EventHandler
    private void onBlockChange(BlockUpdateEvent event) {

    }

    @EventHandler
    private void onPacketSend(PacketEvent.Sent event) {

    }

    // setheadyaw positionsync rotateandmoverelative
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            info("flag");
        }


        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (packet.getState().isReplaceable() && packet.getPos().equals(mc.player.getBlockPos().up().offset(Direction.NORTH))) {
                System.out.println("Packet thread at " + System.currentTimeMillis());
            }
        }
    }
}
