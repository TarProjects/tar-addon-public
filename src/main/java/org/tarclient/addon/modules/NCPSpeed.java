package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.MovementInputEvent;
import org.tarclient.addon.mixin.LivingEntityAccessor;

import java.util.Objects;

public class NCPSpeed extends TarModule {

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> pullDown = sgGeneral.add(new BoolSetting.Builder()
        .name("pull-down")
        .description("Pulls the player down")
        .defaultValue(true)
        .build()
    );

    private static final double SPEED_CONSTANT = 0.199999999;
    private static final double GROUND_CONSTANT = 0.281;
    private static final double AIR_CONSTANT = 0.2;
    private static final double BOOST_CONSTANT = 0.00718;

    private int airTicks = 0;

    public NCPSpeed() {
        super(TarAddon.CATEGORY, "ncp-speed", "Fast speed");
    }

    @Override
    public void onActivate() {
        airTicks = 0;
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (PlayerUtils.isMoving()) {
            event.input = new PlayerInput(event.input.forward(), event.input.backward(), event.input.left(), event.input.right(),
                true, event.input.sneak(), event.input.sprint());
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Post event) {
        if (mc.player == null) return;

        ((LivingEntityAccessor) mc.player).tar$setJumpCooldown(0);

        Vec3d movement = mc.player.getMovement();

        if (pullDown.get()) {
            if (mc.player.isOnGround()) {
                airTicks = 0;
            } else {
                airTicks++;
                if (airTicks == 4) {
                    // do we need to strafe
                    double pull = 0.1892045;
                    movement = movement.subtract(0, pull, 0);
                }
            }
        }

        int speedAmp = 0;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            speedAmp = Objects.requireNonNull(mc.player.getStatusEffect(StatusEffects.SPEED)).getAmplifier();
        }

        if (PlayerUtils.isMoving()) {
            // boost
            double factor = 1 + (BOOST_CONSTANT);
            movement = movement.multiply(factor, 1, factor);

            if (mc.player.isOnGround()) {
                double groundMin = GROUND_CONSTANT + SPEED_CONSTANT * speedAmp;
                double currentSpeed = movement.horizontalLength();
                double targetSpeed = Math.max(currentSpeed, groundMin);
                movement = strafe(movement, targetSpeed, 1);
            } else {
                double groundMin = AIR_CONSTANT + SPEED_CONSTANT * speedAmp;
                double currentSpeed = movement.horizontalLength();
                double targetSpeed = Math.max(currentSpeed, groundMin);

                movement = strafe(movement, targetSpeed, 1);
            }
        }

        mc.player.setVelocity(movement);
    }

    private Vec3d strafe(Vec3d current, double speed, double strength) {
        if (mc.player == null || !PlayerUtils.isMoving()) return current;

        float forwardInput = mc.player.input.getMovementInput().y;
        float sidewaysInput = mc.player.input.getMovementInput().x;


        // Apply strength multiplier

        double side = sidewaysInput * strength;
        double forward = forwardInput * strength;

        double len = Math.hypot(side, forward);
        if (len != 0) {
            side /= len;
            forward /= len;
        }

        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double newX = side * cos - forward * sin;
        double newZ = side * sin + forward * cos;

        double factor = speed / Math.hypot(newX, newZ);
        newX *= factor;
        newZ *= factor;

        return new Vec3d(newX, current.y, newZ);
    }
}
