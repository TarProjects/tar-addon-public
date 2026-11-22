package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.mixin.LivingEntityAccessor;
import org.tarclient.addon.utils.PredictUtils;
import org.tarclient.addon.utils.SimulationState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KillAuraPlus extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Targeting
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range of entity before attacking. Uses multipoint calc")
        .defaultValue(3)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgGeneral.add(new BoolSetting.Builder()
        .name("TPS-sync")
        .description("Sync to server tps")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> hitDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("hit-delay")
        .description("Cooldown between attacking")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> waitForCritical = sgGeneral.add(new IntSetting.Builder()
        .name("wait-for-critical")
        .description("Waits for crit hit for this many ticks")
        .defaultValue(5)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> itemStackDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("item-stack-delay")
        .description("Delays based on where we are going to swap to...")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rangePrediction = sgGeneral.add(new IntSetting.Builder()
        .name("range-prediction")
        .description("Predicts range checks for 6 blocks.")
        .defaultValue(2)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> stopSprinting = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-sprinting")
        .description("Stops sprinting every hit")
        .defaultValue(false)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();

    public KillAuraPlus() {
        super(TarAddon.CATEGORY, "kill-aura-plus", "Meteor KillAura, less features but improved for grim");
    }

    @Override
    public void onActivate() {
        targets.clear();
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) return;
        Entity target = targets.getFirst();

        FindItemResult result = new FindItemResult(mc.player.getInventory().getSelectedSlot(), 1);

        FindItemResult dynamicItem = getItem();
        if (dynamicItem == null) {
            // mapped to elytra logic, goofy ik
            return;
        }

        if (dynamicItem.found()) result = dynamicItem;

        // ig bro
        if (!result.found()) return;


        int ticksSinceLastAttack = ((LivingEntityAccessor) mc.player).tar$getTicksSinceLastAttack();

        // not mace, no crits with that lol
        if (!mc.player.getInventory().getStack(result.slot()).isOf(Items.MACE) && waitForCritical.get() > 0 && !mc.player.isGliding()) {
            // same logic as above but with attack cd added for each tick!
            Vec3d velocity = mc.player.getVelocity();
            SimulationState state = new SimulationState(mc.player.getEntityPos(), velocity);

            boolean willCrit = false;


            for (int i = 0; i < rangePrediction.get(); i++) {
                double lastY = state.pos.getY();
                PredictUtils.advanceOneTick(mc.player, state, 0);
                // reach
                if (state.pos.add(0, mc.player.getStandingEyeHeight(), 0).squaredDistanceTo(targets.getFirst().getEyePos()) > (range.get() + 0.5) * (range.get() + 0.5))
                    continue;


                if (delayCheck(result, ticksSinceLastAttack + i)) {
                    if (state.vel.getY() < 0 && lastY > state.pos.getY()) {
                        willCrit = true;
                        break;
                    }
                }
            }

            // pause
            if (willCrit) return;
        }


        if (delayCheck(result, ticksSinceLastAttack)) {
            InvUtils.swap(result.slot(), true);
            attack(target);
            InvUtils.swapBack();
        }
    }

    private FindItemResult getItem() {
        if (mc.player == null) return null;
        if (mc.player.fallDistance > 1.5 && !mc.player.isGliding()) {
            // mace
            FindItemResult mace = InvUtils.find(Items.MACE);
            if (mace.found()) {
                return mace;
            }
        }

        if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) {
            return InvUtils.findInHotbar(stack -> stack.isIn(ItemTags.SWORDS));
        }

        return new FindItemResult(mc.player.getInventory().getSelectedSlot(), 1);
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        Vec3d eyePos = mc.player.getCameraPosVec(1);
        Vec3d closest = getClosestPosition(mc.player, entity);

        // single range check
        if (PlayerUtils.squaredDistance(eyePos.getX(), eyePos.getY(), eyePos.getZ(),
            closest.getX(), closest.getY(), closest.getZ()) > range.get() * range.get()) return false;

        if (!entities.get().contains(entity.getType())) return false;

        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }

        if (entity instanceof FakePlayerEntity fakePlayer) return !fakePlayer.noHit;

        // expensive range checks!
        Vec3d velocity = entity.getEntityPos().subtract(entity.lastX, entity.lastY, entity.lastZ);
        SimulationState state = new SimulationState(entity.getEntityPos(), velocity);
        for (int i = 0; i < rangePrediction.get(); i++) {
            PredictUtils.advanceOneTick(entity, state, 0, entity instanceof PlayerEntity player && player.isGliding());
            if (mc.player.squaredDistanceTo(state.pos) > 6 * 6) {
                // out of vanilla range in N ticks
                return false;
            }
        }

        return true;
    }

    // attribute swapping shouldnt slow this down, delay should be gotten from mainhand 100%
    private boolean delayCheck(FindItemResult result, int ticksSinceLastAttack) {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getInventory().getStack(result.slot());
        // mace should be used whenever if fallDistance is great enough
        if (stack.isOf(Items.MACE) && mc.player.fallDistance > 1.5 && !mc.player.isGliding()) return true;
        float baseTime = 0.5f;
        if (tpsSync.get()) baseTime /= (TickRate.INSTANCE.getTickRate() / 20);

        if (itemStackDelay.get() && !stack.isEmpty()) {
            return getCooldownProgress(stack, baseTime, ticksSinceLastAttack) >= hitDelay.get();
        } else {
            return getCooldownProgress(mc.player.getMainHandStack(), baseTime, ticksSinceLastAttack) >= hitDelay.get();
        }
    }

    private void attack(Entity target) {
        if (mc.player == null || mc.interactionManager == null) return;

        boolean stoppedSprinting = false;
        if (stopSprinting.get() && mc.player.isSprinting()) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            mc.player.setSprinting(false);

            stoppedSprinting = true;
        }

        Vec3d position = getClosestPosition(mc.player, target);

        sendRotatePacket(Rotations.getYaw(position), Rotations.getPitch(position), RotationPacket.Full);

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (stoppedSprinting) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            mc.player.setSprinting(true);
        }
    }

    private Vec3d getClosestPosition(PlayerEntity self, Entity target) {
        Vec3d eye = self.getCameraPosVec(1.0f);
        Box box = target.getBoundingBox();

        return getClosestPosition(eye, box);
    }

    private Vec3d getClosestPosition(Vec3d eye, Box box) {
        double closestX = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double closestY = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double closestZ = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        return new Vec3d(closestX, closestY, closestZ);
    }

    private float getCooldownProgress(ItemStack stack, float tickDelta, int ticksSinceLastAttack) {
        float attackSpeed = getAttackSpeed(stack);
        float cooldownTicks = 20.0F / attackSpeed;

        return MathHelper.clamp((ticksSinceLastAttack + tickDelta) / cooldownTicks, 0, 1);
    }

    private float getAttackSpeed(ItemStack stack) {
        final float BASE_ATTACK_SPEED = 4.0f;

        AttributeModifiersComponent component = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (component == null) {
            return BASE_ATTACK_SPEED;
        }

        float total = BASE_ATTACK_SPEED;
        for (AttributeModifiersComponent.Entry entry : component.modifiers()) {
            if (entry.attribute().value() != EntityAttributes.ATTACK_SPEED.value()) continue;

            float value = (float) entry.modifier().value();
            switch (entry.modifier().operation()) {
                case ADD_VALUE -> total += value;
                case ADD_MULTIPLIED_BASE -> total += BASE_ATTACK_SPEED * value;
                case ADD_MULTIPLIED_TOTAL -> total *= (1.0f + value);
            }
        }

        return total;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(targets.getFirst());
        return null;
    }
}
