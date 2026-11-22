package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.settings.impl.StringStringMultiMapSetting;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoReply extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<Map.Entry<String, String>>> regexes = sgGeneral.add(new StringStringMultiMapSetting.Builder()
        .name("regexes")
        .description("Regexes to use")
        .build()
    );

    public AutoReply() {
        super(TarAddon.CATEGORY, "auto-reply", "Sends a chat message on specific regex. Be careful to not trigger loops with this!");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        String message = event.getMessage().getString();

        try {
            for (Map.Entry<String, String> entry : regexes.get()) {
                if (entry.getKey().isBlank() || entry.getValue().isBlank()) continue;
                Matcher matcher = Pattern.compile(entry.getKey()).matcher(message);
                if (matcher.find()) {
                    String reply = fromMatcher(matcher, entry.getValue());
                    ChatUtils.sendPlayerMsg(reply, false);
                }
            }
        } catch (Exception ignored) {
        }

    }

    private String fromMatcher(Matcher matcher, String template) {
        Pattern placeholderPattern = Pattern.compile("\\$(\\d+)");
        Matcher placeholderMatcher = placeholderPattern.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (placeholderMatcher.find()) {
            int groupNum = Integer.parseInt(placeholderMatcher.group(1));
            String replacement = matcher.group(groupNum); // from the original matcher
            // quoteReplacement escapes any $ or \ in the replacement
            placeholderMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        placeholderMatcher.appendTail(sb);

        return sb.toString();
    }
}
