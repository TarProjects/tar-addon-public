package org.tarclient.addon.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import org.tarclient.addon.settings.TarSettingsWidgetFactory;

public class TarSettingsTheme extends MeteorGuiTheme {
    public TarSettingsTheme() {
        super();
        settingsFactory = new TarSettingsWidgetFactory(this);
    }
}
