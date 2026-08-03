package org.tarclient.addon.utils;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class VirtualHotbarUtils {

    public static final int[] VISUAL_TO_REAL = new int[9];
    public static final int[] REAL_TO_VISUAL = new int[9];

    public static volatile int virtualSelectedSlot = 0;

    public static void reset() {
        for (int i = 0; i < 9; i++) {
            VISUAL_TO_REAL[i] = i;
            REAL_TO_VISUAL[i] = i;
        }

        virtualSelectedSlot = 0;
    }

    public static int getVirtualSelectedSlot() {
        return virtualSelectedSlot;
    }

    public static int getRealSelectedSlot() {
        if (mc.player == null) return -1;
        return mc.player.getInventory().getSelectedSlot();
    }

    public static int visualToReal(int visualSlot) {
        return VISUAL_TO_REAL[visualSlot];
    }

    public static int realToVisual(int realSlot) {
        return REAL_TO_VISUAL[realSlot];
    }

    /**
     * Get stack used for rendering the hotbar with visual slot id
     */
    public static ItemStack getVisualStack(PlayerInventory inventory, int visualSlot) {
        return inventory.getStack(VISUAL_TO_REAL[visualSlot]);
    }

    /**
     * Called when user scrolls or presses number key
     */
    public static void select(int visualSlot) {
        if (visualSlot < 0 || visualSlot > 8) return;
        if (visualSlot == virtualSelectedSlot) return;
        if (mc.player == null || mc.interactionManager == null) return;

        int currentReal = mc.player.getInventory().getSelectedSlot();
        int targetReal = VISUAL_TO_REAL[visualSlot];

        if (currentReal != targetReal) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                36 + targetReal,
                currentReal,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
            );

            int visualCurrent = REAL_TO_VISUAL[currentReal];
            int visualTarget = REAL_TO_VISUAL[targetReal];

            VISUAL_TO_REAL[visualCurrent] = targetReal;
            VISUAL_TO_REAL[visualTarget] = currentReal;

            REAL_TO_VISUAL[currentReal] = visualTarget;
            REAL_TO_VISUAL[targetReal] = visualCurrent;
        }

        virtualSelectedSlot = visualSlot;
    }
}
