package org.tarclient.addon.modules.disabler;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.modules.Disabler;

// author: nullable
// server: anarcion
public class GrimAirPlaceRotationDisabler extends DisablerMode {
    public GrimAirPlaceRotationDisabler(MinecraftClient mc, Disabler module) {
        super(mc, module);
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (!mc.isOnThread() || mc.world == null) return;

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos packetBlockPos = packet.getBlockHitResult().getBlockPos();

            // check if we are placing?
            if (!isPlacing(packetBlockPos, packet.getHand())) return;

            Vec3d airPlacedPos = packetBlockPos.toCenterPos();
            if (airPlacedPos.squaredDistanceTo(packet.getBlockHitResult().getPos()) > 1e-7) {
                // wasnt airplaced! recalcuate the proper airplace position
                event.cancel();

                // desired place position AFTER clicking
                BlockPos placePosition = packetBlockPos;

                // replaceable? Check cached position through disabler aswell
                if (shouldOffset(placePosition)) {
                    placePosition = placePosition.offset(packet.getBlockHitResult().getSide());
                }

                BlockHitResult bhr = new BlockHitResult(placePosition.toCenterPos(), Direction.UP, placePosition, false);
                // resend
                event.sendSilently(new PlayerInteractBlockC2SPacket(packet.getHand(), bhr, packet.getSequence()));
            }
        }
    }

    private boolean shouldOffset(BlockPos placePosition) {
        if (mc.world == null) return true;

        BlockState state;
        if (placePosition.equals(this.module.getStoredBlockPos())) {
            // get original blockstate!
            state = this.module.getStoredBlockState();
        } else {
            // assume packet only, get from world
            state = mc.world.getBlockState(placePosition);
        }

        // wtf
        if (state == null) return true;

        return !state.isReplaceable();
    }

    private boolean isPlacing(BlockPos pos, Hand hand) {
        if (mc.world == null || mc.player == null) return false;

        boolean hasAnyItem = !mc.player.getMainHandStack().isEmpty() || !mc.player.getOffHandStack().isEmpty();
        if (mc.player.shouldCancelInteraction() && hasAnyItem) {
            return true;
        }

        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (BlockUtils.isClickable(block)) return false;

        ItemStack stackInHand = mc.player.getStackInHand(hand);
        if (stackInHand.isEmpty() || mc.player.getItemCooldownManager().isCoolingDown(stackInHand)) {
            return false;
        }

        return true;
    }
}
