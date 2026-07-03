package org.tarclient.addon.events;

import meteordevelopment.meteorclient.events.Cancellable;

public class SendTypedMessageEvent extends Cancellable {
    private static final SendTypedMessageEvent INSTANCE = new SendTypedMessageEvent();

    public String message;

    public static SendTypedMessageEvent get(String message) {
        INSTANCE.setCancelled(false);
        INSTANCE.message = message;
        return INSTANCE;
    }
}


