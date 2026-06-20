package org.tarclient.addon.utils;

public enum LagDetection {
    Ping,
    Sprint,
    Both;

    public boolean ping() {
        return this == Ping || this == Both;
    }

    public boolean sprint() {
        return this == Sprint || this == Both;
    }
}
