package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.HoleUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import static org.tarclient.addon.utils.MiningUtils.attackWithCompatibility;
import static org.tarclient.addon.utils.MiningUtils.getLastBreaking;
import static org.tarclient.addon.utils.MioUtils.disableAttackingModules;
import static org.tarclient.addon.utils.MioUtils.enableAttackingModules;

public class AutoCEV extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");
    private static final Direction[] DIRECTIONS = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

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
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<Integer> placeDelay = sgDelay.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Block placement delay in ticks")
        .defaultValue(80)
        .sliderRange(0, 100)
        .build()
    );
    private final Setting<Integer> failDelay = sgDelay.add(new IntSetting.Builder()
        .name("fail-delay")
        .description("Block placement delay in ticks")
        .defaultValue(20)
        .sliderRange(0, 100)
        .build()
    );
    private final Setting<Integer> globalDelay = sgDelay.add(new IntSetting.Builder()
        .name("global-delay")
        .description("Global delay before re-search and everything")
        .defaultValue(5)
        .sliderRange(0, 10)
        .build()
    );
    /* --- Delay --- */
    private final Setting<Integer> preDelay = sgDelay.add(new IntSetting.Builder()
        .name("pre-delay")
        .description("The delay before block place")
        .defaultValue(1)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Integer> postDelay = sgDelay.add(new IntSetting.Builder()
        .name("post-delay")
        .description("The delay after placing block")
        .defaultValue(0)
        .sliderRange(0, 20)
        .build()
    );
    /* --- Render --- */
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the target box rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the target box rendering.")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );
    private BlockPos startPos = null;
    private BlockPos placePosition = null;
    private PlayerEntity target;
    private int globalCooldown;
    private int cooldown;
    private Stage stage = Stage.PRE;
    private int stageTicks = 0;

    public AutoCEV() {
        super(TarAddon.CATEGORY, "auto-cev", "Cevs opponents");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }
        reset();

        startPos = mc.player.getBlockPos();
        placePosition = null;
        target = null;
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        globalCooldown = 0;
        cooldown = 0;
        setStageTo(Stage.PRE);

        enableAttackingModules();
    }

    private void fail() {
        reset();
        placePosition = null;
        cooldown = failDelay.get();
        globalCooldown = globalDelay.get();
    }

    private void successDelay() {
        cooldown = placeDelay.get();
        globalCooldown = globalDelay.get();
    }

    private void setStageTo(Stage stage) {
        this.stage = stage;
        stageTicks = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (placePosition != null) {
            event.renderer.box(placePosition, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onBlockAttack(StartBreakingBlockEvent event) {
        if (placePosition != null && !event.blockPos.equals(placePosition)) {
            // re-search placeposition on tickpre
            placePosition = null;
            cooldown = 0;
        }
    }

    @EventHandler
    private void onSlotSwitch(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            info("Switched item, waiting delay");
            cooldown = placeDelay.get();
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        if (mc.player == null) return;

        if (!startPos.equals(mc.player.getBlockPos())) {
            info("Moved, disabling!");
            this.toggle();
            return;
        }

        FindItemResult obby = InvUtils.find(Items.OBSIDIAN);
        if (!obby.found()) {
            error("No obsidian!");
            this.toggle();
            return;
        }

        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }

        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = getTargetInHole(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) {
                reset();
                return;
            }
        }

        if (!HoleUtils.isInHole(target.getBlockPos(), true)) {
            target = null;
            reset();
            return;
        }


        placePosition = getBlockPlacePosition(target);
        if (placePosition == null) {
            fail();
            return;
        }

        BlockPos lastBreaking = getLastBreaking();
        if (lastBreaking == null || !lastBreaking.equals(placePosition)) {
            reset();
            successDelay();

            attackWithCompatibility(placePosition, Direction.UP);
            return;
        }


        boolean intersects = false;
        for (Entity entity : mc.world.getEntities()) {
            if (Box.from(new BlockBox(placePosition)).intersects(entity.getBoundingBox())) {
                intersects = true;
                if (!(entity instanceof EndCrystalEntity)) continue;
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), () -> attack(entity));
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (intersects) {
            return;
        }

        switch (stage) {
            case PRE -> {
                if (stageTicks == 0) disableAttackingModules();

                if (stageTicks >= preDelay.get()) {
                    setStageTo(Stage.PLACE);
                    return;
                }
                stageTicks++;
            }
            case PLACE -> {
                TarBlockUtils.InteractRunnable callback = (bhr -> {
                    float yaw = (float) Rotations.getYaw(bhr.getPos());
                    float pitch = (float) Rotations.getPitch(bhr.getPos());
                    sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

                    swapToOffhand(obby.slot());
                    BlockUtils.interact(bhr, Hand.OFF_HAND, true);
                    swapToOffhand(obby.slot());
                });


                if (TarBlockUtils.place(placePosition, false, true, Blocks.OBSIDIAN, callback)) {
                    // advance stage
                    setStageTo(Stage.POST);
                } else {
                    // will be cleared to pre on reset()
                    fail();
                }
            }
            case POST -> {
                if (stageTicks >= postDelay.get()) {
                    enableAttackingModules();
                    reset();
                    successDelay();
                    return;
                }
                stageTicks++;
            }
        }
    }

    // Deterministic method to get optimal block place position
    private BlockPos getBlockPlacePosition(PlayerEntity target) {
        if (mc.player == null) return null;

        BlockPos center = target.getBlockPos().up();
        for (Direction direction : DIRECTIONS) {
            BlockPos pos = center.offset(direction);
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) continue;
            if (!isValidPlacePos(pos)) continue;
            return pos;
        }
        return null;
    }

    private boolean isValidPlacePos(BlockPos pos) {
        if (mc.world == null) return false;

        Direction placeSide = BlockUtils.getPlaceSide(pos);
        if (placeSide == null) return false;

        BlockPos up = pos.up();
        boolean isBlockValid = mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN;
        return isBlockValid && mc.world.getBlockState(up).isAir();
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
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 40, SlotActionType.SWAP, mc.player);
    }

    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.player == null) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private enum Stage {
        PRE,
        PLACE,
        POST
    }
}
