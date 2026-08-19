package org.tarclient.addon.utils;

import com.peace.util.IRCBlockPos;
import com.peace.util.IRCItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class IRCUtils {
    public static IRCBlockPos blockPosToIrc(BlockPos pos) {
        if (pos == null) return null;
        return new IRCBlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos blockPosFromIrc(IRCBlockPos pos) {
        if (pos == null) return null;
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static IRCItemStack itemStackToIRC(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        return new IRCItemStack(id, stack.getCount(), stack.getDamage(), stack.getMaxDamage());
    }

    public static ItemStack itemStackFromIRC(IRCItemStack stack) {
        try {
            Item item = Registries.ITEM.get(Identifier.of(stack.getId()));
            ItemStack newStack = new ItemStack(item, stack.getCount());
            int damage = stack.getDamage();
            newStack.setDamage(damage);
            return newStack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
