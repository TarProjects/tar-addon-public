package org.tarclient.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.orbit.EventHandler;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;
import java.util.Random;

public class AutoLobby extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<String>> coordinates = sgGeneral.add(new StringListSetting.Builder()
        .name("coordinates")
        .description("List of coordinates")
        .defaultValue("1,2,3")
        .build()
    );


    private final Setting<Integer> delayMin = sgGeneral.add(new IntSetting.Builder()
        .name("delay-min")
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> delayMax = sgGeneral.add(new IntSetting.Builder()
        .name("delay-max")
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> maxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-ticks")
        .description("Max ticks before switching coordinates")
        .sliderRange(600, 1000)
        .defaultValue(600)
        .build()
    );
    Random random = new Random();
    IBaritone baritone;
    int index = 0;
    int cooldown = 0;
    int timer = 0;

    public AutoLobby() {
        super(TarAddon.CATEGORY, "auto-lobby", "Walks around in the lobby. Incredibly tuff");
    }

    @Override
    public void onActivate() {
        index = 0;
        cooldown = 0;
        timer = 0;
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Override
    public void onDeactivate() {
        baritone.getPathingBehavior().cancelEverything();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (coordinates.get().isEmpty()) {
            return;
        }

        timer++;

        if (timer >= maxTicks.get()) {
            baritone.getPathingBehavior().cancelEverything();
            timer = 0;
            return;
        }

        if (baritone.getCustomGoalProcess().isActive()) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        boolean inBounds = (index >= 0) && (index < coordinates.get().size());
        if (!inBounds) index = 0;

        try {
            String[] split = coordinates.get().get(index).split(",");
            if (split.length != 3) {
                throw new IllegalStateException("Invalid split length!");
            }
            int x = Integer.parseInt(split[0]);
            int y = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
        } catch (Exception ignored) {
            index++;
            return;
        }

        index++;
        timer = 0;

        int min = Math.min(delayMin.get(), delayMax.get());
        int max = Math.max(delayMin.get(), delayMax.get());

        cooldown = random.nextInt(max + 1 - min) + min;
    }
}
