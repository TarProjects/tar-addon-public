package org.tarclient.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import org.tarclient.addon.utils.DuelChangeUtils;

public class ResetChangesCommand extends Command {
    public ResetChangesCommand() {
        super("resetchanges", "Resets tracked block changes for duel arenas.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("all").executes(ctx -> {
            DuelChangeUtils.resetAll();
            info("Cleared all changes");
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("arena", StringArgumentType.string()).executes(ctx -> {
            String arena = ctx.getArgument("arena", String.class);
            DuelChangeUtils.resetArena(arena);
            info("Cleared changes for: " + arena);
            return SINGLE_SUCCESS;
        }));
    }
}
