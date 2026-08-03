package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.PlayerJumpEvent;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.ArrayList;
import java.util.List;

public class HeadBurrow extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgPre = this.settings.createGroup("Pre");
    private final SettingGroup sgPost = this.settings.createGroup("Post");

    private final Setting<Integer> moveDelay = sgGeneral.add(new IntSetting.Builder()
        .name("move-delay")
        .description("How long to allow movement")
        .defaultValue(2)
        .sliderRange(0, 20)
        .build()
    );

    /* --- Pre --- */
    private final Setting<Integer> syncDelay = sgPre.add(new IntSetting.Builder()
        .name("sync-delay")
        .description("How long to sync")
        .defaultValue(60)
        .sliderRange(0, 100)
        .build()
    );

    /* --- Post --- */
    private final Setting<Integer> waitDelay = sgPost.add(new IntSetting.Builder()
        .name("wait-delay")
        .description("How long to wait")
        .defaultValue(20)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> customPacketAmount = sgPost.add(new IntSetting.Builder()
        .name("custom-packet-amount")
        .description("How many funsies to add")
        .defaultValue(60)
        .sliderRange(0, 100)
        .build()
    );

    private final List<Packet<?>> packets = new ArrayList<>();
    Stage stage = Stage.SYNC;
    Vec3d startPos = Vec3d.ZERO;
    private boolean dumped = false;

    int counter = 0;

    public HeadBurrow() {
        super(TarAddon.CATEGORY, "head-burrow", "Burrows your head inside a block");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        if (!mc.player.isOnGround()) {
            error("Not on ground!");
            this.toggle();
            return;
        }

        if (!BlockUtils.canPlace(mc.player.getBlockPos().up(), false) || BlockUtils.getClosestPlaceSide(mc.player.getBlockPos().up()) == null) {
            error("Cant place here!");
            this.toggle();
            return;
        }

        packets.clear();
        stage = Stage.SYNC;
        startPos = mc.player.getEntityPos();
        dumped = false;
        counter = 0;
    }

    @Override
    public void onDeactivate() {
        packets.clear();

        if (mc.player == null) return;
        if (!dumped) {
            mc.player.setPosition(startPos.getX(), startPos.getY(), startPos.getZ());
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) {
            toggle();
            return;
        }

        FindItemResult obby = InvUtils.find(Items.OBSIDIAN);
        if (!obby.found()) {
            error("You need obsidian!");
            this.toggle();
            return;
        }

        int x = MathHelper.floor(startPos.getX());
        int y = MathHelper.floor(startPos.getY());
        int z = MathHelper.floor(startPos.getZ());

        BlockPos start = new BlockPos(x, y, z);
        BlockPos head = start.up();

        switch (stage) {
            case SYNC -> {
                sendPacketSilent(new PlayerMoveC2SPacket.LookAndOnGround(MathHelper.wrapDegrees(counter * 10), 0, mc.player.isOnGround(), mc.player.horizontalCollision));
                counter++;
                if (counter >= syncDelay.get()) {
                    stage = Stage.BLINK;
                    counter = 0;
                }
            }
            case BLINK -> {
                if (BlockUtils.canPlace(head, true)) {
                    TarBlockUtils.InteractRunnable runnable = bhr -> {
                        double yaw = Rotations.getYaw(bhr.getPos());
                        double pitch = Rotations.getPitch(bhr.getPos());

                        sendRotatePacket(yaw, pitch, RotationPacket.Full);

                        InvUtils.swap(obby.slot(), true);
                        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 0));
                        InvUtils.swapBack();
                    };

                    TarBlockUtils.place(head, true, true, Blocks.OBSIDIAN, runnable);

                    stage = Stage.WAIT;
                    counter = 0;

                }
            }
            case WAIT -> {
                counter++;
                if (counter >= moveDelay.get() && mc.player.isOnGround()) {
                    stage = Stage.BLINKSYNC;
                    counter = 0;
                }
            }
            case BLINKSYNC -> {
                counter++;
                if (counter >= waitDelay.get()) {
                    addCustom(customPacketAmount.get());

                    stage = Stage.SEND;
                    dump();

                    this.toggle();
                }
            }
        }
    }

    @EventHandler
    private void onJump(PlayerJumpEvent event) {
        if (stage == Stage.SYNC) {
            event.cancel();
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (stage == Stage.SYNC || stage == Stage.BLINKSYNC) {
            ((IVec3d) event.movement).meteor$set(0, event.movement.y, 0);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!Utils.canUpdate()) {
            toggle();
            return;
        }

        if (stage == Stage.SEND) return;

        if (event.packet instanceof UpdateSelectedSlotC2SPacket || event.packet instanceof PlayerInteractBlockC2SPacket || event.packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
            // dont queue if syncing
            if (event.packet instanceof PlayerMoveC2SPacket && (stage == Stage.SYNC || stage == Stage.BLINKSYNC))
                return;

            synchronized (packets) {
                packets.add(event.packet);
            }
        }
    }


    public void addCustom(int amount) {
        if (mc.player == null) return;
        synchronized (packets) {
            for (int i = 0; i < amount; i++) {
                packets.add(new PlayerMoveC2SPacket.LookAndOnGround(
                    MathHelper.wrapDegrees(i * 10),
                    0, mc.player.isOnGround(),
                    mc.player.horizontalCollision));
            }
        }
    }

    public void dump() {
        if (mc.getNetworkHandler() == null) return;

        dumped = true;

        synchronized (packets) {
            packets.forEach(this::sendPacketSilent);
            packets.clear();
        }
    }

    private enum Stage {
        SYNC,
        BLINK,
        PLACE,
        WAIT,
        BLINKSYNC,
        SEND
    }
}
