package org.tarclient.addon.mixin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.commands.arguments.ModuleArgumentType;
import meteordevelopment.meteorclient.commands.arguments.SettingArgumentType;
import meteordevelopment.meteorclient.commands.arguments.SettingValueArgumentType;
import meteordevelopment.meteorclient.commands.commands.SettingCommand;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

@Mixin(SettingCommand.class)
public class SettingCommandMixin {
    @Inject(method = "build", at = @At("RETURN"))
    private void tar$addSilentSettings(LiteralArgumentBuilder<CommandSource> builder, CallbackInfo ci) {
        builder.then(LiteralArgumentBuilder.<CommandSource>literal("silent")
            .then(RequiredArgumentBuilder.<CommandSource, Module>argument("module", ModuleArgumentType.create())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("setting", SettingArgumentType.create())
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("value", SettingValueArgumentType.create())
                        .executes(context -> {
                            Setting<?> setting = SettingArgumentType.get(context);
                            String value = SettingValueArgumentType.get(context);

                            setting.parse(value);

                            return SINGLE_SUCCESS;
                        })))));

    }
}
