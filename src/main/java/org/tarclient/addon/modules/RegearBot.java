package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegearBot extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<String>> usernames = sgGeneral.add(new StringListSetting.Builder()
        .name("usernames")
        .description("Authorized users")
        .defaultValue("")
        .build()
    );


    private final Setting<String> regearCommand = sgGeneral.add(new StringSetting.Builder()
        .name("regear-command")
        .description("What should the bot be messaged in order to regear? Leave as empty if disabled")
        .defaultValue("!regear")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("drop-delay")
        .description("How many ticks to wait before dropping gear")
        .defaultValue(10)
        .sliderRange(0, 120)
        .build()
    );

    private final Setting<Integer> batch = sgGeneral.add(new IntSetting.Builder()
        .name("batch")
        .description("How many items to drop at a time")
        .defaultValue(1)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<Integer> modulo = sgGeneral.add(new IntSetting.Builder()
        .name("modulo")
        .description("This tells how many ticks have to happen before each batch")
        .defaultValue(2)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Boolean> activateChinaExploit = sgGeneral.add(new BoolSetting.Builder()
        .name("activate-china-exploit")
        .description("This module will automatically re-spectate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chinaExploitDelay = sgGeneral.add(new IntSetting.Builder()
        .name("china-exploit-delay")
        .description("When to enable China Exploit")
        .defaultValue(90)
        .sliderRange(0, 120)
        .visible(activateChinaExploit::get)
        .build()
    );




    private static final String whisperRegex = "^(\\w+) says: (.+)";

    public RegearBot() {
        super(TarAddon.CATEGORY, "regear-bot", "Tuff bot that gives you gear");
    }


    Stage stage = Stage.None;
    int counter = 0;

    @Override
    public void onActivate() {
        stage = Stage.None;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        if (stage == Stage.Wait) {
            if (counter >= delay.get()) {
                stage = Stage.Pot;
                counter = 0;
                return;
            }
            counter++;
            return;
        }

        if (stage == Stage.Pot) {
            if (counter >= 5) {
                stage = Stage.DropArmor;
                counter = 0;
                return;
            }

            FindItemResult item = InvUtils.findInHotbar(Items.SPLASH_POTION);

            if (item.found()) {
                InvUtils.swap(item.slot(), true);

                sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), 90, mc.player.isOnGround(), mc.player.horizontalCollision));
                sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), 90));

                InvUtils.swapBack();
            }

            counter++;
            return;
        }


        if (stage == Stage.DropArmor) {
            if (counter >= 4 * modulo.get()) {
                counter = 0;
                if (activateChinaExploit.get()) {
                    stage = Stage.ChinaExploit;
                } else {
                    stage = Stage.None;
                }
                mc.getNetworkHandler().sendChatCommand("kill");
                return;
            }
            if (counter % modulo.get() != 0) {
                // skip ticks
                counter++;
                return;
            }
            InvUtils.drop().slot(SlotUtils.ARMOR_START + (counter / modulo.get()));
            counter++;
        }

        if (stage == Stage.ChinaExploit) {
            if (counter >= chinaExploitDelay.get()) {
                stage = Stage.None;
                counter = 0;
                Objects.requireNonNull(Modules.get().get(ChinaExploit.class)).toggle();
                return;
            }

            counter++;
        }
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (!Utils.canUpdate() || stage != Stage.None) return;

        String message = event.getMessage().getString();

        // Matches for duel start
        Pattern whisperPattern = Pattern.compile(whisperRegex);
        Matcher whisperMatcher = whisperPattern.matcher(message);


        if (whisperMatcher.find()) {
            String username = whisperMatcher.group(1);
            String msg = whisperMatcher.group(2);

            if (usernames.get().contains(username) && msg.strip().equalsIgnoreCase((regearCommand.get().strip()))) {
                regear(username);
                stage = Stage.Wait;
            }
        }
    }

    private void regear(String username) {
        if (mc.player.getGameMode() != GameMode.SPECTATOR) {
            error("Not in spectator!");
            return;
        }


        PlayerListEntry player = null;
        for (PlayerListEntry playerListEntry : mc.getNetworkHandler().getPlayerList()) {
            if (playerListEntry.getProfile().name().equalsIgnoreCase(username)) {
                player = playerListEntry;
                break;
            }
        }

        if (player == null) {
            error("Player with name " + username + " was not found!");
            return;
        }

        mc.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(player.getProfile().id()));
    }


    enum Stage {
        None,
        Wait,
        Pot,
        DropArmor,
        ChinaExploit
    }
}
