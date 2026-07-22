package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.MioPauseSpeedmineEvent;
import org.tarclient.addon.utils.ItemExplosionCalculator;
import org.tarclient.addon.utils.MioUtils;

import java.util.*;

import static org.tarclient.addon.utils.MiningUtils.*;

public class AntiSurround extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDestroyItem = this.settings.createGroup("Destroy Item");
    private final SettingGroup sgReplace = this.settings.createGroup("Replace");
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> blockRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("block-range")
        .description("Maximum distance of block place")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    /* --- Destroy Item --- */
    private final Setting<Boolean> placeDestroyingCrystal = sgDestroyItem.add(new BoolSetting.Builder()
        .name("place-destroying-crystal")
        .description("Should we attempt to place a crystal in order to damage the item dropped by surrounding")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeDestroyingCrystalCondition = sgDestroyItem.add(new DoubleSetting.Builder()
        .name("place-destroying-crystal-condition")
        .description("How much damage to block before placing crystal")
        .defaultValue(0.95)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> quickCrystal = sgDestroyItem.add(new BoolSetting.Builder()
        .name("quick-crystal")
        .description("Destroy crystal to destroy item -> start crystalling opponent")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> quickCrystalMinHp = sgDestroyItem.add(new DoubleSetting.Builder()
        .name("quick-crystal-min-health")
        .description("Minimum health of this module to quick crystal")
        .defaultValue(10)
        .sliderRange(5, 30)
        .visible(quickCrystal::get)
        .build()
    );

    private final Setting<Double> crystalMaxSelfDamage = sgDestroyItem.add(new DoubleSetting.Builder()
        .name("quick-crystal-max-self-damage")
        .description("Max self damage for destroying a crystal")
        .defaultValue(3)
        .sliderRange(1, 20)
        .visible(() -> placeDestroyingCrystal.get() || quickCrystal.get())
        .build()
    );

    /* --- Replace --- */
    private final Setting<Boolean> replace = sgReplace.add(new BoolSetting.Builder()
        .name("replace")
        .description("Should we replace the block with a faster breakable block?")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> replacePlaceCondition = sgReplace.add(new DoubleSetting.Builder()
        .name("replace-place-condition")
        .description("How much damage to block before placing crystal")
        .defaultValue(0.8)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<List<Block>> replaceBlocks = sgReplace.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Primary blocks to use")
        .defaultValue(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)
        .visible(replace::get)
        .build()
    );

    private final Setting<Boolean> replaceOnlyInHole = sgReplace.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only replaces if you are in a hole")
        .defaultValue(true)
        .visible(replace::get)
        .build()
    );

    private final Setting<Double> replaceMinHP = sgReplace.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health of this module")
        .defaultValue(10)
        .sliderRange(5, 30)
        .visible(replace::get)
        .build()
    );

   /* private final Setting<Boolean> disableSpeedMine = sgReplace.add(new BoolSetting.Builder()
        .name("disable-speed-mine")
        .description("Disables speed-mine")
        .defaultValue(true)
        .visible(replace::get)
        .build()
    );

    */

    private final Setting<Integer> speedMineDisableTicks = sgReplace.add(new IntSetting.Builder()
        .name("speed-mine-disable-ticks")
        .description("Helps with disabling")
        .defaultValue(5)
        .sliderRange(0, 10)
        .visible(replace::get)
        .build()
    );

    private final Setting<Boolean> disableAutoMine = sgReplace.add(new BoolSetting.Builder()
        .name("disable-auto-mine")
        .description("Disables auto-mine")
        .defaultValue(true)
        .visible(replace::get)
        .build()
    );

    private final Setting<Integer> autoMineDisableTicks = sgReplace.add(new IntSetting.Builder()
        .name("auto-mine-disable-ticks")
        .description("Helps with disabling")
        .defaultValue(5)
        .sliderRange(0, 10)
        .visible(() -> disableAutoMine.get() && replace.get())
        .build()
    );

    /* --- Delay --- */
    private final Setting<Integer> cooldown = sgDelay.add(new IntSetting.Builder()
        .name("cooldown")
        .description("The global cooldown")
        .defaultValue(3)
        .sliderRange(0, 10)
        .build()
    );

    /* --- Render --- */
    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How many seconds should rendering take?")
        .defaultValue(0.5)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );

    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    private int globalCooldown = 0;
    private int enableAutoMine = 0;
    private int enableSpeedMine = 0;

    public AntiSurround() {
        super(TarAddon.CATEGORY, "anti-surround", "Tries to exploit mechanics in order to deal more damage to people surrounding");
    }

    @Override
    public void onActivate() {
        renderQueue.clear();
        globalCooldown = 0;
        enableAutoMine = 0;
        enableSpeedMine = 0;
    }


    @EventHandler
    private void onMioSpeedmine(MioPauseSpeedmineEvent event) {
        if (enableSpeedMine > 0) {
            event.cancel();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Iterator<Map.Entry<BlockPos, Double>> it = renderQueue.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, Double> entry = it.next();
            double remaining = entry.getValue();

            if (remaining <= 0) {
                it.remove();
                continue;
            }

            double alphaMultip = Math.clamp(remaining / fadeTime.get(), 0, 1);

            // uhh multiply alpha ig?
            Color side = sideColor.get().copy().a((int) (sideColor.get().a * alphaMultip));
            Color line = lineColor.get().copy().a((int) (lineColor.get().a * alphaMultip));

            event.renderer.box(entry.getKey(), side, line, shapeMode.get(), 0);

            entry.setValue(remaining - (float) event.frameTime);
        }
    }

    @EventHandler
    private void onPacketSent(PacketEvent.Sent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!(event.packet instanceof PlayerActionC2SPacket packet)) return;
        if (!MinecraftClient.getInstance().isOnThread()) return;
        if (packet.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return;

        if (globalCooldown > 0) return;

        // magic happens here
        // minecraft packets are handled sequentially,
        // everything happens after the stop_destroy has been sent so we can
        // time everything instantly

        // NOTE: this event runs before MiningUtils gets its own information
        // so we can use old info from it!
        BlockPos broken = packet.getPos();

        if (!broken.equals(getBreakingBlockPos())) return;

        // is an actual surround pos that should activate this?
        Target target = isValidSurroundPos(broken);
        if (target == null) return;

        Direction out = target.direction;

        if (replace.get() && isSafeToReplace() && isValidReplaceSurroundPos(broken, out)) {
            if (replacedSurround(broken)) {
                renderQueue.put(broken, fadeTime.get());

                globalCooldown = cooldown.get();

                if (disableAutoMine.get()) {
                    MioUtils.toggleAutoMine(false);
                    enableAutoMine = autoMineDisableTicks.get();
                }

                enableSpeedMine = speedMineDisableTicks.get();

                return;
            }
        }

        if (quickCrystal.get() && isSafeToQuickCrystal() && hasCrystalPlatformBelow(broken)) {
            // we should spoof block state for all damage utils, spoof to air -> back
            BlockState oldState = mc.world.getBlockState(broken);
            mc.world.setBlockState(broken, Blocks.AIR.getDefaultState());

            if (destroyedCrystal(broken)) {
                globalCooldown = cooldown.get();

                BlockPos crystalBase = packet.getPos().down();

                if (mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
                    Vec3d hitPos = crystalBase.toCenterPos().add(0, 0.5, 0);

                    float yaw = (float) Rotations.getYaw(hitPos);
                    float pitch = (float) Rotations.getPitch(hitPos);
                    sendRotatePacket(yaw, pitch, RotationPacket.Full);

                    BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, crystalBase, false);
                    if (mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, bhr) == ActionResult.SUCCESS) {
                        mc.player.swingHand(Hand.OFF_HAND);
                    }
                }
            }

            mc.world.setBlockState(broken, oldState);
        }
    }

    private boolean isSafeToReplace() {
        if (mc.player == null) return false;
        if (mc.player.getHealth() < replaceMinHP.get()) return false;
        return !replaceOnlyInHole.get() || PlayerUtils.isInHole(true);
    }

    private boolean isSafeToQuickCrystal() {
        if (mc.player == null) return false;
        return !(mc.player.getHealth() < quickCrystalMinHp.get());
    }

    private boolean destroyedCrystal(BlockPos broken) {
        if (mc.world == null || mc.player == null) return false;

        EndCrystalEntity entity = getClosestDestroyingCrystal(broken);
        if (entity == null) return false;

        double yaw = Rotations.getYaw(entity);
        double pitch = Rotations.getPitch(entity);
        sendRotatePacket(yaw, pitch, RotationPacket.Full);
        attack(entity);

        return true;
    }

    private EndCrystalEntity getClosestDestroyingCrystal(BlockPos broken) {
        if (mc.player == null || mc.world == null) return null;

        List<EndCrystalEntity> destroyingCrystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(broken).expand(8), (endCrystalEntity -> {
            if (mc.player.getEyePos().squaredDistanceTo(endCrystalEntity.getEntityPos()) > blockRange.get() * blockRange.get()) return false;
            if (!ItemExplosionCalculator.willCrystalDestroyItem(endCrystalEntity.getEntityPos(), broken.toCenterPos())) return false;
            return DamageUtils.crystalDamage(mc.player, endCrystalEntity.getEntityPos()) < crystalMaxSelfDamage.get();
        }));

        if (destroyingCrystals.isEmpty()) return null;

        destroyingCrystals.sort(((o1, o2) ->
            Double.compare(DamageUtils.crystalDamage(mc.player, o1.getEntityPos()), DamageUtils.crystalDamage(mc.player, o2.getEntityPos()))
        ));

        return destroyingCrystals.getFirst();
    }

    private boolean replacedSurround(BlockPos broken) {
        if (mc.world == null) return false;

        FindItemResult placeable = findPlaceable();
        if (!placeable.found()) return false;
        // break (already done) -> attack -> place again

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (Box.from(new BlockBox(broken)).intersects(entity.getBoundingBox())) {
                double yaw = Rotations.getYaw(entity);
                double pitch = Rotations.getPitch(entity);
                sendRotatePacket(yaw, pitch, RotationPacket.Full);
                attack(entity);
            }
        }

        // swap this out if needed?
        BlockHitResult blockHitResult = new BlockHitResult(broken.toBottomCenterPos(), Direction.UP, broken.down(), false);

        double yaw = Rotations.getYaw(blockHitResult.getPos());
        double pitch = Rotations.getPitch(blockHitResult.getPos());

        sendRotatePacket(yaw, pitch, RotationPacket.Full);

        InvUtils.swap(placeable.slot(), true);
        BlockUtils.interact(blockHitResult, placeable.getHand(), true);
        InvUtils.swapBack();

        return true;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null) return;

        if (disableAutoMine.get()) {
            if (enableAutoMine > 0) {
                enableAutoMine--;
                if (enableAutoMine == 0) {
                    MioUtils.toggleAutoMine(true);
                }
            }
        }
        if (enableSpeedMine > 0) {
            enableSpeedMine--;
        }

        if (globalCooldown > 0) {
            globalCooldown--;
        }

        BlockPos breaking = getBreakingBlockPos();
        if (breaking == null || mc.world.getBlockState(breaking).isReplaceable()) return;

        Target target = isValidSurroundPos(breaking);
        if (target == null) return;

        Direction outWards = target.direction;

        if (replace.get() && isValidReplaceSurroundPos(breaking, outWards)) {
            if (getBreakingProgress() < replacePlaceCondition.get()) return;
            tryPlaceCrystalOutside(breaking, outWards);
        } else if (placeDestroyingCrystal.get() && isSafeToPlaceDestroyingCrystal() && hasCrystalPlatformBelow(breaking)) {
            if (getBreakingProgress() < placeDestroyingCrystalCondition.get()) return;
            // fix calculation with replacing blockstate
            BlockState oldState = mc.world.getBlockState(breaking);
            mc.world.setBlockState(breaking, Blocks.AIR.getDefaultState());

            EndCrystalEntity entity = getClosestDestroyingCrystal(breaking);

            if (entity == null) {
                // place as there wasn't any

                BlockPos crystalBase = findCrystalPosition(breaking, target.player);

                if (crystalBase != null) {
                    placeCrystalOnBase(crystalBase);
                }
            }

            mc.world.setBlockState(breaking, oldState);
        }
    }

    private BlockPos findCrystalPosition(BlockPos centerPos, PlayerEntity target) {
        if (mc.player == null) return null;

        int radius = 6;
        Vec3d itemPos = centerPos.toCenterPos();

        BlockPos bestPos = null;
        double bestDamage = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    BlockPos basePos = centerPos.add(dx, dy, dz);
                    if (basePos.equals(centerPos.down())) continue;

                    if (mc.player.getEyePos().squaredDistanceTo(basePos.toCenterPos()) > blockRange.get() * blockRange.get()) continue;

                    if (!isValidCrystalPos(basePos) || !canCrystal(basePos)) continue;

                    Vec3d crystalPos = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1, basePos.getZ() + 0.5);

                    double dist = crystalPos.distanceTo(itemPos);
                    if (dist > 2 * 6) continue;

                    if (DamageUtils.crystalDamage(mc.player, crystalPos) > crystalMaxSelfDamage.get()) continue;

                    if (ItemExplosionCalculator.willCrystalDestroyItem(crystalPos, itemPos)) {
                        double damage = DamageUtils.crystalDamage(target, crystalPos);

                        if (damage > bestDamage) {
                            bestPos = basePos;
                            bestDamage = damage;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean isSafeToPlaceDestroyingCrystal() {
        if (mc.player == null) return false;
        return !(mc.player.getHealth() < quickCrystalMinHp.get());
    }

    private void tryPlaceCrystalOutside(BlockPos surroundPos, Direction outwardDir) {
        if (mc.player == null || mc.interactionManager == null) return;

        BlockPos crystalBase = surroundPos.offset(outwardDir).down();
        placeCrystalOnBase(crystalBase);
    }

    private void placeCrystalOnBase(BlockPos crystalBase) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.squaredDistanceTo(crystalBase.toCenterPos()) > blockRange.get() * blockRange.get()) return;

        if (canCrystal(crystalBase)) {
            if (mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) return;

            Vec3d hitPos = crystalBase.toCenterPos().add(0, 0.5, 0);

            float yaw = (float) Rotations.getYaw(hitPos);
            float pitch = (float) Rotations.getPitch(hitPos);
            sendRotatePacket(yaw, pitch, RotationPacket.Full);

            BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, crystalBase, false);
            if (mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, bhr) == ActionResult.SUCCESS) {
                mc.player.swingHand(Hand.OFF_HAND);
            }
        }
    }

    private boolean canCrystal(BlockPos base) {
        if (mc.world == null) return false;

        Box crystalBox = new Box(base.up());
        return isValidCrystalPos(base) && mc.world.getOtherEntities(null, crystalBox, e -> !e.isSpectator()).isEmpty();
    }

    public FindItemResult findPlaceable() {
        return InvUtils.findInHotbar(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem) {
                Block itemBlock = ((BlockItem) itemStack.getItem()).getBlock();
                return replaceBlocks.get().contains(itemBlock);
            }
            return false;
        });
    }

    // returns null when not valid
    private Target isValidSurroundPos(BlockPos pos) {
        if (mc.world == null || mc.player == null) return null;

        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > targetRange.get() * targetRange.get()) return null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (TargetUtils.isBadTarget(player, targetRange.get()) || !Friends.get().shouldAttack(player)) continue;
            Direction dir = getSurroundDirection(pos, player.getBlockPos());
            if (dir != null && mc.world.getBlockState(pos).getBlock() != Blocks.BEDROCK) {
                if (player == mc.player) {
                    /* well this is one of the surround blocks of the current player,
                    and we don't want to anti surround ourselves or someone in our
                    hole...
                    */
                    return null;
                }
                return new Target(dir, player);
            }
        }
        return null;
    }

    private Direction getSurroundDirection(BlockPos surroundPos, BlockPos feet) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (feet.offset(dir).equals(surroundPos)) return dir;
        }
        return null;
    }

    private boolean isValidReplaceSurroundPos(BlockPos pos, Direction outwardDir) {
        if (mc.world == null || mc.player == null) return false;
        if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) return false;

        BlockPos crystalBase = pos.offset(outwardDir).down();
        return isValidCrystalPos(crystalBase);
    }

    private boolean hasCrystalPlatformBelow(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;
        BlockState state = mc.world.getBlockState(pos.down());

        return state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.BEDROCK;
    }

    private boolean isValidCrystalPos(BlockPos base) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(base);
        if (state.getBlock() != Blocks.OBSIDIAN && state.getBlock() != Blocks.BEDROCK) return false;
        return mc.world.getBlockState(base.up()).isAir();
    }

    private void attack(Entity entity) {
        if (mc.interactionManager == null || mc.player == null || entity.isRemoved() || !entity.isAlive()) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        entity.setRemoved(Entity.RemovalReason.KILLED);
    }

    public record Target(Direction direction, PlayerEntity player) {}
}
