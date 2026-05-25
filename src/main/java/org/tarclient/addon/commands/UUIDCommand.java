package org.tarclient.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class UUIDCommand extends Command {
    public UUIDCommand() {
        super("uuid", "Finds an another entity's UUID");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("from-player")
            .then(argument("player", PlayerArgumentType.create()).executes(context -> {
                PlayerEntity player = PlayerArgumentType.get(context);
                info("Profile UUID: " + player.getGameProfile().id());
                return SINGLE_SUCCESS;
            })));
        builder.then(literal("from-crosshair")
            .executes(commandContext -> {
                HitResult result = mc.crosshairTarget;
                if (result instanceof EntityHitResult entityHitResult) {
                    info(entityHitResult.getEntity().getUuidAsString());
                }
                return SINGLE_SUCCESS;
            }));
    }
}
