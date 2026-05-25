package org.tarclient.addon.events;

import meteordevelopment.meteorclient.events.Cancellable;

public class TarTickMovementEvent extends Cancellable {
    private static final TarTickMovementEvent INSTANCE = new TarTickMovementEvent();

    public static TarTickMovementEvent get() {
        return INSTANCE;
    }
}
