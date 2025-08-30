package org.tarclient.addon.events;

import net.minecraft.item.ItemStack;

public class UseItemEvent {

    private static final UseItemEvent INSTANCE = new UseItemEvent();

    public ItemStack itemStack;

    public static UseItemEvent get(ItemStack itemStack) {
        INSTANCE.itemStack = itemStack;
        return INSTANCE;
    }
}
