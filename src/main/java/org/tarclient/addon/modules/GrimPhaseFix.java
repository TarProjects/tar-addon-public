package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.PlayerJumpEvent;
import org.tarclient.addon.mixin.LivingEntityAccessor;
import org.tarclient.addon.utils.BurrowUtils;

public class GrimPhaseFix extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> shrinkBox = sgGeneral.add(new DoubleSetting.Builder()
        .name("shrink-box")
        .description("Shrinks the collision checks")
        .defaultValue(0)
        .sliderRange(0, 0.3)
        .range(0, 0.3)
        .build()
    );


    private final Setting<Integer> flagCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("flag-cooldown")
        .description("Cooldown for flagging")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    // derived from the normal jumping while standing!
    private static final double OFFSET = 0.2;

    int cooldown;

    public GrimPhaseFix() {
        super(TarAddon.CATEGORY, "grim-phase-fix", "Crit in blocks more efficiently");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
    }

    @EventHandler
    private void onJump(PlayerJumpEvent event) {
        if (mc.player == null) return;
        if (cooldown > 0) return;

        Box playerBox = mc.player.getBoundingBox();
        // checking from 0.6 blocks cuz that what is allowed
        Box upper = mc.player.getBoundingBox().offset(0, 0.6, 0);

        Box box = new Box(
            upper.minX,
            Math.ceil(playerBox.maxY), // maxY of original box
            upper.minZ,
            upper.maxX,
            upper.maxY,
            upper.maxZ
        );

        // extra has blocks so we should be able to lowjump
        // player phase && above == blocks
        double shrink = Math.clamp(shrinkBox.get(), 0, 0.2999);
        if (BurrowUtils.isPlayerPhased(mc.player, playerBox, shrink) && BurrowUtils.isPlayerPhased(mc.player, box, shrink)) {
            event.cancel();

            // replicate jump!
            mc.player.setPosition(mc.player.getEntityPos().add(0, OFFSET, 0));
            mc.player.setVelocity(mc.player.getVelocity().getX(), 0, mc.player.getVelocity().getZ());

            mc.player.velocityDirty = true;
        }
    }


    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (mc.player.input.playerInput.sneak() || mc.player.isSneaking()) {
            mc.player.setSprinting(false);
        }

        ((LivingEntityAccessor) mc.player).tar$setJumpCooldown(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        if (mc.player == null) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            cooldown = flagCooldown.get();
        }
    }
}
