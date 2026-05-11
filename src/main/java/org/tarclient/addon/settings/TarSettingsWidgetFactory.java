package org.tarclient.addon.settings;

import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

public class TarSettingsWidgetFactory extends DefaultSettingsWidgetFactory {
    public TarSettingsWidgetFactory(GuiTheme theme) {
        super(theme);
        factories.put(IntRangeListSetting.class, (((table, setting) -> intRangeListW(table, (IntRangeListSetting) setting))));
    }

    private void intRangeListW(WTable table, IntRangeListSetting setting) {
        WTable wTable = table.add(theme.table()).expandX().widget();
        IntRangeListSetting.fillTable(theme, wTable, setting);
    }
}
