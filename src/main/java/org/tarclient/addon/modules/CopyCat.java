package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopyCat extends TarModule {
    private static final String regex = "^\\[Duels] (\\w+) \\(\\d+\\) \\(\\+\\d+\\) has defeated (\\w+) \\(\\d+\\) \\(-\\d+\\)";
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("When to start spectating")
        .defaultValue(90)
        .sliderRange(0, 120)
        .build()
    );
    private final Setting<Boolean> disableAutoKit = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-auto-kit")
        .description("This module will disable Auto Kit module.")
        .defaultValue(true)
        .build()
    );
    private final Setting<String> kit = sgGeneral.add(new StringSetting.Builder()
        .name("kit")
        .description("Uses /kit before spectating to keep inventory. Leave as empty if you want to disable this.")
        .defaultValue("")
        .build()
    );
    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Send messages after specating")
        .defaultValue("")
        .build()
    );
    private final Setting<Integer> messagedelay = sgGeneral.add(new IntSetting.Builder()
        .name("message-delay")
        .description("When to actually send the messages")
        .defaultValue(10)
        .sliderRange(0, 120)
        .build()
    );
    int counter = 0;

    public CopyCat() {
        super(TarAddon.CATEGORY, "copy-cat", "Copies ");
    }

    @Override
    public void onActivate() {
        counter = 0;
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

        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
        }
    }
}
