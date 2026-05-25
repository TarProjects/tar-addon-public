package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.socket.infinitenametags.TeleporterServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfiniteNameTagsTeleporter extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> port = sgGeneral.add(new StringSetting.Builder()
        .name("port")
        .description("Port for hosting...")
        .defaultValue("25575")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between teleporting")
        .defaultValue(1)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> forceTrackCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("force-track-cooldown")
        .description("How many ticks to track")
        .defaultValue(100)
        .sliderRange(0, 200)
        .build()
    );

    public InfiniteNameTagsTeleporter() {
        super(TarAddon.CATEGORY, "infinite-nametags-teleporter", "Infinite nametags!");
    }

    private TeleporterServer server;
    private final CopyOnWriteArrayList<UUID> tracked = new CopyOnWriteArrayList<>();
    private UUID forceTrack;
    private int forceTrackTicks;
    private int cooldown;
    private int index;

    @Override
    public void onActivate() {
        tracked.clear();
        index = 0;
        cooldown = 0;

        try {
            int connectionPort = Integer.parseInt(port.get());
            server = new TeleporterServer(connectionPort, this);
        } catch (NumberFormatException e) {
            error("Failed to format port!");
            this.toggle();
            return;
        }

        Thread serverThread = new Thread(() -> {
            try {
                while (server.running()) {
                    server.acceptClient();
                    server.readLoop();
                    // readloop done, client has exited!

                    // constantly repeat this: do action -> when client exits clear everything
                    server.close();
                    tracked.clear();
                    index = 0;
                    cooldown = 0;
                }

            } catch (IOException e) {
                error("Failed to connect!");
                if (this.isActive()) {
                    this.toggle();
                }
            }
        });

        serverThread.start();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            this.toggle();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.toggle();
    }

    @Override
    public void onDeactivate() {
        server.stop();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (forceTrack == null) {
            if (tracked.isEmpty()) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }

            boolean inBounds = (index >= 0) && (index < tracked.size());
            if (!inBounds) index = 0;

            UUID uuid = tracked.get(index);

            mc.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(uuid));
            cooldown = delay.get();

            index++;
        } else {
            if (forceTrackTicks > 0) {
                mc.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(forceTrack));

                forceTrackTicks--;
            } else {
                forceTrack = null;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (packet.getEntityType() == EntityType.PLAYER && !packet.getUuid().equals(mc.player.getUuid())) {
                if (packet.getY() > 100) {
                    // shitty failsafe because of shitty delays...
                    mc.execute(() -> handleRemoveTrack(packet.getUuid()));
                    return;
                }
                server.sendUUIDXYZ(packet.getUuid(), packet.getX(), packet.getY(), packet.getZ());
                // iterative addition
                mc.execute(() -> handleAddTrack(packet.getUuid()));
            }
        }

        if (event.packet instanceof PlayerRemoveS2CPacket(java.util.List<UUID> profileIds)) {
            for (UUID uuid : profileIds) {
                List<PlayerListEntry> playerList = new ArrayList<>(mc.getNetworkHandler().getPlayerList());
                if (playerList.stream().anyMatch((playerListEntry) -> playerListEntry != null && playerListEntry.getProfile().id() == uuid)) {
                    // actually exists lol
                    mc.execute(() -> handleRemoveTrack(uuid));
                }
            }
        }

        // death status (3)
        if (event.packet instanceof EntityStatusS2CPacket packet && packet.getStatus() == 3) {
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) {
                mc.execute(() -> handleRemoveTrack(entity.getUuid()));
            }
        }

    }

    private final static Pattern killedSelfPattern = Pattern.compile("^(\\w+)\\((\\d+)\\) (died|blew up|commited suicide|fell out of the world|was killed|was destroyed).*");
    private final static Pattern killedWithEloPattern = Pattern.compile("^(\\w+)\\((\\d+)\\) .* (\\w+)\\((\\d+)\\).*");
    private final static Pattern killedByBowPattern = Pattern.compile("^(\\w*)\\((\\d*)\\) was shot by (\\w*) using (.*)");


    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        Matcher killedSelf = killedSelfPattern.matcher(msg);

        if (killedSelf.matches()) {
            String killed = killedSelf.group(1);

            UUID uuid = findFromName(killed);
            if (uuid != null) {
                handleRemoveTrack(uuid);
            }
        }

        Matcher killedWithElo = killedWithEloPattern.matcher(msg);
        if (killedWithElo.matches()) {
            String died = killedWithElo.group(1);

            UUID uuid = findFromName(died);
            if (uuid != null) {
                handleRemoveTrack(uuid);
            }
        }

        Matcher killedByBow = killedByBowPattern.matcher(msg);

        if (killedByBow.matches()) {
            String died = killedByBow.group(1);

            UUID uuid = findFromName(died);
            if (uuid != null) {
                handleRemoveTrack(uuid);
            }
        }
    }

    private UUID findFromName(String name) {
        if (mc.getNetworkHandler() == null || mc.player == null) return null;
        if (name.equals(mc.player.getName().getString())) return null;

        List<PlayerListEntry> playerList = new ArrayList<>(mc.getNetworkHandler().getPlayerList());
        for (PlayerListEntry entry : playerList) {
            if (entry == null) continue;
            if (Objects.equals(entry.getProfile().name(), name)) {
                return entry.getProfile().id();
            }
        }

        return null;
    }

    public void handleAddTrack(UUID uuid) {
        if (!server.isConnected() || !server.running()) return;
        tracked.addIfAbsent(uuid);
    }

    public void handleRemoveTrack(UUID uuid) {
        if (!server.isConnected() || !server.running()) return;
        tracked.remove(uuid);
    }

    public void handleForceTrack(UUID uuid) {
        forceTrack = uuid;
        forceTrackTicks = forceTrackCooldown.get();
    }
}
