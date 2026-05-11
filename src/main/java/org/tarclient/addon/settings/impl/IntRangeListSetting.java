package org.tarclient.addon.settings.impl;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import org.tarclient.addon.settings.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class IntRangeListSetting extends Setting<List<IntRange>> {

    public final int min, max;
    public final int sliderMin, sliderMax;

    public IntRangeListSetting(String name, String description, List<IntRange> defaultValue, Consumer<List<IntRange>> onChanged, Consumer<Setting<List<IntRange>>> onModuleActivated, IVisible visible, int min, int max, int sliderMin, int sliderMax) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.min = min;
        this.max = max;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
    }

    public static void fillTable(GuiTheme theme, WTable table, IntRangeListSetting setting) {
        table.clear();

        List<IntRange> list = setting.get();

        for (IntRange range : list) {
            WIntEdit minEdit = table.add(theme.intEdit(range.min, setting.min, setting.max, setting.sliderMin, setting.sliderMax, false)).expandX().widget();

            WIntEdit maxEdit = table.add(theme.intEdit(range.max, setting.min, setting.max, setting.sliderMin, setting.sliderMax, false)).expandX().widget();


            WMinus remove = table.add(theme.minus()).widget();

            table.row();

            Runnable apply = () -> {
                int newMin = minEdit.get();
                int newMax = maxEdit.get();

                range.min = Math.min(newMin, newMax);
                range.max = Math.max(newMin, newMax);

                setting.onChanged();
            };

            minEdit.action = apply;
            maxEdit.action = apply;

            minEdit.actionOnRelease = apply;
            maxEdit.actionOnRelease = apply;

            remove.action = () -> {
                list.remove(range);
                setting.set(list);
                fillTable(theme, table, setting);
            };
        }

        table.row();

        WButton add = table.add(theme.button("Add range"))
            .expandX()
            .widget();

        add.action = () -> {
            list.add(new IntRange(setting.min, setting.max));
            setting.set(list);
            fillTable(theme, table, setting);
        };

        WButton reset = table.add(theme.button("Reset")).widget();

        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };
    }

    @Override
    protected List<IntRange> parseImpl(String str) {
        List<IntRange> list = new ArrayList<>();

        if (str == null || str.isEmpty()) return list;

        String[] parts = str.split(",");

        for (String part : parts) {
            String[] split = part.trim().split("-");

            if (split.length != 2) continue;

            try {
                int a = Integer.parseInt(split[0].trim());
                int b = Integer.parseInt(split[1].trim());

                list.add(new IntRange(a, b));
            } catch (Exception ignored) {
            }
        }

        return list;
    }

    @Override
    protected boolean isValueValid(List<IntRange> value) {
        return value != null;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList listTag = new NbtList();

        for (IntRange range : value) {
            NbtCompound c = new NbtCompound();

            c.put("min", NbtInt.of(range.min));
            c.put("max", NbtInt.of(range.max));

            listTag.add(c);
        }

        tag.put("value", listTag);
        return tag;
    }

    @Override
    protected List<IntRange> load(NbtCompound tag) {
        get().clear();

        NbtList listTag = tag.getListOrEmpty("value");

        for (int i = 0; i < listTag.size(); i++) {
            try {
                NbtCompound c = listTag.getCompound(i).orElseThrow();

                int min = c.getInt("min").orElseThrow();
                int max = c.getInt("max").orElseThrow();

                get().add(new IntRange(min, max));
            } catch (Exception ignored) {
            }
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<IntRange>, IntRangeListSetting> {

        public int min = Integer.MIN_VALUE;
        public int max = Integer.MAX_VALUE;

        public int sliderMin = 0;
        public int sliderMax = 10;

        public Builder() {
            super(List.of(new IntRange(0, 10)));
        }

        public Builder min(int min) {
            this.min = min;
            return this;
        }

        public Builder max(int max) {
            this.max = max;
            return this;
        }

        public Builder range(int min, int max) {
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
            return this;
        }

        public Builder sliderMin(int min) {
            this.sliderMin = min;
            return this;
        }

        public Builder sliderMax(int max) {
            this.sliderMax = max;
            return this;
        }

        public Builder sliderRange(int min, int max) {
            this.sliderMin = min;
            this.sliderMax = max;
            return this;
        }

        @Override
        public IntRangeListSetting build() {
            return new IntRangeListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, min, max, sliderMin, sliderMax);
        }
    }
}
