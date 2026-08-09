package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.*;

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
        .description("Prevents ghost blocks through the world event packet")
        .defaultValue(true)
        .build()
    );

    public Packets() {
        super(TarAddon.CATEGORY, "packets", "Various packet tweaks");
        this.runInMainMenu = true;
        this.enable();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
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
}

