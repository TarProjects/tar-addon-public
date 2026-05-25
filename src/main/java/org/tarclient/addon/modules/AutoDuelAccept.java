package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoDuelAccept extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<String>> usernames = sgGeneral.add(new StringListSetting.Builder()
        .name("usernames")
        .description("People who to automatically accept duels from")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> throwPearl = sgGeneral.add(new BoolSetting.Builder()
        .name("throw-pearl")
        .description("Throws pearl on !throw")
        .defaultValue(true)
        .build()
    );

    private static final Pattern duelRegex = Pattern.compile("^Duel request received from ([a-zA-Z0-9_]+).");
    private static final Pattern whisperRegex = Pattern.compile("^(\\w+) says: ([\\w!]+)");
    private String threw = null;
    private int send;
    private UUID toSend;

    public AutoDuelAccept() {
        super(TarAddon.CATEGORY, "auto-duel-accept", "Accepts crystalpvp.cc duels from specific people");
    }

    @Override
    public void onActivate() {
        threw = null;
        send = -1;
        toSend = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (send != -1) {
            send--;
            if (send == 0) {
                mc.getNetworkHandler().sendChatCommand("msg " + threw + " " + toSend);

                threw = null;
                send = -1;
                toSend = null;
            }
        }
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (!Utils.canUpdate()) {
            return;
        }
        String message = event.getMessage().getString();

        // Matches for duel start
        Matcher duelMatcher = duelRegex.matcher(message);


        if (duelMatcher.find()) {
            String username = duelMatcher.group(1);
            if (usernames.get().contains(username)) {
                mc.getNetworkHandler().sendChatCommand("duel accept " + username);
                info("Accepted duel request from authorized user " + username);
            }
        }

        Matcher whisperMatcher = whisperRegex.matcher(message);

        if (whisperMatcher.find()) {
            String username = whisperMatcher.group(1);
            String command = whisperMatcher.group(2);

            if (usernames.get().contains(username) && command.equalsIgnoreCase("!throw") && throwPearl.get()) {
                FindItemResult result = InvUtils.findInHotbar(Items.ENDER_PEARL);
                if (!result.found()) {
                    mc.getNetworkHandler().sendChatCommand("msg " + username + " ERROR");
                    return;
                }
                InvUtils.swap(result.slot(), true);

                sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), -90, mc.player.isOnGround(), mc.player.horizontalCollision));
                sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), -90));

                InvUtils.swapBack();

                threw = username;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (packet.getEntityType() == EntityType.ENDER_PEARL && packet.getEntityData() == mc.player.getId()) {
                if (threw == null || !throwPearl.get() || send != -1 || toSend != null) return;
                // send in 30 ticks
                toSend = packet.getUuid();
                send = 30;
            }
        }
    }

}
