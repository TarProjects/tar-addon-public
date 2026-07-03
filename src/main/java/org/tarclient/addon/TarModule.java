package org.tarclient.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IChatHud;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TarModule extends Module {
    private final String prefix = Formatting.GRAY + "[" + Formatting.DARK_RED + "Tar" + Formatting.GRAY + "]";

    public TarModule(Category category, String name, String desc) {
        super(category, name, desc);
    }

    // override default uses, and instead send always
    @Override
    public void sendToggledMsg() {}

    public void tar$sendToggledMsg() {
        if (Config.get().chatFeedback.get() && chatFeedback && mc.world != null) {
            // This is stupid but we can still use gray from Formatting...
            String msg = prefix + " Toggled " + Formatting.WHITE + Utils.nameToTitle(name) + (isActive() ? Formatting.GREEN + " on" : Formatting.RED + " off");
            addMsg(Text.of(msg), hashCode());
        }
    }

    public void toggle() {
        super.toggle();
        tar$sendToggledMsg();
    }

    public void info(String text) {
        if (mc.world != null) {
            String msg = prefix + Formatting.GRAY + " [" + Formatting.LIGHT_PURPLE + Utils.nameToTitle(name) + Formatting.GRAY + "] " + Formatting.GRAY + text;
            addMsg(Text.of(msg), 0);
        }
    }

    public void error(String text) {
        if (mc.world != null) {
            String msg = prefix + Formatting.GRAY + " [" + Formatting.LIGHT_PURPLE + Utils.nameToTitle(name) + Formatting.GRAY + "] " + Formatting.RED + text;
            addMsg(Text.of(msg), 0);
        }
    }

    public void addMsg(Text text, int id) {
        ((IChatHud) MeteorClient.mc.inGameHud.getChatHud()).meteor$add(text, id);
    }

    public void sendPacketSilent(Packet<?> packet) {
        if (packet != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().send(packet, null, true);
        }
    }

    public void sendRotatePacket(double yaw, double pitch, RotationPacket packetType) {
        sendRotatePacket((float) yaw, (float) pitch, packetType);
    }

    public void sendRotatePacket(float yaw, float pitch, RotationPacket packetType) {
        if (mc.player == null) return;
        switch (packetType) {
            case Full ->
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            case Minimal ->
                sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }

    public void sendPacket(Packet<?> packet) {
        if (packet != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    public enum RotationPacket {
        Full,
        Minimal
    }
}
