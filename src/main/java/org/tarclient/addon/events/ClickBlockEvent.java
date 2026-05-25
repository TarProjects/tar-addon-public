/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.tarclient.addon.events;

import meteordevelopment.meteorclient.events.Cancellable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ClickBlockEvent extends Cancellable {
    private static final ClickBlockEvent INSTANCE = new ClickBlockEvent();

    public BlockPos blockPos;
    public Direction direction;

    public static ClickBlockEvent get(BlockPos blockPos, Direction direction) {
        INSTANCE.setCancelled(false);
        INSTANCE.blockPos = blockPos;
        INSTANCE.direction = direction;
        return INSTANCE;
    }
}
