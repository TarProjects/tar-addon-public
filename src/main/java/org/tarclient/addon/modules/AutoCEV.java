package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.HoleUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.ArrayList;
import java.util.List;

public class AutoCEV extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");
    private final SettingGroup sgBypass = this.settings.createGroup("Bypass", false);

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance of target")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Integer> maxSupport = sgDelay.add(new IntSetting.Builder()
        .name("max-support")
        .description("Maximum support for the block placements")
        .defaultValue(3)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Integer> placeDelay = sgDelay.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Block placement delay in ticks")
        .defaultValue(80)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> failDelay = sgDelay.add(new IntSetting.Builder()
        .name("fail-delay")
        .description("Block placement delay in ticks")
        .defaultValue(80)
        .sliderRange(0, 500)
        .build()
    );

    /* --- Delay --- */
    private final Setting<Integer> preDelay = sgDelay.add(new IntSetting.Builder()
        .name("pre-delay")
        .description("The delay before block place")
        .defaultValue(10)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<List<String>> preChat = sgDelay.add(new StringListSetting.Builder()
        .name("pre-chat")
        .description("Chat commands to send each tick in its own line")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> postDelay = sgDelay.add(new IntSetting.Builder()
        .name("post-delay")
        .description("The delay after placing block")
        .defaultValue(10)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<List<String>> postChat = sgDelay.add(new StringListSetting.Builder()
        .name("post-chat")
        .description("Chat commands to send each tick in its own line")
        .defaultValue("")
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private final Direction[] directions = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private PlayerEntity target;
    private int cooldown;
    private Stage stage = Stage.PRE;
    private boolean hasSent = false;

    public AutoCEV() {
        super(TarAddon.CATEGORY, "auto-cev", "Cevs opponents");
    }

    @Override
    public void onActivate() {
        placePositions.clear();
        target = null;
        cooldown = 0;
        stage = Stage.PRE;
        hasSent = false;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        FindItemResult obby = InvUtils.find(Items.OBSIDIAN);
        if (!obby.found()) {
            error("No obsidian!");
            this.toggle();
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = getTargetInHole(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) {
                error("No proper target!");
                this.toggle();
                return;
            }
        }


        if (!HoleUtils.isInHole(target.getBlockPos(), true)) {
            target = null;
            onActivate();
            return;
        }


        getBlockPlacePositions(target);
        if (placePositions.isEmpty()) {
            onActivate();
            cooldown = failDelay.get();
            return;
        }


        switch (stage) {
            case PRE:
                if (!hasSent) {
                    sendPre();
                    hasSent = true;
                    return;
                }
                stage = Stage.PLACE;
                hasSent = false;
            case PLACE:

                boolean placed = false;
                for (BlockPos pos : placePositions) {
                    TarBlockUtils.InteractRunnable callback = (bhr -> {
                        swapToOffhand(obby.slot());
                        float yaw = (float) Rotations.getYaw(bhr.getPos());
                        float pitch = (float) Rotations.getPitch(bhr.getPos());
                        sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
                        BlockUtils.interact(bhr, Hand.OFF_HAND, true);
                        swapToOffhand(obby.slot());
                    });
                    if (TarBlockUtils.place(pos, false, true, Blocks.OBSIDIAN, callback)) {
                        placed = true;
                        break;
                    }
                }

                if (!placed) {
                    sendPost();
                    onActivate();
                    cooldown = failDelay.get();
                    return;
                } else {
                    stage = Stage.POST;
                }
            case POST:
                if (!hasSent) {
                    sendPost();
                    hasSent = true;
                    return;
                }

                onActivate();
                cooldown = placeDelay.get();
                hasSent = false;
        }
    }

    private void sendPre() {
        for (String message : preChat.get()) {
            if (!message.isEmpty()) {
                ChatUtils.sendPlayerMsg(message, false);
            }
        }
    }

    private void sendPost() {
        for (String message : postChat.get()) {
            if (!message.isEmpty()) {
                ChatUtils.sendPlayerMsg(message, false);
            }
        }
    }

    private void getBlockPlacePositions(PlayerEntity target) {
        placePositions.clear();

        BlockPos center = target.getBlockPos().up();
        for (Direction direction : directions) {
            BlockPos pos = center.offset(direction);
            if (Math.sqrt(mc.player.squaredDistanceTo(pos.toCenterPos())) > range.get()) continue;
            placePositions.add(pos);
        }
    }

    private PlayerEntity getTargetInHole(double range, SortPriority priority) {
        if (!Utils.canUpdate()) return null;
        return (PlayerEntity) TargetUtils.get(entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isDead() || player.getHealth() <= 0) return false;
            if (!PlayerUtils.isWithin(entity, range)) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer) return !fakePlayer.noHit;
            if (!HoleUtils.isInHole(player.getBlockPos(), true)) return false;
            return EntityUtils.getGameMode(player) == GameMode.SURVIVAL;
        }, priority);

    }

    private void swapToOffhand(int slot) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 40, SlotActionType.SWAP, mc.player);
    }

    private enum Stage {
        PRE,
        PLACE,
        POST
    }
}
