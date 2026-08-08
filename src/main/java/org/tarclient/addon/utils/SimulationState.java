package org.tarclient.addon.utils;

import net.minecraft.util.math.Vec3d;

public class SimulationState {
    public Vec3d pos;
    public Vec3d vel;

    public SimulationState(Vec3d pos, Vec3d vel) {
        this.pos = pos;
        this.vel = vel;
    }
}
