package org.tarclient.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import org.tarclient.addon.utils.MioUtils;

public class MioCommand extends Command {
    public MioCommand() {
        super("mio", "Helper for mio");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("exec")
            .then(argument("commands", StringArgumentType.greedyString()).executes(context -> {
                String arg = context.getArgument("commands", String.class);
                String[] commands = arg.split(";");

                if (!MioUtils.mioCompatibility.enabled.get()) {
                    error("Mio compatibility not active!");
                    return SINGLE_SUCCESS;
                }

                for (String command : commands) {
                    String stripped = command.strip();
                    if (!stripped.isEmpty()) {
                        ChatUtils.sendPlayerMsg(MioUtils.mioCompatibility.mioPrefix.get() + stripped, false);
                    }
                }

                return SINGLE_SUCCESS;
            })));
    }
}
