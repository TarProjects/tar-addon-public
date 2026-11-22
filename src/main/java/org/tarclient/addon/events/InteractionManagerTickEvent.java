package org.tarclient.addon.events;

public class InteractionManagerTickEvent {
    private static final InteractionManagerTickEvent INSTANCE = new InteractionManagerTickEvent();

    public static InteractionManagerTickEvent get() {
        return INSTANCE;
    }
}
