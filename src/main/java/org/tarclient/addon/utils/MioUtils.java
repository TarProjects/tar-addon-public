package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.modules.MioCompatibility;

public class MioUtils {
    public static MioCompatibility mioCompatibility;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(MioUtils.class);
    }

    @EventHandler
    private static void onMessageReceive(ReceiveMessageEvent event) {
        if (mioCompatibility == null) return;
        if (!mioCompatibility.ignoreNotif.get() || mioCompatibility.ignoreNotifPrefix.get().isEmpty()) return;

        if (event.getMessage().getString().startsWith(mioCompatibility.ignoreNotifPrefix.get())) {
            event.cancel();
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

    public static void resetPacketMine() {
        if (!mioCompatibility.enabled.get()) return;
        String packetMine = mioCompatibility.togglePacketMine.get();
        if (!packetMine.isEmpty()) {
            toggleModule(packetMine);
            toggleModule(packetMine);
        }
    }

    public static void toggleAutoMine(boolean state) {
        toggleModule(mioCompatibility.toggleAutoMine.get(), state);
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
        return mioCompatibility.packetMineDamage.get();
    }

    public static boolean getAssumeLastBlockState() {
        return mioCompatibility.assumeLastBlockState.get();
    }
}
