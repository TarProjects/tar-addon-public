package org.tarclient.addon.modules;

import com.peace.client.IRCClientEventHandler;
import com.peace.client.IRCClientMain;
import com.peace.packets.c2s.*;
import com.peace.packets.s2c.IRCUsersS2CPacket;
import com.peace.util.IRCBlockPos;
import com.peace.util.IRCEquipment;
import com.peace.util.IRCInventory;
import com.peace.util.IRCItemStack;
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
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.SendTypedMessageEvent;
import org.tarclient.addon.utils.*;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final Setting<Boolean> sendBreaking = sgGeneral.add(new BoolSetting.Builder()
        .name("send-block-breaking")
        .description("Sends the current block breaking position and progress to server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sendSeen = sgGeneral.add(new BoolSetting.Builder()
        .name("send-seen")
        .description("Sends seen entities to the server with entity data. Do not use this if you dont want your location to be known.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> seenInterval = sgGeneral.add(new IntSetting.Builder()
        .name("seen-interval")
        .description("Interval to send seen packets at in ticks")
        .defaultValue(40)
        .sliderRange(1, 100)
        .min(1)
        .max(100)
        .visible(sendSeen::get)
        .build()
    );

    private final Setting<Boolean> shareInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("share-inventory")
        .description("Shares inventory on request")
        .defaultValue(true)
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

    private final Setting<Boolean> overrideMsg = sgBlockBreaking.add(new BoolSetting.Builder()
        .name("override-msg")
        .description("Overrides the /msg command if both users are in the IRC")
        .defaultValue(true)
        .build()
    );

    /* --- Block Breaking --- */
    private final Setting<Boolean> blockBreaking = sgBlockBreaking.add(new BoolSetting.Builder()
        .name("block-breaking")
        .description("Renders block breaking progresses")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> range = sgBlockBreaking.add(new IntSetting.Builder()
        .name("range")
        .description("Range of checking for block breaking")
        .defaultValue(16)
        .sliderRange(1, 32)
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
    private final Setting<Boolean> nametags = sgNametags.add(new BoolSetting.Builder()
        .name("nametags")
        .description("Renders nametags that are far away using the IRC")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderHealth = sgNametags.add(new BoolSetting.Builder()
        .name("render-health")
        .description("Renders health to the nametags")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDistance = sgNametags.add(new IntSetting.Builder()
        .name("min-distance")
        .description("Minimum distance to render nametags at")
        .defaultValue(47)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> maxDistance = sgNametags.add(new IntSetting.Builder()
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

    private static final Pattern MSG_REGEX = Pattern.compile("^/(?:w|msg) (\\w+) (.+)");

    public final Set<String> onlineIRCUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Breaking> breakingMap = new ConcurrentHashMap<>();
    private final Map<String, PlayerData> playerMap = new ConcurrentHashMap<>();
    boolean disabling;

    int tickCounter;

    public IRCClientMain ircClient;

    public IRCModule() {
        super(TarAddon.CATEGORY, "irc-module", "Allows you to share information with friends through a IRC server");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.isInSingleplayer() || mc.getNetworkHandler().getServerInfo() == null) {
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
            if (mc.player.squaredDistanceTo(breaking.blockPos.toCenterPos()) > range.get() * range.get()) continue;
            float progress = breaking.progress;
            Color line = ColorUtils.lerp(startLineColor.get(), endLineColor.get(), progress);
            Color side = ColorUtils.lerp(startSideColor.get(), endSideColor.get(), progress);

            event.renderer.box(RenderUtils.getBreakingAnimationBox(breaking.blockPos, progress, animation.get()), side, line, shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        if (!nametags.get()) return;

        TextRenderer text = TextRenderer.get();
        boolean shadow = true;


        for (Map.Entry<String, PlayerData> player : playerMap.entrySet()) {
            String name = player.getKey();
            BlockPos blockPos = player.getValue().pos();

            // horiz distance
            double dist = mc.player.getEntityPos().squaredDistanceTo(blockPos.getX(), mc.player.getY(), blockPos.getZ());

            if (dist > minDistance.get()*minDistance.get() && dist < maxDistance.get()*maxDistance.get()) {
                Vector3d pos = new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                pos.add(0, 1.5, 0);

                if (!NametagUtils.to2D(pos, scale.get())) continue;

                NametagUtils.begin(pos, event.drawContext);

                String distanceText = String.format(" %.1fm", Math.sqrt(dist));

                float health = -1;
                if (player.getValue().health != null) health = player.getValue().health();

                String healthText = String.format(" %.1f", health);

                double nameWidth = text.getWidth(name, shadow);
                double distWidth = text.getWidth(distanceText, shadow);
                double healthWidth = text.getWidth(healthText, shadow);

                double totalWidth = nameWidth + distWidth;

                if (renderHealth.get()) totalWidth += healthWidth;

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
                if (health != -1 && renderHealth.get()) x = text.render(healthText, x, y, Color.PINK, shadow);
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
        if (!sendSeen.get()) return;
        if (tickCounter % seenInterval.get() != 0) return;
        for (PlayerEntity entity : world.getPlayers()) {
            BlockPos pos = entity.getBlockPos();
            ircClient.sendPacket(new SeenEntityC2SPacket(entity.getName().getString(), IRCUtils.blockPosToIrc(pos), entity.getHealth() + entity.getAbsorptionAmount(), IRCUtils.entityToIRCEquipment(entity)));
        }
    }

    private void sendBreaking() {
        if (!sendBreaking.get()) return;
        BlockPos mining = MiningUtils.getBreakingBlockPos();
        BreakingC2SPacket packet = new BreakingC2SPacket(IRCUtils.blockPosToIrc(mining), (float) MiningUtils.getBreakingProgress());
        ircClient.sendPacket(packet);
    }

    @EventHandler
    private void onMessageSend(SendMessageEvent event) {
        String prefix = ircPrefix.get().strip();
        if (prefix.isEmpty()) return;
        if (event.message.startsWith(prefix)) {
            ircClient.sendPacket(new ChatC2SPacket(event.message.substring(prefix.length()).strip()));
            event.cancel();
        }
    }

    @EventHandler
    private void onChatSend(SendTypedMessageEvent event) {
        if (overrideMsg.get()) {
            Matcher matcher = MSG_REGEX.matcher(event.message);
            if (matcher.find()) {
                String target = matcher.group(1);
                String message = matcher.group(2);

                if (onlineIRCUsers.contains(target)) {
                    ircClient.sendPacket(new PrivateMessageC2SPacket(target, message));

                    event.cancel();
                }
            }
        }
    }

    private class IRCEventHandler implements IRCClientEventHandler {
        IRCModule module;
        public IRCEventHandler(IRCModule module) {
            this.module = module;
        }

        @Override
        public void postLogin(IRCClientMain main) {
            mc.execute(() -> info("Logged in as %s", main.getUsername()));
        }

        @Override
        public void onProgressUpdate(IRCClientMain main, String username, @Nullable IRCBlockPos pos, float breakingProgress) {
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
            mc.execute(() -> info("Server message: " + s));
        }

        @Override
        public void onIrcChat(IRCClientMain ircClientMain, String username, String message) {
            mc.execute(() -> info("<%s> %s", username, message));
        }

        @Override
        public void onPrivateMessage(IRCClientMain main, String sender, String message, boolean isOwnMessage) {
            String msg = isOwnMessage ? "to %s: %s" : "%s says: %s";
            mc.execute(() -> info(msg, sender, message));
        }

        @Override
        public void onReceiveInventory(IRCClientMain main, String username, IRCInventory inventory) {
            mc.execute(() -> {
                if (mc.player == null) return;
                OtherPlayerInventoryHandler handler = new OtherPlayerInventoryHandler(ScreenHandlerType.GENERIC_9X6, 0, mc.player.getInventory(), module.toInventory(inventory), 6);
                OtherPlayerInventory invScreen = new OtherPlayerInventory(handler, mc.player.getInventory(), Text.of("IRC inventory: " + username));
                mc.setScreen(invScreen);
            });
        }

        @Override
        public void onServerRequestInventory(IRCClientMain main, int id) {
            mc.execute(() -> {
                if (mc.player == null) return;
                if (!shareInventory.get()) return;

                Map<Integer, IRCItemStack> stackMap = new HashMap<>();

                for (int i = 0; i < mc.player.getInventory().size(); i++) {
                    // loop through inv
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.isEmpty()) continue;
                    stackMap.put(i, IRCUtils.itemStackToIRC(stack));
                }

                main.sendPacket(new SendPlayerInventoryC2SPacket(id, new IRCInventory(stackMap)));
            });
        }

        @Override
        public void onIRCUserUpdate(IRCClientMain main, List<String> usernames, IRCUsersS2CPacket.Action action, boolean shouldAnnounce) {
            if (action.equals(IRCUsersS2CPacket.Action.Add)) {
                onlineIRCUsers.addAll(usernames);
                if (shouldAnnounce) {
                    mc.execute(() -> {
                        for (String user : usernames) {
                            info(user + " has joined the IRC");
                        }
                    });
                }
            }
            if (action.equals(IRCUsersS2CPacket.Action.Remove)) {
                usernames.forEach((username) -> {
                    onlineIRCUsers.remove(username);
                    breakingMap.remove(username);
                });

                if (shouldAnnounce) {
                    mc.execute(() -> {
                        for (String user : usernames) {
                            info(user + " has left the IRC");
                        }
                    });
                }
            }
        }

        @Override
        public void onPositionReceive(IRCClientMain main, String username, @Nullable IRCBlockPos position, @Nullable Float health, @Nullable IRCEquipment equipment) {
            if (position == null) {
                playerMap.remove(username);
            } else {
                BlockPos pos = IRCUtils.blockPosFromIrc(position);
                playerMap.put(username, new PlayerData(pos, health));
            }
        }

        @Override
        public void onKick(IRCClientMain main, String reason) {
            mc.execute(() -> info("Kicked: " + reason));
        }

        @Override
        public void onDisconnect(IRCClientMain main) {
            if (module.isActive() && !disabling) {
                mc.execute(() -> info("Disconnected, toggling!"));
                module.toggle();
            }
        }
    }

    private SimpleInventory toInventory(IRCInventory ircInventory) {
        if (mc.player == null) return null;
        try {
            SimpleInventory inventory = new SimpleInventory(9 * 6);

            // reverse scan to prevent out of bounds
            for (int i = 0; i <= mc.player.getInventory().size(); i++) {
                IRCItemStack stack = ircInventory.getItemStackMap().get(i);
                if (stack == null) continue;
                ItemStack itemStack = IRCUtils.itemStackFromIRC(stack);
                if (itemStack != null) {
                    inventory.setStack(i, itemStack);
                }
            }
            return inventory;
        } catch (Exception e) {
            return null;
        }
    }

    public record Breaking(BlockPos blockPos, float progress){}
    public record PlayerData(BlockPos pos, Float health){}
}
