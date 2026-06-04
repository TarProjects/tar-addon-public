package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.ClickBlockEvent;
import org.tarclient.addon.utils.HoleUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import static org.tarclient.addon.utils.MiningUtils.*;
import static org.tarclient.addon.utils.MioUtils.toggleAutoMine;

public class AutoCEV extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> minHP = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health of this module")
        .defaultValue(10)
        .sliderRange(5, 30)
        .build()
    );

    private final Setting<Boolean> waitForCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-for-crystal")
        .description("Waits for offhand crystal")
        .defaultValue(true)
        .build()
    );

    /* --- Delay --- */
    private final Setting<Integer> cooldown = sgDelay.add(new IntSetting.Builder()
        .name("cooldown")
        .description("The global cooldown")
        .defaultValue(10)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> resetCooldown = sgDelay.add(new IntSetting.Builder()
        .name("reset-cooldown")
        .description("The global cooldown on reset")
        .defaultValue(2)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> placeCondition = sgDelay.add(new DoubleSetting.Builder()
        .name("place-condition")
        .description("How much damage to block before place")
        .defaultValue(1)
        .sliderRange(0, 1)
        .build()
    );

    private int globalCooldown = 0;
    private PlayerEntity target;
    private int lastBlockY = 0;

    public AutoCEV() {
        super(TarAddon.CATEGORY, "auto-cev", "Cevs opponents");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }
        reset();


        toggleAutoMine(false);
        lastBlockY = mc.player.getBlockY();
    }

    @Override
    public void onDeactivate() {
        reset();
        toggleAutoMine(true);
    }

    private void reset() {
        target = null;
        globalCooldown = resetCooldown.get();
    }

    @EventHandler
    private void onClickBlock(ClickBlockEvent event) {
        reset();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        if (mc.player == null) return;

        FindItemResult obby = InvUtils.find(Items.OBSIDIAN);
        if (!obby.found()) {
            error("No obsidian!");
            this.toggle();
            return;
        }

        if (mc.player.getBlockY() > lastBlockY) {
            info("Moved vertically, disabling");
            this.toggle();
            return;
        }

        lastBlockY = mc.player.getBlockY();

        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = getTargetInHole(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) {
                reset();
                return;
            }
        }

        if (!HoleUtils.isInHole(target.getBlockPos(), true)) {
            reset();
            return;
        }

        BlockPos breaking = getBreakingBlockPos();
        if (breaking == null || !isValidPlacePosition(breaking) || !isTargetCevPosition(breaking, target)) {
            breaking = findCevPos(target);
            if (breaking != null) {
                attackWithCompatibility(breaking, Direction.UP);
            }
            return;
        }

        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }


        boolean intersects = false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (Box.from(new BlockBox(breaking)).intersects(entity.getBoundingBox()) ||
                Box.from(new BlockBox(breaking.up())).intersects(entity.getBoundingBox())) {

                intersects = true;
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), () -> attack(entity));
            }
        }

        if (intersects) {
            reset();
            return;
        }

        double lastBreakingProgress = getBreakingProgress(Blocks.OBSIDIAN.getDefaultState());
        if (lastBreakingProgress < placeCondition.get()) {
            return;
        }

        if (mc.player.getHealth() < minHP.get()) {
            return;
        }

        if (mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL && waitForCrystal.get()) {
            return;
        }

        TarBlockUtils.InteractRunnable callback = (bhr -> {
            float yaw = (float) Rotations.getYaw(bhr.getPos());
            float pitch = (float) Rotations.getPitch(bhr.getPos());
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

            swap(obby.slot());
            BlockUtils.interact(bhr, Hand.MAIN_HAND, true);
            swap(obby.slot());
        });


        if (!TarBlockUtils.place(breaking, false, true, Blocks.OBSIDIAN, callback)) {
            reset();
            return;
        }

        globalCooldown = cooldown.get();
    }

    private BlockPos findCevPos(PlayerEntity target) {
        if (mc.player == null) return null;

        BlockPos center = target.getBlockPos().up();

        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            BlockPos pos = center.offset(direction);
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) continue;
            if (!isValidPlacePosition(pos)) continue;
            return pos;
        }
        return null;
    }

    private boolean isValidPlacePosition(BlockPos pos) {
        if (mc.player == null) return false;

        Direction placeSide = BlockUtils.getClosestPlaceSide(pos);
        if (placeSide == null) return false;

        if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return false;
        return isValidCevPosition(pos);
    }

    private boolean isValidCevPosition(BlockPos pos) {
        if (mc.world == null) return false;

        BlockPos up = pos.up();
        boolean isBlockStateValid = mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN;
        return !collidesWithPlayer(pos) && isBlockStateValid && mc.world.getBlockState(up).isAir();
    }

    private boolean isTargetCevPosition(BlockPos pos, Entity target) {
        BlockPos center = target.getBlockPos().up();

        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;

            if (pos.equals(center.offset(direction))) return true;
        }

        return pos.equals(center.up(2));
    }

    private PlayerEntity getTargetInHole(double range, SortPriority priority) {
        if (!Utils.canUpdate()) return null;
        return (PlayerEntity) TargetUtils.get(entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isDead() || player.getHealth() <= 0) return false;
            if (!PlayerUtils.isWithin(entity, range)) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer) return !fakePlayer.noHit;
            if (!HoleUtils.isInHole(player.getBlockPos(), true)) return false;
            return EntityUtils.getGameMode(player) == GameMode.SURVIVAL;
        }, priority);
    }

    private boolean collidesWithPlayer(BlockPos pos) {
        if (mc.world == null) return false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity)) continue;
            if (Box.from(new BlockBox(pos)).intersects(entity.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }

    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.player == null) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
