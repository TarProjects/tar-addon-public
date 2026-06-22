package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.mixin.PlayerListHudAccessor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArenaReset extends TarModule {
    private static final Pattern UPTIME_PATTERN = Pattern.compile("uptime\\s+((?:\\d+w\\s*)?(?:\\d+d\\s*)?(?:\\d+h\\s*)?(?:\\d+m\\s*)?(?:\\d+s)?)");
    private static final String SERVER_ARENA_CLEANUP = "{SERVER} SERVER FFA ARENA IS CLEARING";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> warn = sgGeneral.add(new BoolSetting.Builder()
        .name("warn")
        .description("Warns the user before arena reset on 2min, 1min, 30sec, 10 second countdown")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> offsetSec = sgGeneral.add(new IntSetting.Builder()
        .name("offset")
        .description("Offset by seconds")
        .defaultValue(75)
        .sliderRange(0, 1800)
        .visible(warn::get)
        .build()
    );
    private final Setting<Boolean> setAir = sgGeneral.add(new BoolSetting.Builder()
        .name("set-air")
        .description("Sets nearby blocks to air on reset")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> setAirWait = sgGeneral.add(new IntSetting.Builder()
        .name("set-air-wait")
        .description("Ticks waited after received message before setting air")
        .defaultValue(30)
        .sliderRange(0, 60)
        .build()
    );
    private boolean received;
    private int ticks;

    private boolean warn5min, warn2min, warn1min, warn30s, warn15s;
    private int lastAnnounced;

    public ArenaReset() {
        super(TarAddon.CATEGORY, "arena-reset", "Handles arena resets better than cc itself");
    }

    public static long parseFromFooter(String footer) {
        Matcher matcher = UPTIME_PATTERN.matcher(footer);
        if (!matcher.find()) return 0;

        String timePart = matcher.group(1);
        int weeks = 0, days = 0, hours = 0, minutes = 0, seconds = 0;

        Matcher weekMatcher = Pattern.compile("(\\d+)w").matcher(timePart);
        if (weekMatcher.find()) weeks = Integer.parseInt(weekMatcher.group(1));

        Matcher dayMatcher = Pattern.compile("(\\d+)d").matcher(timePart);
        if (dayMatcher.find()) days = Integer.parseInt(dayMatcher.group(1));

        Matcher hourMatcher = Pattern.compile("(\\d+)h").matcher(timePart);
        if (hourMatcher.find()) hours = Integer.parseInt(hourMatcher.group(1));

        Matcher minuteMatcher = Pattern.compile("(\\d+)m").matcher(timePart);
        if (minuteMatcher.find()) minutes = Integer.parseInt(minuteMatcher.group(1));

        Matcher secondMatcher = Pattern.compile("(\\d+)s").matcher(timePart);
        if (secondMatcher.find()) seconds = Integer.parseInt(secondMatcher.group(1));

        return weeks * 604800L + days * 86400L + hours * 3600L + minutes * 60L + seconds;
    }

    @Override
    public void onActivate() {
        received = false;
        ticks = 0;
        resetWarnings();
    }

    private void resetWarnings() {
        warn5min = warn2min = warn1min = warn30s = warn15s = false;
        lastAnnounced = -1;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.world == null) return;

        if (received) {
            ticks++;

            if (ticks >= setAirWait.get()) {
                int cleanUpCount = 0;

                // maybe multithread? chunks are mutable though, would need to save them,
                // and then it would be useless to run multiple threads
                for (Chunk chunk : Utils.chunks()) {
                    ChunkPos chunkPos = chunk.getPos();

                    int startX = chunkPos.getStartX();
                    int startZ = chunkPos.getStartZ();
                    int endX = chunkPos.getEndX();
                    int endZ = chunkPos.getEndZ();

                    for (int x = startX; x <= endX; x++) {
                        for (int z = startZ; z <= endZ; z++) {
                            for (int y = 0; y <= 56; y++) {
                                BlockPos blockPos = new BlockPos(x, y, z);
                                BlockState blockState = chunk.getBlockState(blockPos);
                                // air, light, bedrock checks
                                if (blockState.getBlock() != Blocks.AIR && blockState.getBlock() != Blocks.LIGHT && blockState.getBlock() != Blocks.BEDROCK) {
                                    // no-render flag for this and somehow force rendering at the end? IDK
                                    chunk.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE);
                                    cleanUpCount++;
                                }
                            }
                        }
                    }
                }

                info(String.format("Cleaned up %d blocks!", cleanUpCount));
                received = false;
                ticks = 0;
            }
        }

        if (warn.get()) {
            Text footer = ((PlayerListHudAccessor) mc.inGameHud.getPlayerListHud()).tar$getFooter();
            if (footer == null) return;
            String uptimeStr = footer.getString();
            if (uptimeStr.isEmpty()) return;

            long uptimeSeconds = parseFromFooter(uptimeStr);
            long secondsLeft = getSecondsUntilReset(uptimeSeconds);

            // 1 hour
            if (secondsLeft >= 60 * 60) {
                resetWarnings();
                return;
            }

            if (!warn5min && secondsLeft <= 300) {
                info("5 minutes until reset");
                warn5min = true;
            }
            if (!warn2min && secondsLeft <= 120) {
                info("2 minutes until reset");
                warn2min = true;
            }
            if (!warn1min && secondsLeft <= 60) {
                info("1 minute until reset");
                warn1min = true;
            }
            if (!warn30s && secondsLeft <= 30) {
                info("30 seconds until reset");
                warn30s = true;
            }
            if (!warn15s && secondsLeft <= 15) {
                info("15 seconds until reset");
                warn15s = true;
            }

            if (0 < secondsLeft && secondsLeft <= 10) {
                if (secondsLeft != lastAnnounced) {
                    info(String.format("Reset in %d seconds!", secondsLeft));
                    lastAnnounced = Math.toIntExact(secondsLeft);
                }
            }
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();

        if (setAir.get() && message.strip().equals(SERVER_ARENA_CLEANUP)) {
            received = true;
            ticks = 0;
        }
    }

    private long getSecondsUntilReset(long uptimeSec) {
        long normalized = Math.max(0, uptimeSec - offsetSec.get());
        long remainder = normalized % 7200;
        return remainder == 0 ? 0 : 7200 - remainder;
    }
}
