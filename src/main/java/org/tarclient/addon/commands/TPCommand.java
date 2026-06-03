package org.tarclient.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;

import java.util.UUID;

public class TPCommand extends Command {
    public TPCommand() {
        super("tp", "Teleports to a player if you are in spectator mode");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("player")
            .then(argument("player-entry", PlayerListEntryArgumentType.create())
                .executes(context -> teleportTo(context.getArgument("player-entry", PlayerListEntry.class).getProfile().id()))));
        builder.then(literal("uuid")
            .then(argument("target-uuid", StringArgumentType.word())
                .executes(context -> {
                    try {
                        UUID uuid = UUID.fromString(StringArgumentType.getString(context, "target-uuid"));
                        return teleportTo(uuid);
                    } catch (IllegalArgumentException e) {
                        error("Failed to parse UUID");
                        return SINGLE_SUCCESS;
                    }
                })));
    }

    private int teleportTo(UUID uuid) {
        if (mc.interactionManager == null || mc.getNetworkHandler() == null) return SINGLE_SUCCESS;

        mc.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(uuid));
        return SINGLE_SUCCESS;
    }
}
