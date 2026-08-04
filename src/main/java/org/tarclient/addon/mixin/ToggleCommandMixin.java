package org.tarclient.addon.mixin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.commands.arguments.ModuleArgumentType;
import meteordevelopment.meteorclient.commands.commands.ToggleCommand;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

@Mixin(ToggleCommand.class)
public class ToggleCommandMixin {
    @Inject(method = "build", at = @At("RETURN"))
    private void tar$addSilentToggling(LiteralArgumentBuilder<CommandSource> builder, CallbackInfo ci) {
        builder.then(LiteralArgumentBuilder.<CommandSource>literal("silent")
            .then(RequiredArgumentBuilder.<CommandSource, Module>argument("module", ModuleArgumentType.create())
                .executes(context -> {
                    Module m = ModuleArgumentType.get(context);
                    boolean feedback = m.chatFeedback;
                    m.chatFeedback = false;
                    m.toggle();
                    m.chatFeedback = feedback;
                    return SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSource>literal("on")
                    .executes(context -> {
                        Module m = ModuleArgumentType.get(context);
                        boolean feedback = m.chatFeedback;
                        m.chatFeedback = false;
                        m.enable();
                        m.chatFeedback = feedback;
                        return SINGLE_SUCCESS;
                    }))
                .then(LiteralArgumentBuilder.<CommandSource>literal("off")
                    .executes(context -> {
                        Module m = ModuleArgumentType.get(context);
                        boolean feedback = m.chatFeedback;
                        m.chatFeedback = false;
                        m.disable();
                        m.chatFeedback = feedback;
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );
    }
}
