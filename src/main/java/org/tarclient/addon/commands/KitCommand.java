package org.tarclient.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

public class KitCommand extends Command {
    private static final String KITCHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private static final int MAX_KIT_LENGTH = 245;
    private static final ItemStackArgument PLACEHOLDER_ARG = new ItemStackArgument(null, null);
    public KitCommand() {
        super("kit", "CrystalPvP.cc kit utilities");
    }

    public static String generateRandomKitName(int length) {
        char[] ret = new char[length];
        for (int i = 0; i < length; i++) {
            ret[i] = KITCHARS.charAt((int) (Math.random() * KITCHARS.length()));
        }
        return new String(ret);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("create-random")
            .executes(this::createKitInternal)
            .then(argument("length", IntegerArgumentType.integer(0, MAX_KIT_LENGTH))
                .executes(this::createKitInternal)
                .then(argument("prefix", StringArgumentType.string())
                    .executes(this::createKitInternal)
                    .then(argument("item", ItemStackArgumentType.itemStack(REGISTRY_ACCESS))
                        .executes(this::createKitInternal)))));
    }

    private int createKitInternal(CommandContext<CommandSource> context) {
        int length = getOptionalArgument(context, "length", MAX_KIT_LENGTH);
        String prefix = getOptionalArgument(context, "prefix", "");
        ItemStackArgument itemStackArgument = getOptionalArgument(context, "item", PLACEHOLDER_ARG);

        String itemName = "";
        if (itemStackArgument != PLACEHOLDER_ARG) {
            try {
                Item item = itemStackArgument.createStack(1, false).getItem();
                itemName = Registries.ITEM.getId(item).getPath();
            } catch (CommandSyntaxException ignored) {
            }
        }
        createKit(length, prefix, itemName);
        return SINGLE_SUCCESS;
    }

    public void createKit(int length, String prefix, String item) {
        if (mc.getNetworkHandler() == null) return;
        int randomLength = length - prefix.length() - item.length();
        String itemName = "";
        if (!item.isEmpty()) {
            // we need to save space for a space 🔥
            randomLength--;
            itemName = " " + item;
        }
        if (randomLength < 0) {
            error("Length is too small!");
            return;
        }
        mc.getNetworkHandler().sendChatCommand("createukit " + prefix + generateRandomKitName(randomLength) + itemName);
    }

    @SuppressWarnings("unchecked")
    private <T> T getOptionalArgument(CommandContext<CommandSource> context, String name, T defaultValue) {
        try {
            return context.getArgument(name, (Class<T>) defaultValue.getClass());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
