package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class SpectatorCamera extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Vertical movement speed.")
        .defaultValue(1)
        .sliderRange(0, 5)
        .build()
    );
    private boolean upPressed, downPressed;
    private double offset = 0;
    private double prevOffset = 0;

    public SpectatorCamera() {
        super(TarAddon.CATEGORY, "spectator-camera", "Modifies camera Y level. Used for spectating players while staying above a certain Y");
    }

    @Override
    public void onActivate() {
        offset = 0;
        prevOffset = 0;

        upPressed = Input.isPressed(mc.options.jumpKey);
        downPressed = Input.isPressed(mc.options.sneakKey);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        prevOffset = offset;
        if (upPressed) offset += speed.get();
        if (downPressed) offset -= speed.get();
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (mc.options.jumpKey.matchesKey(event.input)) {
            upPressed = event.action != KeyAction.Release;
            event.cancel();
        } else if (mc.options.sneakKey.matchesKey(event.input)) {
            downPressed = event.action != KeyAction.Release;
            event.cancel();
        }
    }

    public double getOffset(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevOffset, offset);
    }
}
