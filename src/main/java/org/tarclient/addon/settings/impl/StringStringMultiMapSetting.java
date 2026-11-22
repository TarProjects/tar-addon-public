package org.tarclient.addon.settings.impl;

import com.google.common.collect.Multimap;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class StringStringMultiMapSetting extends Setting<List<Map.Entry<String, String>>> {
    public final Class<? extends WTextBox.Renderer> renderer;
    public final CharFilter filter;

    public StringStringMultiMapSetting(String name, String description, List<Map.Entry<String, String>> defaultValue, Consumer<List<Map.Entry<String, String>>> onChanged, Consumer<Setting<List<Map.Entry<String, String>>>> onModuleActivated, IVisible visible, Class<? extends WTextBox.Renderer> renderer, CharFilter filter) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.renderer = renderer;
        this.filter = filter;
    }

    @Override
    protected List<Map.Entry<String, String>> parseImpl(String str) {
        // no parsing impl yet!
        return null;
    }

    @Override
    protected boolean isValueValid(List<Map.Entry<String, String>> value) {
        return true;
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList keys = new NbtList();
        NbtList values = new NbtList();

        for (int i = 0; i < this.value.size(); i++) {
            keys.add(i, NbtString.of(this.value.get(i).getKey()));
            values.add(i, NbtString.of(this.value.get(i).getValue()));
        }

        tag.put("keys", keys);
        tag.put("values", values);

        return tag;
    }

    @Override
    protected List<Map.Entry<String, String>> load(NbtCompound tag) {
        get().clear();

        NbtList keys = tag.getListOrEmpty("keys");
        NbtList values = tag.getListOrEmpty("values");

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.getString(i, "");
            String value = values.getString(i, "");
            get().add(Map.entry(key, value));
        }

        return get();
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    public static void fillTable(GuiTheme theme, WTable table, StringStringMultiMapSetting setting) {
        table.clear();

        ArrayList<Map.Entry<String, String>> strings = new ArrayList<>(setting.get());
        CharFilter filter = setting.filter == null ? (str, ch) -> true : setting.filter;

        for (int i = 0; i < setting.get().size(); i++) {
            int msgI = i;
            Map.Entry<String, String> entry = setting.get().get(i);

            WTextBox keyBox = table.add(theme.textBox(entry.getKey(), filter, setting.renderer)).expandX().widget();
            WTextBox valueBox = table.add(theme.textBox(entry.getValue(), filter, setting.renderer)).expandX().widget();

            keyBox.action = () -> strings.set(msgI, Map.entry(keyBox.get(), valueBox.get()));
            keyBox.actionOnUnfocused = () -> setting.set(strings);

            valueBox.action = () -> strings.set(msgI, Map.entry(keyBox.get(), valueBox.get()));
            valueBox.actionOnUnfocused = () -> setting.set(strings);

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                strings.remove(msgI);
                setting.set(strings);

                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!setting.get().isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton add = table.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            strings.add(Map.entry("", ""));
            setting.set(strings);

            fillTable(theme, table, setting);
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();

            fillTable(theme, table, setting);
        };
        reset.tooltip = "Reset";
    }

    public static class Builder extends SettingBuilder<Builder, List<Map.Entry<String, String>>, StringStringMultiMapSetting> {
        private Class<? extends WTextBox.Renderer> renderer;
        private CharFilter filter;

        public Builder() {
            super(new ArrayList<>(0));
        }

        @SafeVarargs
        public final Builder defaultValue(Map.Entry<String, String>... defaults) {
            return defaultValue(defaults != null ? Arrays.asList(defaults) : new ArrayList<>());
        }

        public Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder filter(CharFilter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        public StringStringMultiMapSetting build() {
            return new StringStringMultiMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer, filter);
        }
    }
}
