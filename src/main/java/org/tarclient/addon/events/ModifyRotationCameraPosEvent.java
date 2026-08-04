package org.tarclient.addon.events;


import net.minecraft.util.math.Vec3d;

public class ModifyRotationCameraPosEvent {
    private static final ModifyRotationCameraPosEvent INSTANCE = new ModifyRotationCameraPosEvent();

    // Feet position, standing eye height not included!
    public Vec3d pos;

    public static ModifyRotationCameraPosEvent get(Vec3d pos) {
        INSTANCE.pos = pos;
        return INSTANCE;
    }
}

