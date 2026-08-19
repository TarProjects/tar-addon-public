package org.tarclient.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.peace.packets.c2s.PrivateMessageC2SPacket;
import com.peace.packets.c2s.RequestPlayerInventoryC2SPacket;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import org.tarclient.addon.modules.IRCModule;

public class IRCCommand extends Command {
    public IRCCommand() {
        super("irc", "Handles irc stuff");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("msg")
                .then(argument("player-entry", PlayerListEntryArgumentType.create())
                    .then(argument("message", StringArgumentType.greedyString()).executes(context -> {
                        PlayerListEntry entry = context.getArgument("player-entry", PlayerListEntry.class);
                        String target = entry.getProfile().name();
                        String message = context.getArgument("message", String.class);

                        IRCModule module = getIrc();
                        if (!module.isActive() || module.ircClient == null) {
                            error("IRC not active!");
                            return SINGLE_SUCCESS;
                        }
                        module.ircClient.sendPacket(new PrivateMessageC2SPacket(target, message));

                        return SINGLE_SUCCESS;
                    }))
                ));

        builder.then(literal("list").executes(context -> {
            IRCModule module = getIrc();

            if (!module.isActive() || module.ircClient == null) {
                error("IRC not active!");
                return SINGLE_SUCCESS;
            }

            int count = module.onlineIRCUsers.size();
            info("Total IRC users: " + count);
            info(String.join(", ", module.onlineIRCUsers));
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("inventory")
            .then(argument("player-entry", PlayerListEntryArgumentType.create()).executes(context -> {
                    PlayerListEntry entry = context.getArgument("player-entry", PlayerListEntry.class);
                    String target = entry.getProfile().name();

                    IRCModule module = getIrc();
                    if (!module.isActive() || module.ircClient == null) {
                        error("IRC not active!");
                        return SINGLE_SUCCESS;
                    }

                    module.ircClient.sendPacket(new RequestPlayerInventoryC2SPacket(target));

                    return SINGLE_SUCCESS;
                })
            ));
    }

    private IRCModule getIrc() {
        IRCModule module = Modules.get().get(IRCModule.class);
        if (module == null) throw new NullPointerException("IRCModule not initialized!");
        return module;
    }
}
