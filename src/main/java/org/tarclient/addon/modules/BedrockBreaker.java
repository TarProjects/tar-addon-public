package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.ClickBlockEvent;
import org.tarclient.addon.utils.MiningUtils;
import org.tarclient.addon.utils.MioUtils;

import java.util.List;

public class BedrockBreaker extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<List<Block>> updateBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("update-blocks")
        .description("Primary blocks to use for updating")
        .defaultValue(Blocks.GLASS)
        .build()
    );

    private int ticks;
    private Stage stage;
    private boolean disabledAutomine;

    private BlockPos pistonPos;
    private BlockPos budPos;
    private BlockPos updatePos;

    private Direction pistonRotation;
    private Direction clickedFace;

    private boolean packetGuard;

    public BedrockBreaker() {
        super(TarAddon.CATEGORY, "bedrock-breaker", "Breaks bedrock");
    }

    private void reset() {
        if (disabledAutomine) {
            MioUtils.toggleAutoMine(true);
        }
        stage = Stage.None;
        ticks = 0;
        disabledAutomine = false;

        pistonPos = null;
        budPos = null;
        updatePos = null;
        clickedFace = null;
        packetGuard = false;
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

        reset();
    }

    @Override
    public void onDeactivate() {
        if (disabledAutomine) {
            MioUtils.toggleAutoMine(true);
        }
    }

    @EventHandler
    private void onClickBlock(ClickBlockEvent event) {
        if (mc.world == null) return;
        if (stage != Stage.None) {
            event.cancel();
            return;
        }

        BlockState state = mc.world.getBlockState(event.blockPos);
        if (state.getBlock() == Blocks.BEDROCK) {
            reset();
            stage = Stage.BuddedPiston;
            ticks = 0;

            pistonPos = event.blockPos.offset(event.direction);
            clickedFace = event.direction;

            pistonRotation = findPistonRotation(pistonPos);
            if (pistonRotation == null) {
                reset();
                return;
            }

            budPos = findBudPos(pistonPos, pistonRotation);
            updatePos = findUpdatePos(pistonPos);
            // find positions and check for nullability
            if (budPos == null || updatePos == null) {
                reset();
                return;
            }

            MioUtils.toggleAutoMine(false);
            disabledAutomine = true;
            event.cancel();
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        FindItemResult piston = InvUtils.findInHotbar((stack -> stack.isOf(Items.PISTON) || stack.isOf(Items.STICKY_PISTON)));
        if (!piston.found()) {
            return;
        }

        FindItemResult redstoneBlock = InvUtils.findInHotbar(Items.REDSTONE_BLOCK);
        if (!redstoneBlock.found()) {
            return;
        }

        FindItemResult updateBlock = findUpdateBlock();
        if (!updateBlock.found()) {
            return;
        }

        switch (stage) {
            case BuddedPiston -> {
                if (!BlockUtils.canPlace(budPos)) {
                    reset();
                    return;
                }

                if (!BlockUtils.canPlace(pistonPos)) {
                    reset();
                    return;
                }

                BlockHitResult budBhr = airPlace(budPos);
                BlockHitResult pistonBhr = airPlace(pistonPos);

                InvUtils.swap(redstoneBlock.slot(), true);
                sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, budBhr, 0));
                sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                InvUtils.swapBack();

                double yaw = pistonRotation.getOpposite().getPositiveHorizontalDegrees();
                double pitch = pistonRotation.getOpposite().getVector().getY() * -90;

                // IMPORTANT: ROTATE IN THE DIRECTION
                sendRotatePacket(yaw, pitch, RotationPacket.Full);
                InvUtils.swap(piston.slot(), true);
                sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, pistonBhr, 0));
                sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                InvUtils.swapBack();

                stage = Stage.UnBud;
                ticks = 0;
            }

            case UnBud -> {
                if (ticks > 20) {
                    error("Piston didnt place!");
                    reset();
                    return;
                }

                BlockState pistonState = mc.world.getBlockState(pistonPos);
                if (pistonState.isReplaceable()) {
                    ticks++;
                    return;
                }

                MiningUtils.attackWithCompatibility(budPos, Direction.UP);
                stage = Stage.WaitForUnBud;
                ticks = 0;
            }

            case WaitForUnBud -> {
                if (ticks > 40) {
                    error("Bud breaking took too long, resetting!");
                    reset();
                    return;
                }

                BlockState pistonState = mc.world.getBlockState(pistonPos);
                if (pistonState.isReplaceable()) {
                    reset();
                    return;
                }

                BlockState budState = mc.world.getBlockState(budPos);
                if (budState.isReplaceable()) {
                    // broken
                    ticks = 0;
                    stage = Stage.UpdateAndReplace;
                    MiningUtils.attackWithCompatibility(pistonPos, Direction.UP);
                } else {
                    ticks++;
                }
            }

            case UpdateAndReplace -> {
                // Handled on packet sent!
                if (ticks > 40) {
                    error("Final breaking took too long, resetting!");
                    reset();
                    return;
                }

                ticks++;
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (packetGuard) return;
        if (mc.world == null) return;
        if (stage != Stage.UpdateAndReplace) return;
        if (event.packet instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return;
            if (packet.getPos() != pistonPos) return;

            FindItemResult updateBlock = findUpdateBlock();
            if (!updateBlock.found()) {
                return;
            }

            BlockHitResult updateBhr = airPlace(updatePos);


            altSwap(updateBlock.slot());
            event.sendSilently(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, updateBhr, 0));
            event.sendSilently(new HandSwingC2SPacket(Hand.MAIN_HAND));
            altSwap(updateBlock.slot());
        }
    }

    @EventHandler
    private void onSentPacket(PacketEvent.Sent event) {
        if (packetGuard) return;
        if (mc.world == null || mc.player == null) return;
        if (stage != Stage.UpdateAndReplace) return;
        if (event.packet instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return;
            if (packet.getPos() != pistonPos) return;

            FindItemResult piston = InvUtils.findInHotbar((stack -> stack.isOf(Items.PISTON) || stack.isOf(Items.STICKY_PISTON)));
            if (!piston.found()) {
                return;
            }

            BlockHitResult pistonBhr = airPlace(pistonPos);

            packetGuard = true;
            double yaw = clickedFace.getPositiveHorizontalDegrees();
            double pitch = clickedFace.getVector().getY() * -90;
            event.sendSilently(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), (float) yaw, (float) pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            InvUtils.swap(piston.slot(), true);
            event.sendSilently(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, pistonBhr, 0));
            event.sendSilently(new HandSwingC2SPacket(Hand.MAIN_HAND));
            InvUtils.swapBack();
            packetGuard = false;

            reset();
        }
    }


    private BlockPos findBudPos(BlockPos pistonPos, Direction pistonRotation) {
        if (mc.player == null) return null;
        if (mc.world == null) return null;

        BlockPos up = pistonPos.up();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            BlockPos pos = up.offset(direction);
            // adjacency to piston pos
            BlockPos maxPiston = pistonPos.offset(pistonRotation);
            if (touches(maxPiston, pos)) continue;

            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) continue;
            BlockState state = mc.world.getBlockState(pos);
            if (state.isReplaceable()) return pos;
        }
        return null;
    }

    private BlockPos findUpdatePos(BlockPos pistonPos) {
        if (mc.player == null) return null;
        if (mc.world == null) return null;

        for (Direction direction : Direction.values()) {
            if (direction == pistonRotation) continue;
            BlockPos pos = pistonPos.offset(direction);
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) continue;
            BlockState state = mc.world.getBlockState(pos);
            if (state.isReplaceable()) return pos;
        }
        return null;
    }

    private Direction findPistonRotation(BlockPos pos) {
        if (mc.world == null) return null;
        for (Direction direction : Direction.values()) {
            // cant bud an up piston
            if (direction == Direction.UP) continue;
            if (mc.world.getBlockState(pos.offset(direction)).isReplaceable()) return direction;
        }
        return null;
    }

    private boolean touches(BlockPos a, BlockPos b) {
        for (Direction direction : Direction.values()) {
            if (a.offset(direction).equals(b)) return true;
        }

        return false;
    }

    public FindItemResult findUpdateBlock() {
        return InvUtils.find(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem) {
                Block itemBlock = ((BlockItem) itemStack.getItem()).getBlock();
                return updateBlocks.get().contains(itemBlock);
            }
            return false;
        });
    }

    private BlockHitResult airPlace(BlockPos pos) {
        return new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
    }

    private void altSwap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }

    private enum Stage {
        None,
        BuddedPiston,
        UnBud,
        WaitForUnBud,
        UpdateAndReplace
    }
}
