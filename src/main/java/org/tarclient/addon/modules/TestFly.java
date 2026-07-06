package org.tarclient.addon.modules;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

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


    int timer = 0;

    Vec3d oldPos = null;
    public TestFly() {
        super(TarAddon.CATEGORY, "test", "");
    }

    @Override
    public void onActivate() {
        timer = 0;
        oldPos = null;
    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

    }

    @EventHandler
    private void onBlockChange(BlockUpdateEvent event) {

    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ClickSlotC2SPacket(int syncId, int revision, short slot, byte button, SlotActionType actionType, Int2ObjectMap<ItemStackHash> modifiedStacks, ItemStackHash cursor)) {
            System.out.println("ClickSlot");
            System.out.printf("id: %d, rev: %d, slot %d, button %d, type: %s%n", syncId, revision, slot, button, actionType.name());
        }

        if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {
            System.out.println("UpdateSlot");
            System.out.println("slot: " + packet.getSelectedSlot());
        }

        if (event.packet instanceof PlayerActionC2SPacket packet) {
            System.out.println("PlayerAction");
            System.out.printf("action: %s, pos %s, sequence: %s, dir: %s%n", packet.getAction().name(), packet.getPos().toShortString(), packet.getSequence(), packet.getDirection().name());
        }

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            System.out.println("InteractBlock");
            System.out.printf("hand: %s, blockpos: %s, pos: %s, side: %s%n", packet.getHand().name(), packet.getBlockHitResult().getBlockPos().toShortString(), packet.getBlockHitResult().getPos().toString(), packet.getBlockHitResult().getSide().name());
        }
    }

    // setheadyaw positionsync rotateandmoverelative
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            info("flag");
        }
    }


}
