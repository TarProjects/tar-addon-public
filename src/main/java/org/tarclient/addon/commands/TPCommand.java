package org.tarclient.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.world.GameMode;

public class TPCommand extends Command {
    public TPCommand() {
        super("tp", "Teleports to a player if you are in spectator mode");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("player", PlayerListEntryArgumentType.create()).executes(context -> {
            if (mc.interactionManager == null || mc.getNetworkHandler() == null) return SINGLE_SUCCESS;

            if (mc.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR) {
                error("You are not in spectator mode!");
                return SINGLE_SUCCESS;
            }

            mc.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(PlayerListEntryArgumentType.get(context).getProfile().id()));
            return SINGLE_SUCCESS;
        }));
    }
}
