package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
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
import org.tarclient.addon.utils.MiningUtils;
import org.tarclient.addon.utils.MioUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.tarclient.addon.utils.MiningUtils.*;

public class AntiSurround extends TarModule {
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

    private final Setting<Boolean> onlyInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only places if you are in a hole")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minHP = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health of this module")
        .defaultValue(10)
        .sliderRange(5, 30)
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

    private final Setting<Boolean> disableSpeedMine = sgDelay.add(new BoolSetting.Builder()
        .name("disable-speed-mine")
        .description("Disables speed-mine")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> speedMineDisableTicks = sgDelay.add(new IntSetting.Builder()
        .name("speed-mine-disable-ticks")
        .description("Helps with disabling")
        .defaultValue(5)
        .sliderRange(0, 10)
        .visible(disableSpeedMine::get)
        .build()
    );

    private final Setting<Boolean> disableAutoMine = sgDelay.add(new BoolSetting.Builder()
        .name("disable-auto-mine")
        .description("Disables auto-mine")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> autoMineDisableTicks = sgDelay.add(new IntSetting.Builder()
        .name("auto-mine-disable-ticks")
        .description("Helps with disabling")
        .defaultValue(5)
        .sliderRange(0, 10)
        .visible(disableSpeedMine::get)
        .build()
    );


    private final Setting<Double> placeConditionSafety = sgDelay.add(new DoubleSetting.Builder()
        .name("place-condition")
        .description("How much damage to block before place")
        .defaultValue(0.8)
        .sliderRange(0, 1)
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

    private BlockPos toClick = null;

    public AntiSurround() {
        super(TarAddon.CATEGORY, "anti-surround", "Replaces surround blocks with blocks that are easier to break");
    }

    @Override
    public void onActivate() {
        renderQueue.clear();
        globalCooldown = 0;
        enableAutoMine = 0;
        enableSpeedMine = 0;

        toClick = null;
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
        if (mc.world == null) return;
        if (!(event.packet instanceof PlayerActionC2SPacket packet)) return;
        if (!MinecraftClient.getInstance().isOnThread()) return;
        if (packet.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return;

        if (isUnsafe()) return;
        if (globalCooldown > 0) return;

        // magic happens here
        // everything happens after the stop_destroy has been sent, so we can instantly
        // break crystal and send a new block place without ping contesting as we
        // know the exact timing

        // NOTE: this runs before MiningUtils gets its own information
        // so we can use old info from it!
        BlockPos broken = packet.getPos();

        if (!broken.equals(getBreakingBlockPos())) return;

        // is an actual surround pos that should activate this?
        if (isValidSurroundPos(broken) == null) return;

        FindItemResult placeable = findPlaceable();
        if (!placeable.found()) return;
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

        // swap this out
        BlockHitResult blockHitResult = new BlockHitResult(broken.toBottomCenterPos(), Direction.UP, broken.down(), false);

        double yaw = Rotations.getYaw(blockHitResult.getPos());
        double pitch = Rotations.getPitch(blockHitResult.getPos());

        sendRotatePacket(yaw, pitch, RotationPacket.Full);

        InvUtils.swap(placeable.slot(), true);
        BlockUtils.interact(blockHitResult, placeable.getHand(), true);
        InvUtils.swapBack();

        renderQueue.put(broken, fadeTime.get());

        globalCooldown = cooldown.get();

        if (disableAutoMine.get()) {
            MioUtils.toggleAutoMine(false);
            enableAutoMine = autoMineDisableTicks.get();
        }

        if (disableSpeedMine.get()) {
            toClick = broken;
            MioUtils.toggleSpeedMine(false);
            enableSpeedMine = speedMineDisableTicks.get();
        }
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

        if (disableSpeedMine.get()) {
            if (enableSpeedMine > 0) {
                enableSpeedMine--;
                if (enableSpeedMine == 0) {
                    MioUtils.toggleSpeedMine(true);
                    if (toClick != null) {
                        MiningUtils.attackWithCompatibility(toClick, Direction.UP);
                    }
                }
            }
        }

        if (globalCooldown > 0) {
            globalCooldown--;
        }

        if (isUnsafe()) return;

        if (getBreakingProgress() < placeConditionSafety.get()) return;

        BlockPos breaking = getBreakingBlockPos();
        if (breaking == null || mc.world.getBlockState(breaking).isReplaceable()) return;

        Direction outWards = isValidSurroundPos(breaking);
        if (outWards == null) return;

        tryPlaceCrystalOutside(breaking, outWards);
    }

    private boolean isUnsafe() {
        if (mc.player == null) return true;
        if (mc.player.getHealth() < minHP.get()) return true;
        return onlyInHole.get() && !PlayerUtils.isInHole(true);
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
        return isValidCrystalPos(base) && mc.world.getOtherEntities(null, crystalBox, e -> e instanceof EndCrystalEntity).isEmpty();
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

    // returns null when not valid
    private Direction isValidSurroundPos(BlockPos pos) {
        if (mc.world == null || mc.player == null) return null;

        if (mc.player.squaredDistanceTo(pos.toCenterPos()) > blockRange.get() * blockRange.get()) return null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (TargetUtils.isBadTarget(player, targetRange.get()) || !Friends.get().shouldAttack(player)) continue;
            Direction dir = getSurroundDirection(pos, player.getBlockPos());
            if (dir != null && isValidSurroundPos(pos, dir)) {
                if (player == mc.player) {
                    /* well this is one of the surround blocks of the current player,
                    and we don't want to anti surround ourselves or someone in our
                    hole...
                    */
                    return null;
                }
                return dir;
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

    private boolean isValidSurroundPos(BlockPos pos, Direction outwardDir) {
        if (mc.world == null || mc.player == null) return false;
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

    private void attack(Entity entity) {
        if (mc.interactionManager == null || mc.player == null || entity.isRemoved() || !entity.isAlive()) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        entity.setRemoved(Entity.RemovalReason.KILLED);
    }
}
