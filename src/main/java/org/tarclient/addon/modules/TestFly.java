package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.List;


public class TestFly extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between teleporting")
        .defaultValue(1)
        .sliderRange(0, 100)
        .build()
    );


    private final Setting<List<IntRange>> test = sgGeneral.add(new IntRangeListSetting.Builder()
        .name("test")
        .description("test")
        .min(0)
        .max(10)
        .build()
    );

    BlockPos toPlace = null;

    public TestFly() {
        super(TarAddon.CATEGORY, "test", "");
    }

    @Override
    public void onActivate() {
        toPlace = null;
    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        FindItemResult anvil = InvUtils.findInHotbar(Items.ANVIL);
        if (!anvil.found()) return;

        if (toPlace != null) {
            if (TarBlockUtils.place(toPlace, false, true, Blocks.OBSIDIAN, (blockHitResult) -> {
                float yaw = (float) Rotations.getYaw(blockHitResult.getPos());
                float pitch = (float) Rotations.getPitch(blockHitResult.getPos());
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

                // only swaps once so we don't have to spam swaps
                InvUtils.swap(anvil.slot(), true);
                BlockUtils.interact(blockHitResult, anvil.getHand(), true);
            })) {
                toPlace = null;
                InvUtils.swapBack();
            }
        }
    }

    @EventHandler
    private void onBlockChange(BlockUpdateEvent event) {
        if ((event.oldState.getBlock() == Blocks.OBSIDIAN || event.oldState.getBlock() == Blocks.ANVIL) && event.newState.getBlock() == Blocks.AIR) {
            info("Should place");
            toPlace = event.pos;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {

    }
    // setheadyaw positionsync rotateandmoverelative

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;

    }


}
