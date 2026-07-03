package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IChatHud;
import net.minecraft.text.Text;

public class GenericUtil {
    public static void addMsg(Text text, int id) {
        ((IChatHud) MeteorClient.mc.inGameHud.getChatHud()).meteor$add(text, id);
    }
}
