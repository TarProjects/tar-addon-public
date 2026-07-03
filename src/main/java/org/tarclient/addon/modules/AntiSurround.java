package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.ClickBlockEvent;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.List;

import static org.tarclient.addon.utils.MiningUtils.*;
import static org.tarclient.addon.utils.MioUtils.toggleAutoMine;
import static org.tarclient.addon.utils.MioUtils.toggleModule;

public class AntiSurround extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");

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

    private final Setting<Double> blockRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("block-range")
        .description("Maximum distance of block place")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Primary blocks to use")
        .defaultValue(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)
        .build()
    );

    private final Setting<Double> minHP = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health of this module")
        .defaultValue(10)
        .sliderRange(5, 30)
        .build()
    );

    private final Setting<Boolean> setDead = sgGeneral.add(new BoolSetting.Builder()
        .name("set-dead")
        .description("Sets the obsidian to air after break packet, inconsistent!")
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

    private final Setting<Integer> failTicks = sgDelay.add(new IntSetting.Builder()
        .name("fail-ticks")
        .description("How many ticks before failing if cant place")
        .defaultValue(10)
        .sliderRange(0, 40)
        .build()
    );

    private int globalCooldown = 0;
    private int cantPlaceTicks = 0;
    private PlayerEntity target;

    private int lastBlockY = 0;

    public AntiSurround() {
        super(TarAddon.CATEGORY, "anti-surround", "Replaces surround blocks with blocks that are easier to break");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

        reset(false);

        toggleModule("CrystalAura", false);
        toggleAutoMine(false);

        lastBlockY = mc.player.getBlockY();
    }

    @Override
    public void onDeactivate() {
        toggleModule("CrystalAura", true);
        toggleAutoMine(true);
    }

    public void reset(boolean cooldown) {
        target = null;
        globalCooldown = cooldown ? this.cooldown.get() : 0;
        cantPlaceTicks = 0;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (setDead.get()) {
            if (event.packet instanceof PlayerActionC2SPacket packet) {
                if (packet.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return;
                if (!packet.getPos().equals(getBreakingBlockPos())) return;
                if (getBreakingProgress() < 0.999) return;

                mc.execute(() -> {
                    if (mc.world == null) return;
                    info("setdead at " + packet.getPos());
                    mc.world.setBlockState(packet.getPos(), Blocks.AIR.getDefaultState());
                });
            }
        }
    }


    @EventHandler
    private void onClickBlock(ClickBlockEvent event) {
        reset(true);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null) return;

        FindItemResult placeable = findPlaceable();
        if (!placeable.found()) {
            error("No valid blocks found!");
            this.toggle();
            return;
        }

        if (mc.player.getBlockY() > lastBlockY) {
            info("Moved vertically, disabling");
            this.toggle();
            return;
        }

        lastBlockY = mc.player.getBlockY();

        if (globalCooldown > 0) {
            globalCooldown--;
        }

        if (TargetUtils.isBadTarget(target, targetRange.get()) || findSurroundPos(target) == null) {
            target = getTarget(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) {
                reset(false);
                info("bad target");
                return;
            }
        }

        BlockPos breaking = getBreakingBlockPos();
        if (breaking == null || !isValidSurroundPos(breaking, target)) {
            breaking = findSurroundPos(target);
            if (breaking != null) {
                attackWithCompatibility(breaking, Direction.UP);
            }
            return;
        }

        if (mc.world.getBlockState(breaking).isReplaceable()) {
            if (globalCooldown > 0) {
                return;
            }

            if (mc.player.getHealth() < minHP.get()) {
                info("health");
                return;
            }

            handleAnvilPlace(placeable, breaking);
        } else {
            handleCrystalPlace(breaking);
        }

    }

    public void handleAnvilPlace(FindItemResult placeable, BlockPos toPlace) {
        if (mc.world == null || mc.player == null) return;

        if (!placeable.found()) {
            error("No Anvils!");
            this.toggle();
            return;
        }

        info("checking anvil place");

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (Box.from(new BlockBox(toPlace)).intersects(entity.getBoundingBox())) {
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), () -> attack(entity));
                info("Attacked crystal");
            }
        }

        TarBlockUtils.InteractRunnable callback = (bhr -> {
            Rotations.rotate(Rotations.getYaw(bhr.getPos()), Rotations.getPitch(bhr.getPos()), () -> {
                InvUtils.swap(placeable.slot(), true);
                BlockUtils.interact(bhr, Hand.MAIN_HAND, true);
                InvUtils.swapBack();
                info("Placed AntiSurround Anvil");
            });
        });

        TarBlockUtils.place(toPlace, false, true, Blocks.ANVIL, callback);
    }

    private void handleCrystalPlace(BlockPos breaking) {
        Direction dir = getSurroundDirection(breaking, target.getBlockPos());
        if (dir == null) return;

        tryPlaceCrystalOutside(breaking, dir);
    }

    private void tryPlaceCrystalOutside(BlockPos surroundPos, Direction outwardDir) {
        if (mc.player == null || mc.interactionManager == null) return;

        BlockPos crystalBase = surroundPos.offset(outwardDir).down();
        if (mc.player.squaredDistanceTo(crystalBase.toCenterPos()) > blockRange.get() * blockRange.get()) return;

        if (canCrystal(crystalBase)) {
            if (mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) return;

            Vec3d hitPos = crystalBase.toCenterPos().add(0, 0.5, 0);

            float yaw = (float) Rotations.getYaw(hitPos);
            float pitch = (float) Rotations.getPitch(hitPos);
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

            BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, crystalBase, false);
            if (mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, bhr) == ActionResult.SUCCESS) {
                mc.player.swingHand(Hand.OFF_HAND);
            }
        }
    }

    public FindItemResult findPlaceable() {
        return InvUtils.findInHotbar(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem) {
                Block itemBlock = ((BlockItem) itemStack.getItem()).getBlock();
                return blocks.get().contains(itemBlock);
            }
            return false;
        });
    }

    private boolean isValidSurroundPos(BlockPos pos, PlayerEntity target) {
        if (target == null) return false;
        Direction dir = getSurroundDirection(pos, target.getBlockPos());
        return dir != null && isValidSurroundPos(pos, dir);
    }

    private Direction getSurroundDirection(BlockPos surroundPos, BlockPos feet) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (feet.offset(dir).equals(surroundPos)) return dir;
        }
        return null;
    }

    private BlockPos findSurroundPos(PlayerEntity target) {
        if (target == null || mc.player == null || mc.world == null) return null;
        BlockPos feet = target.getBlockPos();

        int surrBlocks = 0;

        BlockPos validSurroundPos = null;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos pos = feet.offset(dir);

            if (mc.world.getBlockState(pos).getBlock().getBlastResistance() >= 600) surrBlocks++;
            // find first horizontal
            if (validSurroundPos != null) continue;
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > blockRange.get() * blockRange.get()) continue;
            if (isValidSurroundPos(pos, dir)) validSurroundPos = pos;
        }

        return surrBlocks >= 3 ? validSurroundPos : null;
    }

    private boolean isValidSurroundPos(BlockPos pos, Direction outwardDir) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);

        if (state.getBlock() == Blocks.BEDROCK) return false;
        if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) return false;

        BlockPos crystalBase = pos.offset(outwardDir).down();
        return isValidCrystalPos(crystalBase);
    }

    private boolean isValidCrystalPos(BlockPos base) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(base);
        if (state.getBlock() != Blocks.OBSIDIAN && state.getBlock() != Blocks.BEDROCK) return false;
        return mc.world.getBlockState(base.up()).isAir();
    }

    private boolean canCrystal(BlockPos base) {
        if (mc.world == null) return false;

        Box crystalBox = new Box(base.up());
        return isValidCrystalPos(base) && mc.world.getOtherEntities(null, crystalBox, e -> e instanceof EndCrystalEntity).isEmpty();
    }

    private PlayerEntity getTarget(double range, SortPriority priority) {
        if (!Utils.canUpdate()) return null;
        return (PlayerEntity) TargetUtils.get(entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isDead() || player.getHealth() <= 0) return false;
            if (!PlayerUtils.isWithin(entity, range)) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer) {
                if (fakePlayer.noHit) return false;
            } else if (EntityUtils.getGameMode(player) != GameMode.SURVIVAL) return false;

            // Strict check: Only target players who actually have a valid anti-surround spot!
            info("target checks out otherwise");
            return findSurroundPos(player) != null;
        }, priority);
    }

    private void attack(Entity entity) {
        if (mc.interactionManager == null || mc.player == null || entity.isRemoved() || !entity.isAlive()) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        entity.setRemoved(Entity.RemovalReason.KILLED);
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }
}
