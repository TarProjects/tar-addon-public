package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.VirtualHotbarUtils;

public class VirtualHotbar extends TarModule {
    public VirtualHotbar() {
        super(TarAddon.CATEGORY, "virtual-hotbar", "Uses swap packets instead of changing hotbar for mining compatibility");
        this.runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        VirtualHotbarUtils.reset();
        if (mc.player == null) return;
        VirtualHotbarUtils.virtualSelectedSlot = mc.player.getInventory().getSelectedSlot();
    }

    @EventHandler
    private void onWorldJoin(GameJoinedEvent event) {
        VirtualHotbarUtils.reset();
    }

    @EventHandler
    private void onDeath(PacketEvent.Receive event) {
        if (event.packet instanceof DeathMessageS2CPacket) {
            VirtualHotbarUtils.reset();
        }
    }
}

