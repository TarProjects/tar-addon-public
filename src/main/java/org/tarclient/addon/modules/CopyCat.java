package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopyCat extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> target = sgGeneral.add(new StringSetting.Builder()
        .name("target")
        .description("Username of person to target")
        .defaultValue("FreedomForSkids")
        .build()
    );
    private final Setting<Double> wpm = sgGeneral.add(new DoubleSetting.Builder()
        .name("wpm")
        .description("How fast to type")
        .defaultValue(80)
        .sliderRange(0, 100)
        .build()
    );

    private static final String regex = "^<(\\w+)> (.+)";
    private final Queue<String> toSend = new ArrayDeque<>();
    private double lettersTyped = 0;

    public CopyCat() {
        super(TarAddon.CATEGORY, "copy-cat", "Copies messages sent by someone");
    }

    @Override
    public void onActivate() {
        lettersTyped = 0;
        toSend.clear();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.getNetworkHandler() == null) return;

        boolean paused = mc.currentScreen instanceof ChatScreen;

        if (!paused) {
            // not paused, peek into queue
            String peek = toSend.peek();
            if (peek == null) return;

            // stupid math to convert wpm into letters per tick
            lettersTyped += (wpm.get() * 5) / 1200;
            int messageLength = peek.length();

            if (lettersTyped >= messageLength) {
                mc.getNetworkHandler().sendChatMessage(toSend.poll());
                lettersTyped = 0;
            }
        }
    }

    @EventHandler
    private void onMessageSend(SendMessageEvent event) {
        // reset on send, just incase other modules/clients
        if (!event.isCancelled()) {
            lettersTyped = 0;
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!Utils.canUpdate()) {
            return;
        }

        String message = event.getMessage().getString();


        // Matches for duel end messages
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String sender = matcher.group(1);
            String messageSent = matcher.group(2);
            if (sender.equals(target.get())) {
                toSend.add(messageSent);
            }
        }
    }
}
