package org.tarclient.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.BlockPosArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

public class SendMoveCommand extends Command {
    public SendMoveCommand() {
        super("send-move", "Sends a move packet");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("blockpos", BlockPosArgumentType.blockPos())
            .executes(context -> {
                if (mc.player == null || mc.getNetworkHandler() == null) return SINGLE_SUCCESS;
                BlockPos pos = BlockPosArgumentType.getBlockPos(context, "blockpos");
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.getX(), pos.getY(), pos.getZ(), false, false));

                return SINGLE_SUCCESS;
            }));
    }
}
