package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.MioUtils;
import org.tarclient.addon.utils.TarBlockUtils;


public class AutoEnderChest extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range of blockplace and break")
        .defaultValue(5)
        .sliderRange(0, 6)
        .build()
    );

    int timer = 0;
    BlockPos target;

    public AutoEnderChest() {
        super(TarAddon.CATEGORY, "auto-ender-chest", "Places and mines ender chests with less delay on crystalpvp.cc");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;
        MioUtils.toggleAutoMine(false);
        MioUtils.toggleSpeedMine(false);
        timer = -5;

        HitResult result = TarBlockUtils.raycastBlocks(range.get());
        if (!(result instanceof BlockHitResult bhr)) {
            error("No target! Please look at a block and re-enable the module!");
            this.toggle();
            return;
        }

        target = bhr.getBlockPos().offset(bhr.getSide());
        if (!mc.world.getBlockState(target).isAir() && !mc.world.getBlockState(target).getBlock().equals(Blocks.ENDER_CHEST)) {
            error("Target not air/enderchest! Please look in a valid position!");
            this.toggle();
            return;
        }

        if (mc.player.getEyePos().squaredDistanceTo(target.toBottomCenterPos()) > range.get() * range.get()) {
            error("Too far away!");
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        MioUtils.toggleAutoMine(true);
        MioUtils.toggleSpeedMine(true);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getEyePos().squaredDistanceTo(target.toBottomCenterPos()) > range.get() * range.get()) {
            error("Too far away!");
            this.toggle();
            return;
        }

        if (timer == 0) {
            double yaw = Rotations.getYaw(target.toCenterPos());
            double pitch = Rotations.getPitch(target.toCenterPos());
            sendRotatePacket(yaw, pitch, RotationPacket.Full);

            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, Direction.UP));
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, Direction.UP));
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, Direction.UP));
        }

        if (timer >= 18 && mc.player.isOnGround()) {
            FindItemResult pickaxe = InvUtils.find(Items.NETHERITE_PICKAXE);
            if (!pickaxe.found()) {
                error("No pickaxe/invalid pickaxe!");
                this.toggle();
                return;
            }

            FindItemResult enderChest = InvUtils.find(Items.ENDER_CHEST);
            if (!enderChest.found()) {
                error("No enderchests in inventory!");
                this.toggle();
                return;
            }

            TarBlockUtils.place(target, false, true, Blocks.ENDER_CHEST, (blockHitResult) -> {
                double yaw = Rotations.getYaw(blockHitResult.getPos());
                double pitch = Rotations.getPitch(blockHitResult.getPos());

                sendRotatePacket(yaw, pitch, RotationPacket.Full);

                swap(enderChest.slot());
                BlockUtils.interact(blockHitResult, Hand.MAIN_HAND, true);
                swap(enderChest.slot());
            });

            swap(pickaxe.slot());
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, Direction.UP));
            swap(pickaxe.slot());

            timer = 0;

            return;
        }

        timer++;
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }
}
