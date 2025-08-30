package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;


public class PearlPhase extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> angle = sgGeneral.add(new DoubleSetting.Builder()
        .name("angle")
        .description("Angle where the pearl is thrown at")
        .defaultValue(85)
        .range(-90d, 90d)
        .sliderRange(-90d, 90d)
        .build()
    );


    private final Setting<Boolean> ncp = sgGeneral.add(new BoolSetting.Builder()
        .name("ncp")
        .description("Bypasses anticheat using a firecharge")
        .defaultValue(true)
        .build()
    );

    public PearlPhase() {
        super(TarAddon.CATEGORY, "pearl-phase", "Phases inside a block using an ender pearl");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate() && !mc.player.isOnGround()) {
            toggle();
            return;
        }

        FindItemResult enderpearl = InvUtils.find(Items.ENDER_PEARL);
        if (!enderpearl.found()) {
            error("No enderpearls in inventory!");
            toggle();
            return;
        }

        FindItemResult firecharge = InvUtils.find(Items.FIRE_CHARGE);
        if (ncp.get() && !firecharge.found()) {
            error("No firecharge in inventory!");
            toggle();
            return;
        }

        if (ncp.get()) {
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw(), 90, mc.player.isOnGround(), mc.player.horizontalCollision));

            InvUtils.swap(firecharge.slot(), true);
            sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(mc.player.getBlockPos().down().toCenterPos(), Direction.UP, mc.player.getBlockPos().down(), false), 0));
            InvUtils.swapBack();

        }

        // Note that rotation might not be neccessary due to new interactitem?
        sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw(), angle.get().floatValue(), mc.player.isOnGround(), mc.player.horizontalCollision));

        InvUtils.swap(enderpearl.slot(), true);
        sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw(), angle.get().floatValue()));
        InvUtils.swapBack();

        this.toggle();
    }

    private float yaw() {
        return (float) Rotations.getYaw(new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0.0, Math.floor(mc.player.getZ()) + 0.5)) + 180;
    }
}
