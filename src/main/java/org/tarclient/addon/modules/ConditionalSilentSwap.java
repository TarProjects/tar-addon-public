package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.MioUtils;

import java.util.List;

/**
 * @concept hekt
 * @author nullable
 */
public class ConditionalSilentSwap extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<String>> switchToOtherCommands = sgGeneral.add(new StringListSetting.Builder()
        .name("switch-to-totem-commands")
        .description("Commands to run when switching to totems items")
        .defaultValue("autocrystal autoswap silent", "autocrystal PlaceDelay 175")
        .build()
    );

    private final Setting<List<String>> switchToCrystalCommands = sgGeneral.add(new StringListSetting.Builder()
        .name("switch-to-crystal-commands")
        .description("Commands to run when switching to crystals")
        .defaultValue("autocrystal autoswap none", "autocrystal PlaceDelay 0")
        .build()
    );

    private boolean lastHadTotems;

    public ConditionalSilentSwap() {
        super(TarAddon.CATEGORY, "conditional-silent-swap", "Switches mio settings to silent swap when crystals are missing");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

        lastHadTotems = hasTotems(mc.player);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean currentlyHasTotems = hasTotems(mc.player);

        if (!lastHadTotems && currentlyHasTotems) {
            // no tots -> tots
            for (String command : switchToOtherCommands.get()) {
                if (command.isEmpty()) continue;
                MioUtils.sendMioMessage(command);
            }
        }

        if (lastHadTotems && !currentlyHasTotems) {
            // tots -> no tots
            for (String command : switchToCrystalCommands.get()) {
                if (command.isEmpty()) continue;
                MioUtils.sendMioMessage(command);
            }
        }

        lastHadTotems = currentlyHasTotems;
    }

    private boolean hasTotems(PlayerEntity player) {
        return player.getOffHandStack().copy().getItem() == Items.TOTEM_OF_UNDYING;
    }
}
