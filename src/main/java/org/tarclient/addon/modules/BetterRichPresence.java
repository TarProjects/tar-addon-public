/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.tarclient.addon.modules;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.starscript.Script;
import net.minecraft.util.Util;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.ArrayList;
import java.util.List;

public class BetterRichPresence extends TarModule {
    private static final RichPresence rpc = new RichPresence();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> appid = sgGeneral.add(new StringSetting.Builder()
        .name("application-id")
        .description("DO NOT APPEND \"L\" TO THIS STRING!")
        .defaultValue("835240968533049424")
        .build()
    );

    private final SettingGroup sgLine1 = settings.createGroup("Line 1");
    private final SettingGroup sgLine2 = settings.createGroup("Line 2");

    // Line 1
    private final Setting<Integer> line1UpdateDelay = sgLine1.add(new IntSetting.Builder()
        .name("line-1-update-delay")
        .description("How fast to update the first line in ticks.")
        .defaultValue(200)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );
    private final Setting<SelectMode> line1SelectMode = sgLine1.add(new EnumSetting.Builder<SelectMode>()
        .name("line-1-select-mode")
        .description("How to select messages for the first line.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );    private final Setting<List<String>> line1Strings = sgLine1.add(new StringListSetting.Builder()
        .name("line-1-messages")
        .description("Messages used for the first line.")
        .defaultValue("{player}", "{server}")
        .onChanged(strings -> recompileLine1())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );
    private final Setting<Integer> line2UpdateDelay = sgLine2.add(new IntSetting.Builder()
        .name("line-2-update-delay")
        .description("How fast to update the second line in ticks.")
        .defaultValue(60)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );
    // Line 2
    private final Setting<SelectMode> line2SelectMode = sgLine2.add(new EnumSetting.Builder<SelectMode>()
        .name("line-2-select-mode")
        .description("How to select messages for the second line.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );
    private final SettingGroup sgImage1 = settings.createGroup("Image 1");
    private final SettingGroup sgImage2 = settings.createGroup("Image 2");    private final Setting<List<String>> line2Strings = sgLine2.add(new StringListSetting.Builder()
        .name("line-2-messages")
        .description("Messages used for the second line.")
        .defaultValue("Meteor on Crack!", "{round(server.tps, 1)} TPS", "Playing on {server.difficulty} difficulty.", "{server.player_count} Players online")
        .onChanged(strings -> recompileLine2())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );
    private final Setting<Integer> image1UpdateDelay = sgImage1.add(new IntSetting.Builder()
        .name("image-1-update-delay")
        .description("How fast to update the first image in ticks.")
        .defaultValue(200)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );
    private final Setting<SelectMode> image1SelectMode = sgImage1.add(new EnumSetting.Builder<SelectMode>()
        .name("image-1-select-mode")
        .description("How to select messages for the first image.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );
    private final Setting<Integer> image2UpdateDelay = sgImage2.add(new IntSetting.Builder()
        .name("image-2-update-delay")
        .description("How fast to update the second image in ticks.")
        .defaultValue(200)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );
    private final Setting<SelectMode> image2SelectMode = sgImage2.add(new EnumSetting.Builder<SelectMode>()
        .name("image-2-select-mode")
        .description("How to select messages for the second image.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );
    private final List<Script> line1Scripts = new ArrayList<>();
    private final List<Script> line2Scripts = new ArrayList<>();    private final Setting<List<String>> image1Strings = sgImage1.add(new StringListSetting.Builder()
        .name("image-1-pair")
        .description("Pairs used for the first image.")
        .defaultValue("key,text")
        .onChanged(strings -> recompileImage1())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );
    private final List<Script> image1Scripts = new ArrayList<>();
    private final List<Script> image2Scripts = new ArrayList<>();
    private boolean forceUpdate;
    private int line1Ticks, line1I;    private final Setting<List<String>> image2Strings = sgImage2.add(new StringListSetting.Builder()
        .name("image-2-pair")
        .description("Pairs used for the second image.")
        .defaultValue("key,text")
        .onChanged(strings -> recompileImage2())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );
    private int line2Ticks, line2I;
    private int image1Ticks, image1I;
    private int image2Ticks, image2I;
    public BetterRichPresence() {
        super(TarAddon.CATEGORY, "better-rich-presence", "Displays your presence on Discord.");

        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        try {
            DiscordIPC.start(Long.parseLong(appid.get()), null);
        } catch (NumberFormatException e) {
            this.toggle();
            return;
        }

        rpc.setStart(System.currentTimeMillis() / 1000L);

        recompileLine1();
        recompileLine2();
        recompileImage1();
        recompileImage2();

        line1Ticks = 0;
        line2Ticks = 0;
        image1Ticks = 0;
        image2Ticks = 0;
        line1I = 0;
        line2I = 0;
        image1I = 0;
        image2I = 0;
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
    }

    private void recompile(List<String> messages, List<Script> scripts) {
        scripts.clear();

        for (String message : messages) {
            Script script = MeteorStarscript.compile(message);
            if (script != null) scripts.add(script);
        }

        forceUpdate = true;
    }

    private void recompileLine1() {
        recompile(line1Strings.get(), line1Scripts);
    }

    private void recompileLine2() {
        recompile(line2Strings.get(), line2Scripts);
    }

    private void recompileImage1() {
        recompile(image1Strings.get(), image1Scripts);
    }

    private void recompileImage2() {
        recompile(image2Strings.get(), image2Scripts);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean update = false;

        // Image 1
        if (image1Ticks >= image1UpdateDelay.get() || forceUpdate) {
            if (!image1Scripts.isEmpty()) {
                int i = Utils.random(0, image1Scripts.size());
                if (image1SelectMode.get() == SelectMode.Sequential) {
                    if (image1I >= image1Scripts.size()) image1I = 0;
                    i = image1I++;
                }

                String[] message = MeteorStarscript.run(image1Scripts.get(i)).split(",");
                if (message.length == 2) {
                    String first = message[0];
                    String second = message[1];
                    if (first != null && second != null) rpc.setLargeImage(first, second);
                }
            }
            update = true;

            image1Ticks = 0;
        } else image1Ticks++;

        // Image 2
        if (image2Ticks >= image2UpdateDelay.get() || forceUpdate) {
            if (!image2Scripts.isEmpty()) {
                int i = Utils.random(0, image2Scripts.size());
                if (image2SelectMode.get() == SelectMode.Sequential) {
                    if (image2I >= image2Scripts.size()) image2I = 0;
                    i = image2I++;
                }

                String[] message = MeteorStarscript.run(image2Scripts.get(i)).split(",");
                if (message.length == 2) {
                    String first = message[0];
                    String second = message[1];
                    if (first != null && second != null) rpc.setSmallImage(first, second);
                }
            }
            update = true;

            image2Ticks = 0;
        } else image2Ticks++;


        // Line 1
        if (line1Ticks >= line1UpdateDelay.get() || forceUpdate) {
            if (!line1Scripts.isEmpty()) {
                int i = Utils.random(0, line1Scripts.size());
                if (line1SelectMode.get() == SelectMode.Sequential) {
                    if (line1I >= line1Scripts.size()) line1I = 0;
                    i = line1I++;
                }

                String message = MeteorStarscript.run(line1Scripts.get(i));
                if (message != null) rpc.setDetails(message);
            }
            update = true;

            line1Ticks = 0;
        } else line1Ticks++;

        // Line 2
        if (line2Ticks >= line2UpdateDelay.get() || forceUpdate) {
            if (!line2Scripts.isEmpty()) {
                int i = Utils.random(0, line2Scripts.size());
                if (line2SelectMode.get() == SelectMode.Sequential) {
                    if (line2I >= line2Scripts.size()) line2I = 0;
                    i = line2I++;
                }

                String message = MeteorStarscript.run(line2Scripts.get(i));
                if (message != null) rpc.setState(message);
            }
            update = true;

            line2Ticks = 0;
        } else line2Ticks++;


        // Update
        if (update) DiscordIPC.setActivity(rpc);
        forceUpdate = false;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton help = theme.button("Open documentation.");
        help.action = () -> Util.getOperatingSystem().open("https://github.com/MeteorDevelopment/meteor-client/wiki/Starscript");

        return help;
    }

    public enum SelectMode {
        Random,
        Sequential
    }










}
