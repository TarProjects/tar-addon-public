package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;


public class AntiArmorBreak extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> percentage = sgGeneral.add(new DoubleSetting.Builder()
        .name("percentage")
        .description("Percentage of armor before taking it off")
        .defaultValue(5)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay")
        .defaultValue(5)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> warn = sgGeneral.add(new BoolSetting.Builder()
        .name("warn")
        .description("Shows a message on chat")
        .defaultValue(true)
        .build()
    );

    public AntiArmorBreak() {
        super(TarAddon.CATEGORY, "anti-armor-break", "Prevents armor from breaking by unequipping it.");
    }

    int cooldown;

    @Override
    public void onActivate() {
        cooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        for (int i = 0; i < 4; i++) {
            int slot = SlotUtils.ARMOR_START + i;
            ItemStack stack = mc.player.getInventory().getStack(slot);

            if (stack.isEmpty()) {
                continue;
            }

            boolean hasSimilar = false;

            for (ItemStack inventoryStack : mc.player.getInventory().getMainStacks()) {
                if (!inventoryStack.isEmpty() && inventoryStack.isOf(stack.getItem())) {
                    hasSimilar = true;
                    break;
                }
            }

            if (hasSimilar) continue;
            int maxDmg = stack.getMaxDamage();
            if (maxDmg == 0 || !Utils.hasEnchantment(stack, Enchantments.MENDING)) continue; // fallback or no mending

            int dmg = stack.getDamage();
            double durability = (double) (maxDmg - dmg) / maxDmg;

            if (durability * 100 < percentage.get() && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                if (warn.get()) info(String.format("Trying to unequip armor with %d%%!", Math.round(durability)));

                if (hasInventorySpace(mc.player)) {
                    InvUtils.shiftClick().slotArmor(i);
                } else {
                    InvUtils.click().slotArmor(i);
                }
                cooldown = delay.get();
                return;
            }
        }
    }

    private boolean hasInventorySpace(PlayerEntity player) {
        for (ItemStack inventoryStack : player.getInventory().getMainStacks()) {
            if (inventoryStack.isEmpty()) {
                return true;
            }
        }

        return false;
    }
}
