package org.tarclient.addon.modules;

import com.peace.client.IRCClientEventHandler;
import com.peace.client.IRCClientMain;
import com.peace.packets.c2s.BreakingC2SPacket;
import com.peace.packets.c2s.ChatC2SPacket;
import com.peace.packets.c2s.SeenEntityC2SPacket;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.ColorUtils;
import org.tarclient.addon.utils.MiningUtils;
import org.tarclient.addon.utils.RenderUtils;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IRCModule extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgBlockBreaking = settings.createGroup("block-breaking");
    private final SettingGroup sgNametags = settings.createGroup("nametags");

    private final Setting<String> serverAddress = sgGeneral.add(new StringSetting.Builder()
        .name("server-address")
        .description("IRC server address to connect to.")
        .defaultValue("127.0.0.1")
        .build()
    );

    private final Setting<Integer> seenInterval = sgGeneral.add(new IntSetting.Builder()
        .name("seen-interval")
        .description("Interval to send seen packets at in ticks")
        .defaultValue(40)
        .sliderRange(10, 100)
        .min(10)
        .max(100)
        .build()
    );

    private final Setting<Integer> port = sgGeneral.add(new IntSetting.Builder()
        .name("port")
        .description("IRC server port")
        .defaultValue(8080)
        .noSlider()
        .min(1)
        .max(65535)
        .build()
    );

    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("IRC server password")
        .defaultValue("")
        .build()
    );

    private final Setting<String> ircPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("irc-prefix")
        .description("IRC chat prefix. Empty prefix means a message will never be sent into irc")
        .defaultValue("++")
        .build()
    );

    /* --- Block Breaking --- */
    private final Setting<Boolean> blockBreaking = sgGeneral.add(new BoolSetting.Builder()
        .name("block-breaking")
        .description("Renders block breaking progresses")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgBlockBreaking.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<RenderUtils.BreakAnimation> animation = sgBlockBreaking.add(new EnumSetting.Builder<RenderUtils.BreakAnimation>()
        .name("animation")
        .description("What animation to use on render")
        .defaultValue(RenderUtils.BreakAnimation.Grow)
        .build()
    );

    private final Setting<SettingColor> startSideColor = sgBlockBreaking.add(new ColorSetting.Builder()
        .name("start-side-color")
        .description("The side color of the target box rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> startLineColor = sgBlockBreaking.add(new ColorSetting.Builder()
        .name("start-line-color")
        .description("The line color of the target box rendering.")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );

    private final Setting<SettingColor> endSideColor = sgBlockBreaking.add(new ColorSetting.Builder()
        .name("end-side-color")
        .description("The side color of the target box rendering.")
        .defaultValue(new SettingColor(0, 255, 0, 70))
        .build()
    );

    private final Setting<SettingColor> endLineColor = sgBlockBreaking.add(new ColorSetting.Builder()
        .name("end-line-color")
        .description("The line color of the target box rendering.")
        .defaultValue(new SettingColor(0, 255, 0))
        .build()
    );

    /* --- Nametags --- */
    private final Setting<Boolean> nametags = sgGeneral.add(new BoolSetting.Builder()
        .name("nametags")
        .description("Renders nametags that are far away using the IRC")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDistance = sgGeneral.add(new IntSetting.Builder()
        .name("min-distance")
        .description("Minimum distance to render nametags at")
        .defaultValue(47)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render nametags at")
        .defaultValue(200)
        .sliderRange(1, 300)
        .build()
    );

    private final Setting<Double> scale = sgNametags.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the nametag.")
        .defaultValue(1.1)
        .min(0.1)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgNametags.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Background color of the nametag.")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .build()
    );

    private final Setting<SettingColor> nameColor = sgNametags.add(new ColorSetting.Builder()
        .name("name-color")
        .description("Color of the player's name.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> distanceColor = sgNametags.add(new ColorSetting.Builder()
        .name("distance-color")
        .description("Color of the distance text.")
        .defaultValue(new SettingColor(150, 150, 150))
        .build()
    );

    private final Map<String, Breaking> breakingMap = new ConcurrentHashMap<>();
    private final Map<String, BlockPos> positionMap = new ConcurrentHashMap<>();
    boolean disabling;

    int tickCounter;

    IRCClientMain ircClient;

    public IRCModule() {
        super(TarAddon.CATEGORY, "irc-module", "Allows you to share information with friends through a IRC server");
    }

    @Override
    public void onActivate() {
        System.out.println("activate called :)");
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.isInSingleplayer() || mc.getNetworkHandler().getServerInfo() == null) {
            System.out.println("Issue with singleplayer");
            error("IRC does not work in singleplayer");
            this.toggle();
            return;
        }

        tickCounter = 0;
        breakingMap.clear();
        ircClient = new IRCClientMain(serverAddress.get(), port.get(), mc.player.getName().getString(), password.get(), mc.getNetworkHandler().getServerInfo().address, new IRCEventHandler(this));
        try {
            ircClient.start();
        } catch (Exception exception) {
            error("Error with connecting!");
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        disabling = true;
        if (ircClient != null) ircClient.disconnect();
        disabling = false;
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (mc.player == null) return;
        if (!blockBreaking.get()) return;

        for (Breaking breaking : breakingMap.values()) {
            if (mc.player.squaredDistanceTo(breaking.blockPos.toCenterPos()) > 16*16) continue;
            float progress = breaking.progress;
            Color line = ColorUtils.lerp(startLineColor.get(), endLineColor.get(), progress);
            Color side = ColorUtils.lerp(startSideColor.get(), endSideColor.get(), progress);


            event.renderer.box(RenderUtils.getBox(breaking.blockPos, progress, animation.get()), side, line, shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        if (!nametags.get()) return;

        TextRenderer text = TextRenderer.get();
        boolean shadow = true;


        for (Map.Entry<String, BlockPos> player : positionMap.entrySet()) {
            String name = player.getKey();
            BlockPos blockPos = player.getValue();

            double dist = mc.player.getEntityPos().squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            if (dist > minDistance.get()*minDistance.get() && dist < maxDistance.get()*maxDistance.get()) {
                Vector3d pos = new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                pos.add(0, 1.5, 0);

                if (!NametagUtils.to2D(pos, scale.get())) continue;

                NametagUtils.begin(pos, event.drawContext);

                String distanceText = String.format(" %.1fm", Math.sqrt(dist));
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

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        sendEntities(mc.world);
        sendBreaking();
        tickCounter++;
    }

    private void sendEntities(ClientWorld world) {
        if (tickCounter % seenInterval.get() != 0) return;
        long now = System.currentTimeMillis();
        for (PlayerEntity entity : world.getPlayers()) {
            BlockPos pos = entity.getBlockPos();
            ircClient.sendPacket(new SeenEntityC2SPacket(entity.getName().getString(), fromMinecraft(pos), now));
        }
    }

    private void sendBreaking() {
        BlockPos mining = MiningUtils.getBreakingBlockPos();
        BreakingC2SPacket packet = new BreakingC2SPacket(fromMinecraft(mining), (float) MiningUtils.getBreakingProgress());
        ircClient.sendPacket(packet);
    }

    private com.peace.util.BlockPos fromMinecraft(BlockPos pos) {
        if (pos == null) return null;
        return new com.peace.util.BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private BlockPos toMinecraft(com.peace.util.BlockPos pos) {
        if (pos == null) return null;
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @EventHandler
    private void onChatSend(SendMessageEvent event) {
        String prefix = ircPrefix.get().strip();
        if (prefix.isEmpty()) return;
        if (event.message.startsWith(prefix)) {
            ircClient.sendPacket(new ChatC2SPacket(event.message.substring(prefix.length()).strip()));
            event.cancel();
        }
    }

    private class IRCEventHandler implements IRCClientEventHandler {
        IRCModule module;
        public IRCEventHandler(IRCModule module) {
            this.module = module;
        }

        @Override
        public void onProgressUpdate(IRCClientMain main, String username, com.peace.util.@Nullable BlockPos pos, float breakingProgress) {
            if (pos == null) {
                breakingMap.remove(username);
            } else {
                // breaking something
                BlockPos blockPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
                breakingMap.put(username, new Breaking(blockPos, breakingProgress));
            }
        }

        @Override
        public void onServerMessage(IRCClientMain ircClientMain, String s) {
            mc.execute(() -> info(s));
        }

        @Override
        public void onIrcChat(IRCClientMain ircClientMain, String username, String message) {
            mc.execute(() -> info(username + ": " + message));
        }

        @Override
        public void onPositionReceive(IRCClientMain main, String username, com.peace.util.BlockPos position) {
            if (position == null) {
                positionMap.remove(username);
            } else {
                positionMap.put(username, toMinecraft(position));
            }
        }

        @Override
        public void tick(IRCClientMain ircClientMain) {

        }

        @Override
        public void onKick(IRCClientMain main, String reason) {
            mc.execute(() -> info("Kicked: " + reason));
        }

        @Override
        public void onDisconnect(IRCClientMain main) {
            System.out.println("DISCONNECT!");
            if (module.isActive() && !disabling) module.toggle();
        }
    }

    public record Breaking(BlockPos blockPos, float progress){}
}
