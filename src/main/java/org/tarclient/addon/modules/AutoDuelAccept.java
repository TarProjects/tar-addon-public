package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;
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

    private static final String regex = "^Duel request received from ([a-zA-Z0-9_]+).";

    public AutoDuelAccept() {
        super(TarAddon.CATEGORY, "auto-duel-accept", "Accepts crystalpvp.cc duels from specific people");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (!Utils.canUpdate()) {
            return;
        }
        String message = event.getMessage().getString();

        // Matches for duel start
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);


        if (matcher.find()) {
            String username = matcher.group(1);
            if (usernames.get().contains(username)) {
                mc.getNetworkHandler().sendChatCommand("duel accept " + username);
                info("Accepted duel request from authorized user " + username);
            }
        }
    }

}
