package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.modules.disabler.DisablerMode;
import org.tarclient.addon.modules.disabler.GrimAirPlaceRotationDisabler;

public class Disabler  extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Why are you reading this")
        .defaultValue(Mode.GrimAirPlaceRotation)
        .onChanged(this::updateCurrentMode)
        .onModuleActivated(setting -> this.updateCurrentMode(setting.get()))
        .build()
    );

    public Disabler() {
        super(TarAddon.CATEGORY, "disabler", "Disables anticheats/their features");
        updateCurrentMode(mode.get());
    }

    DisablerMode currentMode;

    protected BlockPos storedBlockPos;
    protected BlockState storedBlockState;

    public void updateCurrentMode(Mode mode) {
        switch (mode) {
            case GrimAirPlaceRotation -> currentMode = new GrimAirPlaceRotationDisabler(mc, this);
        }
    }

    public BlockPos getStoredBlockPos() {
        return storedBlockPos;
    }

    public BlockState getStoredBlockState() {
        return storedBlockState;
    }

    public void store(BlockPos pos, BlockState state) {
        this.storedBlockPos = pos;
        this.storedBlockState = state;
    }

    public void clear() {
        this.storedBlockPos = null;
        this.storedBlockState = null;
    }

    @Override
    public void onActivate() {
        currentMode.onActivate();
    }

    @Override
    public void onDeactivate() {
        currentMode.onDeactivate();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        currentMode.onPacketSend(event);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        currentMode.onPacketReceive(event);
    }


    public enum Mode {
        GrimAirPlaceRotation
    }
}
