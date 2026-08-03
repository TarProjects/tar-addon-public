package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;


public class Crasher extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("Amount of packets sent")
        .defaultValue(1)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates")
        .defaultValue(true)
        .build()
    );


    public Crasher() {
        super(TarAddon.CATEGORY, "crasher", "Spams interaction packets, use chipped anvils to get desired result");
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        Vec3d hitPos = mc.player.getBlockPos().toBottomCenterPos();

        if (rotate.get()) {
            double yaw = Rotations.getYaw(hitPos);
            double pitch = Rotations.getPitch(hitPos);
            Rotations.rotate(yaw, pitch, () -> {
                // blockhitresult below
                BlockHitResult result = new BlockHitResult(hitPos, Direction.UP, mc.player.getBlockPos().down(), false);
                for (int i = 0; i < amount.get(); i++) {
                    sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, 0));
                }
            });
        } else {
            // blockhitresult below
            BlockHitResult result = new BlockHitResult(hitPos, Direction.UP, mc.player.getBlockPos().down(), false);
            for (int i = 0; i < amount.get(); i++) {
                sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, 0));
            }
        }
    }
}
