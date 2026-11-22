package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.InteractionManagerTickEvent;

public class Packets extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Boolean> antiGhostBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-ghost-blocks")
        .description("Prevents ghost blocks through the world event packet")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiPearlInteract = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-pearl-interact")
        .description("Cancels block place packets when pearl is in hand.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> grimSync = sgGeneral.add(new BoolSetting.Builder()
        .name("grim-sync")
        .description("Sends move packets every interaction tick, syncs the position & rotation after pretick")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> staleRotationFix = sgGeneral.add(new BoolSetting.Builder()
        .name("stale-rotation-fix")
        .description("Forces rotate flag on stale rots by .full packets!")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> startGlidingFix = sgGeneral.add(new BoolSetting.Builder()
        .name("start-gliding-fix")
        .description("Fixes start gliding packets")
        .defaultValue(true)
        .build()
    );

    public Packets() {
        super(TarAddon.CATEGORY, "packets", "Various packet tweaks");
        this.runInMainMenu = true;
        this.enable();
    }

    boolean isInTick;
    boolean didSendMovePacket;

    @Override
    public void onActivate() {
        isInTick = false;
        didSendMovePacket = false;
    }


    // high priority runs first
    @EventHandler(priority = EventPriority.HIGHEST + 100)
    private void onTickPre(TickEvent.Pre event) {
        isInTick = true;
        didSendMovePacket = false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onMovePacketPost(SendMovementPacketsEvent.Post event) {
        // reset
        isInTick = false;
        didSendMovePacket = false;
    }

    // see ClientPlayerEntityMixin
    public boolean shouldSendStaleRotation() {
        if (!staleRotationFix.get()) return false;
        return didSendMovePacket;
    }

    @EventHandler
    private void onPre(InteractionManagerTickEvent event) {
        if (mc.player == null) return;
        if (!grimSync.get()) return;
        // send .full on grim sync
        sendRotatePacket(mc.player.getYaw(), mc.player.getPitch(), RotationPacket.Full);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (staleRotationFix.get()) {
            if (isInTick && event.packet instanceof PlayerMoveC2SPacket) didSendMovePacket = true;
        }

        if (startGlidingFix.get()) {
            if (event.packet instanceof ClientCommandC2SPacket packet && packet.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
                if (shouldCancelGliding()) {
                    event.cancel();
                }
            }
        }

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            if (antiPearlInteract.get() && mc.player.getStackInHand(packet.getHand()).isOf(Items.ENDER_PEARL)) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof WorldEventS2CPacket packet) {
            if (packet.getEventId() == 2001 && antiGhostBlocks.get()) {
                // hardcoded, block break
                mc.execute(() -> {
                    if (mc.world == null) return;
                    if (mc.world.getBlockState(packet.getPos()).isAir()) return; // isn't a ghost block, already air
                    mc.world.setBlockState(packet.getPos(), Blocks.AIR.getDefaultState());
                });
            }
        }
    }

    public boolean shouldCancelGliding() {
        // called from PlayerEntityMixin
        if (mc.player == null) return false;

        if (!isActive()) return false;
        if (!startGlidingFix.get()) return false;

        if (!mc.isOnThread()) {
            // not on thread, cheaper checks here
            return mc.player.isOnGround();
        } else {
            // fluid checks!
            return mc.player.isTouchingWater() || mc.player.isOnGround();
        }
    }
}

