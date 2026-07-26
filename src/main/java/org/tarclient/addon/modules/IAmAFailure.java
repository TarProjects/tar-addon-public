package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.ClickSlotEvent;
import org.tarclient.addon.utils.MiningUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.*;

public class IAmAFailure extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Interact reach, dont mess this up")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> progress = sgGeneral.add(new DoubleSetting.Builder()
        .name("progress")
        .description("Progress check for valid swaps")
        .defaultValue(0.9)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("The global cooldown")
        .defaultValue(20)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> swap = sgGeneral.add(new BoolSetting.Builder()
        .name("swap")
        .description("Swaps...")
        .defaultValue(false)
        .build()
    );

    private int globalCooldown = 0;
    private float lastYaw;
    private float lastPitch;

    boolean ignoreSwap = false;

    public IAmAFailure() {
        super(TarAddon.CATEGORY, "i-am-a-failure", "Spreads the agenda");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        globalCooldown = 0;

        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();

        ignoreSwap = false;
    }


    // mio sends swap differently so use clickslotevent
    @EventHandler
    private void onClick(ClickSlotEvent event) {
        // avoid recursion on swap method
        if (ignoreSwap) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        // we dont care about this swap..
        if (globalCooldown > 0) return;
        if (MiningUtils.getBreakingProgress() < progress.get()) return;
        if (event.actionType != SlotActionType.SWAP) return; // only allow alt swap because why not

        // check if we should break this
        if (isValidSurroundPos(MiningUtils.getBreakingBlockPos())) {
            // do module
            // rely on chipped anvils on mainhand, or swap to them
            // store slot
            int slot = -1;
            if (swap.get()) {
                FindItemResult result = InvUtils.find(Items.CHIPPED_ANVIL);
                if (!result.found()) return;

                // found, swap
                swap(result.slot());
                slot = result.slot();
            } else {
                // check if we have anvil in hand rn
                if (mc.player.getInventory().getSelectedStack().getItem() != Items.CHIPPED_ANVIL) return;
            }


            HitResult hitResult = TarBlockUtils.raycastBlocks(reach.get(), lastYaw, lastPitch, mc.player.getEyePos());
            if (hitResult == null || hitResult.getType().equals(HitResult.Type.MISS) || !(hitResult instanceof BlockHitResult bhr)) {
                // raytrace miss -> should not happen unless reach is too low
                return;
            }

            // magic v2
            for (int i = 0; i < 25; i++) {
                sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 0));
            }

            // swap back
            if (swap.get()) {
                swap(slot);
            }

            globalCooldown = cooldown.get();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            lastYaw = packet.getYaw(lastYaw);
            lastPitch = packet.getPitch(lastPitch);
        }

        if (event.packet instanceof PlayerActionC2SPacket packet && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            // generalize this to get also from stop_break if no swap happened
            // bait
            //this.onClick(ClickSlotEvent.get(0,0,0,SlotActionType.SWAP,mc.player));
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null) return;

        if (globalCooldown > 0) {
            globalCooldown--;
        }
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        ignoreSwap = true;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
        ignoreSwap = false;
    }

    private boolean isValidSurroundPos(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;

        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > targetRange.get() * targetRange.get()) return false;

        boolean isValid = false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            // skip player
            if (player == mc.player) continue;
            if (TargetUtils.isBadTarget(player, targetRange.get())) continue;
            Direction dir = getSurroundDirection(pos, player.getBlockPos());
            if (dir != null && mc.world.getBlockState(pos).getBlock() != Blocks.BEDROCK) {
                // dont want our friend to desync :D
                if (!Friends.get().shouldAttack(player)) return false;
                // keep scanning for friends...
                isValid = true;
            }
        }
        return isValid;
    }

    private Direction getSurroundDirection(BlockPos surroundPos, BlockPos feet) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (feet.offset(dir).equals(surroundPos)) return dir;
        }
        return null;
    }
}
