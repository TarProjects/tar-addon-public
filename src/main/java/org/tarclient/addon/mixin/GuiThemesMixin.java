package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.themes.TarSettingsTheme;

@Mixin(GuiThemes.class)
public class GuiThemesMixin {
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/GuiThemes;add(Lmeteordevelopment/meteorclient/gui/GuiTheme;)V"))
    private static void onGuiThemeInit(GuiTheme theme) {
        // override the default theme here
        GuiThemes.add(new TarSettingsTheme());
    }
}
