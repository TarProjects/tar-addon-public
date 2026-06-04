package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class DuelChangeUtils {
    private static final Map<String, Set<BlockPos>> ARENA_CHANGES = new HashMap<>();

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(DuelChangeUtils.class);
    }

    @EventHandler
    private static void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || event.oldState.equals(event.newState)) return;

        BlockPos pos = event.pos;

        String arena = mc.world.getRegistryKey().getValue().getPath();
        if (arena == null || arena.isEmpty()) return;

        addChange(arena, pos);
    }

    public static Set<BlockPos> getArenaChanges(String name) {
        return ARENA_CHANGES.computeIfAbsent(name, k -> new HashSet<>());
    }

    public static Set<BlockPos> getArenaBlocks(String arena) {
        return Collections.unmodifiableSet(getArenaChanges(arena));
    }

    public static void addChange(String arena, BlockPos pos) {
        getArenaChanges(arena).add(pos);
    }

    public static boolean isAdjacent(BlockPos pos, Set<BlockPos> set) {
        return TarBlockUtils.isAdjacentToAny(pos, set);
    }

    public static void resetArena(String arena) {
        Set<BlockPos> set = ARENA_CHANGES.get(arena);
        if (set != null) {
            set.clear();
        }
    }

    public static void resetAll() {
        ARENA_CHANGES.clear();
    }
}
