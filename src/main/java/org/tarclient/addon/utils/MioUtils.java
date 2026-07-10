package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.tarclient.addon.events.SendTypedMessageEvent;
import org.tarclient.addon.modules.MioCompatibility;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MioUtils extends GenericUtil {
    public static MioCompatibility mioCompatibility;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(MioUtils.class);
    }

    @EventHandler
    private static void onMessageReceive(ReceiveMessageEvent event) {
        if (mioCompatibility == null) return;
        if (mioCompatibility.ignoreNotif.get() && !mioCompatibility.ignoreNotifPrefix.get().isEmpty()) {
            if (event.getMessage().getString().startsWith(mioCompatibility.ignoreNotifPrefix.get())) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private static void onSendTypedMessage(SendTypedMessageEvent event) {
        if (mioCompatibility == null) return;
        if (mioCompatibility.warnFriendSync.get() && !mioCompatibility.warnFriendSyncPattern.get().isEmpty()) {
            try {
                String message = event.message;

                Pattern pattern = Pattern.compile(Pattern.quote(mioCompatibility.mioPrefix.get()) + mioCompatibility.warnFriendSyncPattern.get());
                Matcher matcher = pattern.matcher(message);

                if (matcher.matches() && matcher.groupCount() >= 2) {
                    String action = matcher.group(1);
                    String username = matcher.group(2);

                    boolean add = action.equals("add");

                    MutableText displayMessage = Text.literal("[Friend Sync] ")
                        .styled(style -> style.withColor(Formatting.GOLD))
                        .append(Text.literal("You have ")
                            .styled(style -> style.withColor(Formatting.WHITE)))
                        .append(Text.literal(add ? "added" : "removed")
                            .styled(style -> style.withColor(add ? Formatting.GREEN : Formatting.RED)))
                        .append(Text.literal(" friend: ")
                            .styled(style -> style.withColor(Formatting.WHITE)))
                        .append(Text.literal(username)
                            .styled(style -> style.withColor(Formatting.AQUA)));


                    Text addButton = Text.literal("[Add Friend]")
                        .styled(style -> style
                            .withColor(Formatting.GREEN)
                            .withClickEvent(getSendCommandClickEvent("friends add " + username))
                            .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal("Add " + username + " to Meteor friends")
                            ))
                        );

                    Text removeButton = Text.literal("[Remove Friend]")
                        .styled(style -> style
                            .withColor(Formatting.RED)
                            .withClickEvent(getSendCommandClickEvent("friends remove " + username))
                            .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal("Remove " + username + " from Meteor friends")
                            ))
                        );

                    addMsg(displayMessage.append(" ").append(add ? addButton : removeButton), 0);
                }
            } catch (Exception ignored) {
            }
        }
    }
    /**
     * Helper method to toggle notifications if it should
     */
    public static void toggleNotif() {
        if (!mioCompatibility.toggleNotificationsMessage.get().isEmpty()) {
            // internal because sendMioMessage would StackOverflow lol
            internalSendWithPrefix(mioCompatibility.toggleNotificationsMessage.get());
        }
    }

    public static void sendMioMessage(String toSend) {
        toggleNotif();
        internalSendWithPrefix(toSend);
        toggleNotif();
    }

    public static void resetSpeedMine() {
        if (!mioCompatibility.enabled.get()) return;
        String packetMine = mioCompatibility.toggleSpeedMine.get();
        if (!packetMine.isEmpty()) {
            toggleModule(packetMine);
            toggleModule(packetMine);
        }
    }

    public static void toggleAutoMine(boolean state) {
        toggleModule(mioCompatibility.toggleAutoMine.get(), state);
    }

    public static void toggleSpeedMine(boolean state) {
        toggleModule(mioCompatibility.toggleSpeedMine.get(), state);
    }

    public static void enableAttackingModules() {
        if (!mioCompatibility.enabled.get() || mioCompatibility.toggleAttackingModules.get().isEmpty()) return;
        for (String moduleToToggle : mioCompatibility.toggleAttackingModules.get()) {
            if (moduleToToggle.isEmpty()) continue;

            toggleModule(moduleToToggle, true);
        }
    }

    public static void disableAttackingModules() {
        if (!mioCompatibility.enabled.get() || mioCompatibility.toggleAttackingModules.get().isEmpty()) return;
        for (String moduleToToggle : mioCompatibility.toggleAttackingModules.get()) {
            if (moduleToToggle.isEmpty()) continue;

            toggleModule(moduleToToggle, false);
        }
    }

    public static void toggleModule(String module) {
        sendMioMessage("toggle " + module);
    }

    public static void toggleModule(String module, boolean state) {
        sendMioMessage("toggle " + module + " " + state);
    }


    private static void internalSendWithPrefix(String message) {
        ChatUtils.sendPlayerMsg(mioCompatibility.mioPrefix.get() + message, false);
    }

    public static double getPacketMineDamage() {
        return mioCompatibility.speedMineDamage.get();
    }

    public static boolean getAssumeLastBlockState() {
        return mioCompatibility.assumeLastBlockState.get();
    }

    private static ClickEvent getSendCommandClickEvent(String message) {
        return getSendMessageClickEvent(Config.get().prefix.get() + message);
    }

    private static ClickEvent getSendMessageClickEvent(String message) {
        return new ClickEvent.Custom(Identifier.of("tar:send_message"), Optional.of(NbtString.of(message)));
    }

}
