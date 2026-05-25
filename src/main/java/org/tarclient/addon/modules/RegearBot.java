package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.TarInvUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegearBot extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgConfig = this.settings.createGroup("Config");


    private final Setting<List<String>> usernames = sgGeneral.add(new StringListSetting.Builder()
        .name("usernames")
        .description("Authorized users")
        .defaultValue("CrystalEU")
        .build()
    );

    private final Setting<String> regearCommand = sgGeneral.add(new StringSetting.Builder()
        .name("regear-command")
        .description("What should the bot be messaged in order to regear? Leave as empty if disabled")
        .defaultValue("!regear")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How many ticks to wait after spectating")
        .defaultValue(1)
        .sliderRange(0, 10)
        .build()
    );


    /* --- CONFIG --- */
    private final Setting<Boolean> usePotions = sgConfig.add(new BoolSetting.Builder()
        .name("use-potions")
        .description("Uses potions")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> potionAmount = sgConfig.add(new IntSetting.Builder()
        .name("potion-amount")
        .description("How many potions to drop each tick")
        .defaultValue(3)
        .sliderRange(0, 10)
        .visible(usePotions::get)
        .build()
    );

    private final Setting<Boolean> dropArmor = sgConfig.add(new BoolSetting.Builder()
        .name("drop-armor")
        .description("Drop armor before killing. NOTE: takes more time!")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> modulo = sgConfig.add(new IntSetting.Builder()
        .name("modulo")
        .description("This tells how many ticks have to happen before each batch")
        .defaultValue(2)
        .sliderRange(0, 4)
        .visible(dropArmor::get)
        .build()
    );

    private final Setting<Boolean> activateChinaExploit = sgConfig.add(new BoolSetting.Builder()
        .name("activate-china-exploit")
        .description("This module will automatically re-spectate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chinaExploitDelay = sgConfig.add(new IntSetting.Builder()
        .name("china-exploit-delay")
        .description("When to enable China Exploit")
        .defaultValue(90)
        .sliderRange(0, 120)
        .visible(activateChinaExploit::get)
        .build()
    );

    private static final String whisperRegex = "^(\\w+) says: ([\\w!]+) (\\w+)?";

    public RegearBot() {
        super(TarAddon.CATEGORY, "regear-bot", "Tuff bot that gives you gear");
    }


    Stage stage = Stage.None;
    int counter = 0;
    final List<Integer> thrown = new ArrayList<>();

    @Override
    public void onActivate() {
        stage = Stage.None;
        counter = 0;
        thrown.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        if (stage == Stage.Wait) tickWait();
        if (stage == Stage.Pot) tickPot();
        if (stage == Stage.DropArmor) tickDropArmor();
        if (stage == Stage.ChinaExploit) tickChinaExploit();
    }

    private void tickWait() {
        if (counter >= delay.get()) {
            stage = Stage.Pot;
            counter = 0;
        } else {
            counter++;
        }
    }

    private void tickPot() {
        if (!usePotions.get()) {
            stage = Stage.DropArmor;
            counter = 0;
            thrown.clear();
            return;
        }


        for (int i = 0; i < potionAmount.get(); i++) {
            int slot = findThrowable();

            if (slot != -1) { // item exists in hotbar
                throwPot(slot);
                thrown.add(slot); // no duplicate throw
            } else {
                // slot wasn't found
                stage = Stage.DropArmor;
                counter = 0;
                thrown.clear();
            }
        }
    }

    private int findThrowable() {
        // "complex" logic to not accidentally throw same pot on high ping
        ArrayList<Integer> items = TarInvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.SPLASH_POTION);

        for (Integer slot : items) {
            if (!thrown.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private void throwPot(int slot) {
        InvUtils.swap(slot, true);
        Hand hand = (slot == SlotUtils.OFFHAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        sendPacket(new PlayerInteractItemC2SPacket(hand, 0, mc.player.getYaw(), -90));
        InvUtils.swapBack();
    }

    private void tickDropArmor() {
        if (!dropArmor.get() || counter >= 4 * modulo.get()) { // either done dropping or not gonna drop armor
            counter = 0;
            stage = Stage.ChinaExploit;
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

    private void tickChinaExploit() {
        if (!activateChinaExploit.get()) {
            stage = Stage.None;
            counter = 0;
        }

        if (counter >= chinaExploitDelay.get()) {
            stage = Stage.None;
            counter = 0;
            Objects.requireNonNull(Modules.get().get(ChinaExploit.class)).toggle();
            return;
        }

        counter++;
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
            String command = whisperMatcher.group(2);
            if (usernames.get().contains(username) && command.strip().equalsIgnoreCase((regearCommand.get().strip()))) {
                if (whisperMatcher.groupCount() == 3) {
                    regear(whisperMatcher.group(3));
                } else {
                    regear(username);
                }
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

        sendPacket(new SpectatorTeleportC2SPacket(player.getProfile().id()));
        stage = Stage.Wait;
    }


    enum Stage {
        None,
        Wait,
        Pot,
        DropArmor,
        ChinaExploit
    }
}
