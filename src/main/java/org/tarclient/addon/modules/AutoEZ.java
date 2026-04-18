package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoEZ extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();


    private final Setting<String> ezmessage = sgGeneral.add(new StringSetting.Builder()
        .name("auto-ez-message")
        .description("What message to send. {username} will be replaced with the \"loser's\" username")
        .defaultValue("ggs {username}")
        .build()
    );

    private final Setting<Integer> elocap = sgGeneral.add(new IntSetting.Builder()
        .name("elo-cap")
        .description("If people are under this elo, Auto EZ messages wont be sent")
        .defaultValue(1500)
        .sliderRange(0, 2500)
        .build()
    );

    public AutoEZ() {
        super(TarAddon.CATEGORY, "auto-ez", "Sends a message after you get a kill. Change the regex for other servers instead of crystalpvp.cc");
    }


    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!Utils.canUpdate()) {
            return;
        }
        String message = event.getMessage().getString();

        // Matches for kills
        String regex = "^(\\w+)\\((\\d+)\\) was killed by (\\w+)\\((\\d+)\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);


        if (matcher.find()) {
            String death = matcher.group(1);
            // Why tf am i naming this killer idk
            String killer = matcher.group(3);
            int eloDeath = Integer.parseInt(matcher.group(2));
            // int eloKiller = Integer.parseInt(matcher.group(4));
            if (Objects.equals(killer, mc.getNetworkHandler().getProfile().name()) && eloDeath > elocap.get()) {
                ChatUtils.sendPlayerMsg(ezmessage.get().replaceAll("(?i)\\{username}", death));
            }
        }
    }
}
