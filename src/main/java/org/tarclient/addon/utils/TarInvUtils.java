package org.tarclient.addon.utils;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TarInvUtils {
    public static ArrayList<Integer> findInHotbar(Predicate<ItemStack> predicate) {
        return findPredicate(predicate, 0, 8);
    }

    public static ArrayList<Integer> findPredicate(Predicate<ItemStack> predicate, int start, int end) {
        if (mc.player == null) return new ArrayList<>();

        ArrayList<Integer> list = new ArrayList<>();

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (predicate.test(stack)) {
                list.add(i);
            }
        }

        return list;
    }
}
