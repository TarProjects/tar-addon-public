package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.ArrayList;
import java.util.List;

public class TickAligner extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetMS = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-ms")
        .description("Desired average delay between server packet and client tick (ms).")
        .defaultValue(25)
        .sliderRange(1, 50)
        .min(1)
        .build()
    );

    private final Setting<Double> tolerance = sgGeneral.add(new DoubleSetting.Builder()
        .name("tolerance")
        .description("+- target ms")
        .defaultValue(5.0)
        .sliderRange(0.5, 20)
        .min(0.1)
        .build()
    );

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("How often to apply correction")
        .defaultValue(20)
        .sliderRange(10, 60)
        .min(5)
        .build()
    );

    private long lastPacketTime = 0;
    private boolean gettingSample = false;
    private final List<Long> diffs = new ArrayList<>();
    private long intervalStartTime = 0;

    public TickAligner() {
        super(TarAddon.CATEGORY, "tick-aligner", "Periodically adjusts client tick speed to keep server‑client delay near a target.");
    }

    @Override
    public void onActivate() {
        diffs.clear();
        intervalStartTime = System.currentTimeMillis();
        gettingSample = false;
        resetTimer();
    }

    @Override
    public void onDeactivate() {
        resetTimer();
        diffs.clear();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerListS2CPacket) {
            long now = System.currentTimeMillis();
            if (!gettingSample) {
                lastPacketTime = now;
                gettingSample = true;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long now = System.currentTimeMillis();
        if (gettingSample) {
            long diff = now - lastPacketTime;
            gettingSample = false;
            diffs.add(diff);
        }

        long elapsed = now - intervalStartTime;
        if (elapsed >= interval.get() * 1000L) {
            evaluateAndApplyCorrection();
            intervalStartTime = System.currentTimeMillis();
            diffs.clear();
        }
    }

    private void evaluateAndApplyCorrection() {
        if (diffs.isEmpty()) {
            return;
        }

        long sum = 0;
        for (long d : diffs) sum += d;
        double avgDiff = (double) sum / diffs.size();

        info(String.format("Avg Diff: %.2fms over %d samples.", avgDiff, diffs.size()));

        double lower = targetMS.get() - tolerance.get();
        double upper = targetMS.get() + tolerance.get();

        if (avgDiff >= lower && avgDiff <= upper) return;

        double shift = avgDiff - targetMS.get();
        double targetTickDurationMs = 50.0 - shift;

        if (targetTickDurationMs < 5.0) targetTickDurationMs = 5.0;
        if (targetTickDurationMs > 100.0) targetTickDurationMs = 100.0;

        float timerMultiplier = (float) (50.0 / targetTickDurationMs);

        info(String.format("Out of range. Applying timer multiplier: %.3f", timerMultiplier));
        setTimerOverride(timerMultiplier);
    }

    private void setTimerOverride(float multiplier) {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(multiplier);
    }

    private void resetTimer() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(Timer.OFF);
    }
}
