package org.tarclient.addon.events;


import meteordevelopment.meteorclient.events.Cancellable;

public class MioPauseSpeedmineEvent extends Cancellable {
    private static final MioPauseSpeedmineEvent INSTANCE = new MioPauseSpeedmineEvent();

    public static MioPauseSpeedmineEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}

