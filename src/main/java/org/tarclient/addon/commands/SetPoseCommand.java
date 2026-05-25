package org.tarclient.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.EntityPose;

public class SetPoseCommand extends Command {
    public SetPoseCommand() {
        super("set-pose", "Sets player pose");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("pose", StringArgumentType.word())
            .suggests((context, suggestionsBuilder) -> {
                for (EntityPose pose : EntityPose.values()) {
                    suggestionsBuilder.suggest(pose.name().toLowerCase());
                }
                return suggestionsBuilder.buildFuture();
            })
            .executes(context -> {
                if (mc.player == null) return SINGLE_SUCCESS;

                String input = StringArgumentType.getString(context, "pose");

                EntityPose pose;
                try {
                    pose = EntityPose.valueOf(input.toUpperCase());
                } catch (IllegalArgumentException e) {
                    error("Invalid pose.");
                    return 0;
                }

                mc.player.setPose(pose);

                return SINGLE_SUCCESS;
            }));
    }
}
