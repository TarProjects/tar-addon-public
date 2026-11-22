package org.tarclient.addon.modules.disabler;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.client.MinecraftClient;
import org.tarclient.addon.modules.Disabler;

public class DisablerMode {
    protected final MinecraftClient mc;
    protected final Disabler module;

    public DisablerMode(MinecraftClient mc, Disabler module) {
        this.mc = mc;
        this.module = module;
    }

    public void onActivate() {}
    public void onDeactivate() {}

    public void onTick() {}
    public void onPacketSend(PacketEvent.Send event) {}
    public void onPacketReceive(PacketEvent.Receive event) {}
}
