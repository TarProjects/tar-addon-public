package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.mixin.FireworkRocketEntityAccessor;

public class FakeFly extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks before using a new firework when the current is about to expire")
        .defaultValue(3)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Cooldown for onground state")
        .defaultValue(5)
        .sliderRange(0, 20)
        .build()
    );


    private final Setting<Boolean> boost = sgGeneral.add(new BoolSetting.Builder()
        .name("boost")
        .description("Boosts you")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableIfNoRockets = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-if-no-rockets")
        .description("Disables if rockets werent found")
        .defaultValue(true)
        .visible(boost::get)
        .build()
    );


    private final Setting<Integer> rocketLag = sgGeneral.add(new IntSetting.Builder()
        .name("rocket-lag")
        .description("Max lag in ticks for rocket to attach to you")
        .defaultValue(10)
        .sliderRange(0, 20)
        .visible(boost::get)
        .build()
    );


    private final Setting<Double> speedBps = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-bps")
        .description("Your speed when flying")
        .defaultValue(0.1)
        .min(0.0)
        .visible(boost::get)
        .build()
    );


    int lastFirework;
    int ticks;

    public FakeFly() {
        super(TarAddon.CATEGORY, "fake-fly", "Flies using silent swaps to avoid damage and such");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        lastFirework = ticks - rocketLag.get();
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (mc.player == null) return;
        if (!mc.player.isGliding()) return;
        if (!boost.get()) return;
        // 3 tick check for pretick in order to firework
        if (ticksLeft() <= 0) return;

        Vec3d direction = getDirectionVector();

        if (direction.equals(Vec3d.ZERO)) {
            ((IVec3d) event.movement).meteor$set(Vec3d.ZERO.subtract(0, 1e-5, 0));
            return;
        }

        Vec3d movement = direction.multiply(speedBps.get() / 20).subtract(0, 1e-5, 0);
        Vec3d self = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d total = self.add(movement);

        double yaw = Rotations.getYaw(total);
        double pitch = Rotations.getPitch(total);
        Rotations.rotate(yaw, pitch);

        ((IVec3d) event.movement).meteor$set(movement);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        FindItemResult firework = InvUtils.find(Items.FIREWORK_ROCKET);
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!firework.found() && boost.get() && disableIfNoRockets.get()) {
            error("Fireworks not found!");
            this.toggle();
            return;
        }
        if (!elytra.found()) {
            error("Elytra not found!");
            this.toggle();
            return;
        }

        if (mc.player.isOnGround()) {
            // reset but keep relative offset
            if (ticks == -cooldown.get()) {
                // already reset, count backwards (hacky but works)
                lastFirework--;
            } else {
                lastFirework -= (ticks - cooldown.get());
                ticks = -cooldown.get();
            }
            return;
        }

        // cooldown
        if (!mc.player.isGliding()) {
            InvUtils.move().from(elytra.slot()).toArmor(2);
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startGliding();
            InvUtils.move().fromArmor(2).to(elytra.slot());
            if (shouldUseFirework()) useFirework(firework);
        }

        ticks++;
    }

    @EventHandler
    private void onPost(TickEvent.Post event) {
        if (mc.player == null) return;
        if (mc.player.isGliding()) mc.player.setPose(EntityPose.STANDING);
    }

    private Vec3d getDirectionVector() {
        if (mc.player == null) return Vec3d.ZERO;
        float forward = mc.player.input.getMovementInput().y;
        float strafe = mc.player.input.getMovementInput().x;

        float yaw = mc.player.getYaw();
        double sinYaw = MathHelper.sin(yaw * ((float) Math.PI / 180));
        double cosYaw = MathHelper.cos(yaw * ((float) Math.PI / 180));

        double vecX = strafe * cosYaw - forward * sinYaw;
        double vecZ = forward * cosYaw + strafe * sinYaw;
        double vecY = 0;

        if (mc.options.jumpKey.isPressed()) vecY = 1.0;
        else if (mc.options.sneakKey.isPressed()) vecY = -1.0;

        Vec3d direction = new Vec3d(vecX, vecY, vecZ);

        direction = direction.normalize();
        return direction;
    }

    private FireworkRocketEntity getNewestRocket() {
        if (mc.world == null || mc.player == null) return null;

        FireworkRocketEntity newest = null;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof FireworkRocketEntity rocket) || !rocket.isAlive()) continue;
            if (rocket.getOwner() == mc.player) {
                if (newest == null || getLife(rocket) < getLife(newest))
                    newest = rocket;
            }
        }

        return newest;
    }

    private int getLife(FireworkRocketEntity entity) {
        return ((FireworkRocketEntityAccessor) entity).tar$getLife();
    }

    private int getLifetime(FireworkRocketEntity entity) {
        int i = 1;
        FireworksComponent fireworksComponent = entity.getStack().get(DataComponentTypes.FIREWORKS);
        if (fireworksComponent != null) {
            i += fireworksComponent.flightDuration();
        }
        // see FireworkRocketEntity
        return 10 * i + 5;
    }

    private int ticksLeft() {
        FireworkRocketEntity newest = getNewestRocket();
        if (newest == null) {
            return 0;
        }
        return getLifetime(newest) - getLife(newest);
    }

    private boolean shouldUseFirework() {
        if (!boost.get()) return false;
        return ticksLeft() <= delay.get() && ticks - lastFirework >= rocketLag.get();
    }

    private void useFirework(FindItemResult result) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.isUsingItem()) return;
        swap(result.slot());
        sendPacketSilent(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, 0, 0));
        swap(result.slot());
        lastFirework = ticks;
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }
}
