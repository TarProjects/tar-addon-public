package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

/**
 * @concept hekt
 * @author nullable
 */
public class TickShift extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> chargeTicks = sgGeneral.add(new IntSetting.Builder()
        .name("ticks")
        .description("How many ticks to charge")
        .defaultValue(6)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> flagCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("flag-cooldown")
        .description("How many ticks to wait after flagging")
        .defaultValue(10)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Almost required on cc")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> resetOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-on-ground")
        .description("Almost required on cc")
        .defaultValue(false)
        .visible(onlyOnGround::get)
        .build()
    );

    private final Setting<Double> timerMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer")
        .description("What to set timer to")
        .defaultValue(2)
        .sliderRange(1, 4)
        .build()
    );

    private static final Timer timer = Modules.get().get(Timer.class);

    private int ticks;
    private int cooldownTicks;

    private static final double EPSILON = 1e-7;
    private double lastPacketX;
    private double lastPacketY;
    private double lastPacketZ;
    private boolean lastPacketGround;

    private int sentMovement;

    public TickShift() {
        super(TarAddon.CATEGORY, "tick-shift", "Speeds up game after no movement occurs...");
    }


    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }
        ticks = 0;
        cooldownTicks = 0;

        lastPacketX = mc.player.getX();
        lastPacketY = mc.player.getY();
        lastPacketZ = mc.player.getZ();
        lastPacketGround = mc.player.isOnGround();

        sentMovement = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) {
            toggle();
            return;
        }

        // reset to default
        timer.setOverride(1.0);

        if (cooldownTicks > 0) {
            cooldownTicks--;
            ticks = 0;
            sentMovement = 0;
            return;
        }

        boolean notOnGround = !mc.player.isOnGround() && onlyOnGround.get();

        if (notOnGround && resetOnGround.get()) {
            ticks = 0;
            sentMovement = 0;
            return;
        }

        if (isMoving() && !notOnGround) {
            // moving rn, wohoo
            // NOTE: this weird logic is used for 1 delay tick
            if (ticks > 1) {
                ticks--;
                timer.setOverride(timerMultiplier.get());
            } else {
                ticks = 0;
            }
        } else {
            // movement small :)
            // increment ticks by 1 if sentMovement is 0
            // decrease if sentMovement > 1
            ticks += 1 - sentMovement;
            ticks = Math.clamp(ticks, 0, chargeTicks.get()); // clamp to 0-charge
        }

        // reset
        sentMovement = 0;
    }


    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (!packet.changesLook()) handleMovementNoLook(packet, event);

            if (!event.isCancelled()) {
                // have to send a movement :(
                sentMovement++;
            }
        }
    }

    private void handleMovementNoLook(PlayerMoveC2SPacket packet, PacketEvent.Send event) {
        boolean onGround = packet.isOnGround();

        if (!packet.changesPosition()) {
            // only on-ground
            if (onGround == lastPacketGround) {
                event.cancel();
            }
            lastPacketGround = onGround;
            return;
        }

        double x = packet.getX(lastPacketX);
        double y = packet.getY(lastPacketY);
        double z = packet.getZ(lastPacketZ);

        boolean posChanged = Math.abs(x - lastPacketX) > EPSILON ||
            Math.abs(y - lastPacketY) > EPSILON ||
            Math.abs(z - lastPacketZ) > EPSILON;

        if (!posChanged && onGround == lastPacketGround) {
            event.cancel();
        }

        lastPacketX = x;
        lastPacketY = y;
        lastPacketZ = z;
        lastPacketGround = onGround;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            cooldownTicks = flagCooldown.get();
        }
    }

    public boolean isMoving() {
        if (mc.player == null) return false;
        return getPlayerSpeed().horizontalLength() > 4 && !mc.player.isSneaking();
    }

    public Vec3d getPlayerSpeed() {
        if (mc.player == null) return Vec3d.ZERO;

        double tX = mc.player.getX() - mc.player.lastX;
        double tY = mc.player.getY() - mc.player.lastY;
        double tZ = mc.player.getZ() - mc.player.lastZ;

        tX *= 20;
        tY *= 20;
        tZ *= 20;

        return new Vec3d(tX, tY, tZ);
    }

    @Override
    public String getInfoString() {
        return String.valueOf(ticks);
    }
}
