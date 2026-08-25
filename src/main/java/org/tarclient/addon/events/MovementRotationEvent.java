package org.tarclient.addon.events;


public class MovementRotationEvent {
    private static final MovementRotationEvent INSTANCE = new MovementRotationEvent();

    public float yaw;
    public float pitch;

    public static MovementRotationEvent get(float yaw, float pitch) {
        INSTANCE.yaw = yaw;
        INSTANCE.pitch = pitch;
        return INSTANCE;
    }
}

