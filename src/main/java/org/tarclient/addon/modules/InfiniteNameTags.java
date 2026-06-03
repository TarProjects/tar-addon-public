package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.joml.Vector3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.socket.infinitenametags.WatcherClient;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class InfiniteNameTags extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<String> address = sgGeneral.add(new StringSetting.Builder()
        .name("address")
        .description("Address for connecting")
        .defaultValue("127.0.0.1")
        .build()
    );

    private final Setting<String> port = sgGeneral.add(new StringSetting.Builder()
        .name("port")
        .description("Port for server...")
        .defaultValue("25575")
        .build()
    );

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the nametag.")
        .defaultValue(1.1)
        .min(0.1)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Background color of the nametag.")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .build()
    );

    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder()
        .name("name-color")
        .description("Color of the player's name.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> distanceColor = sgRender.add(new ColorSetting.Builder()
        .name("distance-color")
        .description("Color of the distance text.")
        .defaultValue(new SettingColor(150, 150, 150))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );


    public InfiniteNameTags() {
        super(TarAddon.CATEGORY, "infinite-nametags", "Infinite nametags!");
    }

    private WatcherClient client;
    private Thread clientThread;
    private final CopyOnWriteArrayList<TargetPlayer> list = new CopyOnWriteArrayList<>();

    @Override
    public void onActivate() {
        list.clear();
        try {
            int connectionPort = Integer.parseInt(port.get());
            client = new WatcherClient(address.get(), connectionPort, this);
        } catch (NumberFormatException e) {
            error("Failed to format port!");
            this.toggle();
            return;
        }

        clientThread = new Thread(() -> {
            try {
                client.connect();
                client.readLoop();
            } catch (IOException e) {
                error("Failed to connect!");
                if (this.isActive()) {
                    this.toggle();
                }
            }
        });

        clientThread.start();
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

    public void handleUUIDXYZ(UUID uuid, double x, double y, double z) {
        if (mc.getNetworkHandler() == null) return;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().id().equals(uuid)) {
                String name = entry.getProfile().name();

                // remove old names
                list.removeIf(player -> Objects.equals(player.name, name));

                list.add(new TargetPlayer(name, x, y, z));
                return;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        for (TargetPlayer player : list) {
            if (player.tickCounter < 50) {
                if (mc.player.squaredDistanceTo(player.x, player.y, player.z) > 47 * 47) {
                    double x = player.x;
                    double y = player.y;
                    double z = player.z;

                    double min_x = x - 0.6 / 2;
                    double max_x = x + 0.6 / 2;

                    double max_y = y + 1.6;

                    double min_z = z - 0.6 / 2;
                    double max_z = z + 0.6 / 2;
                    event.renderer.box(min_x, y, min_z, max_x, max_y, max_z, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        TextRenderer text = TextRenderer.get();
        boolean shadow = true;


        for (TargetPlayer player : list) {
            if (player.tickCounter < 50) {
                double dist = Math.sqrt(mc.player.squaredDistanceTo(player.x, player.y, player.z));

                if (dist > 47) {
                    Vector3d pos = new Vector3d(player.x, player.y, player.z);
                    pos.add(0, 1.5, 0);

                    if (!NametagUtils.to2D(pos, scale.get())) continue;

                    NametagUtils.begin(pos, event.drawContext);

                    String name = player.name;


                    String distanceText = String.format(" %.1fm", dist);
                    double nameWidth = text.getWidth(name, shadow);
                    double distWidth = text.getWidth(distanceText, shadow);
                    double totalWidth = nameWidth + distWidth;
                    double height = text.getHeight(shadow);

                    // Background
                    Renderer2D.COLOR.begin();
                    Renderer2D.COLOR.quad(-totalWidth / 2 - 1, -height - 1, totalWidth + 2, height + 2, backgroundColor.get());
                    Renderer2D.COLOR.render();

                    // Text
                    text.beginBig();
                    double x = -totalWidth / 2;
                    double y = -height;
                    x = text.render(name, x, y, nameColor.get(), shadow);
                    text.render(distanceText, x, y, distanceColor.get(), shadow);
                    text.end();

                    NametagUtils.end(event.drawContext);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        list.removeIf(player -> player.tickCounter >= 50);

        for (TargetPlayer player : list) {
            player.tickCounter++;
        }

        if (Objects.equals(mc.world.getRegistryKey().getValue().getPath(), "overworld")) {
            for (PlayerEntity entity : mc.world.getPlayers()) {
                if (entity.getY() < 100 && entity.isAlive()) {
                    client.addTrack(entity.getUuid());
                }
            }
        }
    }



    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;
        if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (packet.getEntityType() != EntityType.ENDER_PEARL) return;
            // track own pearls
            if (packet.getEntityData() == mc.player.getId()) {
                client.forceTrack(packet.getUuid());
            }
        }
    }



    @Override
    public void onDeactivate() {
        if (client != null) {
            client.stop();
        }
    }

    private static class TargetPlayer {
        String name;
        double x;
        double y;
        double z;
        int tickCounter;

        public TargetPlayer(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tickCounter = 0;
        }
    }
}
