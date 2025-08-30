package org.tarclient.addon.events;

import meteordevelopment.meteorclient.events.Cancellable;

public class PlayerJumpEvent extends Cancellable {
    private static final PlayerJumpEvent INSTANCE = new PlayerJumpEvent();

    public static PlayerJumpEvent get() {
        return INSTANCE;
    }
}
