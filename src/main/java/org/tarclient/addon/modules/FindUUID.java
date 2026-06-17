package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindUUID extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> isFinder = sgGeneral.add(new BoolSetting.Builder()
        .name("is-finder")
        .description("Is this module the finder, or the teleporter?")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("Username of person who the UUID will be sent to")
        .defaultValue("CCStatsBot")
        .visible(isFinder::get)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range of finding stuff")
        .defaultValue(10)
        .sliderRange(0, 50)
        .visible(isFinder::get)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entities to accept?")
        .defaultValue(EntityType.ITEM, EntityType.ENDER_PEARL, EntityType.FALLING_BLOCK)
        .visible(isFinder::get)
        .build()
    );

    private final Setting<Integer> entityAge = sgGeneral.add(new IntSetting.Builder()
        .name("entity-age")
        .description("Checks for min entity age")
        .defaultValue(2)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<List<String>> usernames = sgGeneral.add(new StringListSetting.Builder()
        .name("usernames")
        .description("People who to automatically accept teleports from")
        .defaultValue("CCStatsBot")
        .visible(() -> !isFinder.get())
        .build()
    );

    private static final Pattern whisperRegex = Pattern.compile("^(\\w+) says: (.+)");

    public FindUUID() {
        super(TarAddon.CATEGORY, "find-uuid", "Finds an uuid of an entity and sends it to the specified player");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.getNetworkHandler() == null || mc.world == null || mc.player == null) return;
        if (!isFinder.get()) return;

        double minDistance = -1;
        Entity closest = null;
        for (Entity entity : mc.world.getEntities()) {
            if (!valid(entity)) continue;

            double dist = mc.player.squaredDistanceTo(entity);
            if (dist > range.get() * range.get()) continue;

            if (minDistance == -1 || dist < minDistance) {
                info(entity.getName());
                info(entity.getType().getName());
                closest = entity;
                minDistance = dist;
            }
        }

        if (closest != null) {
            // Not player!
            info("Found entity with UUID: " + closest.getUuid());
            mc.getNetworkHandler().sendChatCommand("msg " + name.get() + " " + closest.getUuid());
            this.toggle();
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!Utils.canUpdate() || isFinder.get()) {
            return;
        }

        String message = event.getMessage().getString();

        Matcher whisperMatcher = whisperRegex.matcher(message);
        if (whisperMatcher.find()) {
            String username = whisperMatcher.group(1);
            String uuid = whisperMatcher.group(2);
            if (usernames.get().contains(username)) {
                try {
                    UUID parsed = UUID.fromString(uuid);
                    sendPacket(new SpectatorTeleportC2SPacket(parsed));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public boolean valid(Entity entity) {
        return entity.age >= entityAge.get() && entities.get().contains(entity.getType());
    }
}
