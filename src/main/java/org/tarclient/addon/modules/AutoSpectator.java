package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoSpectator extends TarModule {
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

    Stage stage;
    int counter;
    String winner;

    public AutoSpectator() {
        super(TarAddon.CATEGORY, "auto-spectator", "Crystalpvp.cc spectator exploit. Tweak delay if needed. This module will trigger when someone exits a duel");
    }

    @Override
    public void onActivate() {
        stage = Stage.WaitForMessage;
        counter = 0;
        winner = "";
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!Utils.canUpdate() || stage != Stage.WaitForMessage) {
            return;
        }
        String message = event.getMessage().getString();


        // Matches for duel end messages
        String regex = "^\\[Duels] (\\w+) \\(\\d+\\) \\(\\+\\d+\\) has defeated (\\w+) \\(\\d+\\) \\(-\\d+\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);


        if (matcher.find()) {
            this.winner = matcher.group(1);

            stage = Stage.Delay;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            return;
        }
        if (stage == Stage.Delay) {
            counter++;
            // Handling kit here since otherwise we would run into issues with positions when exiting a duel to autospectate
            if (counter == delay.get() / 2) {
                // Stupid having the same if statement twice, no can do
                if (!Objects.equals(kit.get(), "") && mc.player.getY() >= 120 && mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL) {
                    mc.getNetworkHandler().sendChatCommand("kit " + kit.get());
                }
            }
            if (counter >= delay.get()) {
                if (mc.player.getY() >= 120 && mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL) {
                    info("Spectating!");
                    mc.getNetworkHandler().sendChatCommand("spectate " + winner);
                    if (disableAutoKit.get()) {
                        AutoKit autoKit = Modules.get().get(AutoKit.class);
                        if (autoKit.isActive()) {
                            autoKit.toggle();
                        }
                    }
                }
                onActivate();
            }
        }
    }

    enum Stage {
        WaitForMessage,
        Delay,
    }
}
