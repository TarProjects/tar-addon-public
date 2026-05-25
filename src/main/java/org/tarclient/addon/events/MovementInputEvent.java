package org.tarclient.addon.events;

import meteordevelopment.meteorclient.events.Cancellable;
import net.minecraft.util.PlayerInput;

public class MovementInputEvent extends Cancellable {
    private static final MovementInputEvent INSTANCE = new MovementInputEvent();

    public PlayerInput input;

    public static MovementInputEvent get(PlayerInput input) {
        INSTANCE.setCancelled(false);
        INSTANCE.input = input;
        return INSTANCE;
    }
}
