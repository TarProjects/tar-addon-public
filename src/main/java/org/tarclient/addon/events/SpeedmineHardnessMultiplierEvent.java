package org.tarclient.addon.events;


import meteordevelopment.meteorclient.events.Cancellable;

public class SpeedmineHardnessMultiplierEvent extends Cancellable {
    private static final SpeedmineHardnessMultiplierEvent INSTANCE = new SpeedmineHardnessMultiplierEvent();

    // r/namesoundalikes -> markiplier 🔥
    public float  multiplier;

    public static SpeedmineHardnessMultiplierEvent get() {
        INSTANCE.setCancelled(false);
        INSTANCE.multiplier = 1.0f;
        return INSTANCE;
    }
}

