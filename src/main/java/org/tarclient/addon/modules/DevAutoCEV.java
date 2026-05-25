package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.ClickBlockEvent;
import org.tarclient.addon.utils.TarBlockUtils;

import static org.tarclient.addon.utils.MiningUtils.*;
import static org.tarclient.addon.utils.MioUtils.*;

public class DevAutoCEV extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDelay = this.settings.createGroup("Delay");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Integer> secondarySyncTicks = sgDelay.add(new IntSetting.Builder()
        .name("secondary-sync-ticks")
        .description("How many ticks to wait before breaking/syncing")
        .defaultValue(20)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> doubleSyncTicks = sgDelay.add(new IntSetting.Builder()
        .name("double-sync-ticks")
        .description("How many ticks to wait before breaking/syncing")
        .defaultValue(40)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> findDelay = sgDelay.add(new IntSetting.Builder()
        .name("find-delay")
        .description("How many ticks to wait before determining mode")
        .defaultValue(40)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Integer> brokenTicks = sgDelay.add(new IntSetting.Builder()
        .name("broken-ticks")
        .description("How many ticks does it take to break a block?")
        .defaultValue(45)
        .sliderRange(0, 100)
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
    private Stage stage = Stage.PRE;
    private Mode mode;
    private int stageTicks = 0;

    private int shouldDouble;
    private int shouldSecondary;

    public DevAutoCEV() {
        super(TarAddon.CATEGORY, "dev-auto-cev", "Cevs opponents");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }
        reset();

        stage = Stage.FINDMODE;
        mode = null;

        shouldDouble = 0;
        shouldSecondary = 0;

        toggleAutoMine(false);
        resetMiningProgress();
    }

    @Override
    public void onDeactivate() {
        reset();
        toggleAutoMine(true);
    }

    private void reset() {
        setStageTo(Stage.PRE);

        lastState = null;
        enableAttackingModules();
    }

    private void setStageTo(Stage stage) {
        this.stage = stage;
        stageTicks = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        BlockPos breaking = getBreakingBlockPos();
        if (breaking != null) {
            event.renderer.box(breaking, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onClickBlock(ClickBlockEvent event) {
        if (mode == null) {
            reset();
            setStageTo(Stage.FINDMODE);

            shouldDouble = 0;
            shouldSecondary = 0;
            return;
        }

        reset();
        if (mode != Mode.PRIMARY) {
            setStageTo(Stage.WAITFORSYNC);
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        if (mc.player == null) return;

        FindItemResult obby = InvUtils.find(Items.OBSIDIAN);
        if (!obby.found()) {
            error("No obsidian!");
            this.toggle();
            return;
        }

        BlockPos breaking = getBreakingBlockPos();
        if (breaking == null) {
            if (stage != Stage.FINDMODE) {
                reset();
            }
            return;
        }

        if (stage == Stage.FINDMODE) {
            if (stageTicks >= findDelay.get()) {
                if (shouldDouble == 2) {
                    mode = Mode.DOUBLE;
                    resetMiningProgress();
                    setStageTo(Stage.WAITFORSYNC);
                } else if (shouldSecondary == 2) {
                    mode = Mode.SECONDARY;
                    resetMiningProgress();
                    setStageTo(Stage.WAITFORSYNC);
                } else {
                    mode = Mode.PRIMARY;
                    // not required but useful
                    toggleAutoMine(true);
                    setStageTo(Stage.PRE);
                }
                return;
            }

            switch (shouldDouble) {
                case 0 -> {
                    if (mc.world.getBlockState(breaking.down()).getBlock() == Blocks.OBSIDIAN) shouldDouble++;
                }
                case 1 -> {
                    if (mc.world.getBlockState(breaking.down()).isAir()) shouldDouble++;
                }
            }

            switch (shouldSecondary) {
                case 0 -> {
                    if (mc.world.getBlockState(breaking).getBlock() != Blocks.OBSIDIAN) break;

                    for (Entity entity : mc.world.getEntities()) {
                        if (!(entity instanceof EndCrystalEntity)) continue;
                        if (Box.from(new BlockBox(breaking.up())).intersects(entity.getBoundingBox())) {
                            shouldSecondary++;
                            break;
                        }
                    }
                }
                case 1 -> {
                    if (mc.world.getBlockState(breaking).isAir()) {
                        shouldSecondary++;
                    }
                }
            }

            stageTicks++;
            return;
        }

        switch (mode) {
            case PRIMARY -> handlePrimary(breaking, obby);
            case SECONDARY -> handleSecondary(breaking, obby);
            case DOUBLE -> handleDouble(breaking);
        }

    }

    private BlockState lastBelowState = null;

    private void handleDouble(BlockPos breaking) {
        BlockState currentBelowState = mc.world.getBlockState(breaking.down());
        if (lastBelowState == null) {
            lastBelowState = currentBelowState;
            return;
        }

        if (stage == Stage.WAITFORSYNC) {
            if (lastBelowState.getBlock() == Blocks.OBSIDIAN && currentBelowState.isAir()) {
                setStageTo(Stage.SYNC);
            }

            lastBelowState = currentBelowState;

            return;
        }

        if (stage == Stage.SYNC) {
            if (stageTicks >= doubleSyncTicks.get()) {
                resetMiningProgress();
                setStageTo(Stage.PRE);
            }

            stageTicks++;
        }
    }

    private BlockState lastState = null;

    private void handleSecondary(BlockPos breaking, FindItemResult obby) {
        boolean isValid = isValidCevPosition(breaking);

        if (!isValid) {
            reset();
            return;
        }

        boolean intersects = false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (Box.from(new BlockBox(breaking)).intersects(entity.getBoundingBox())) {
                intersects = true;
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), () -> attack(entity));
            }
        }

        if (intersects) {
            reset();
            return;
        }

        BlockState currentState = mc.world.getBlockState(breaking);
        if (lastState == null) {
            lastState = currentState;
            return;
        }

        if (stage == Stage.WAITFORSYNC) {
            if (lastState.getBlock() == Blocks.OBSIDIAN && currentState.isAir()) {
                setStageTo(Stage.SYNC);
            }

            lastState = currentState;
            return;
        }

        if (stage == Stage.SYNC) {
            if (stageTicks >= secondarySyncTicks.get()) {
                resetMiningProgress();
                setStageTo(Stage.PRE);
            }

            stageTicks++;
            return;
        }


        int lastBreakingTicks = getBreakingTicks();
        if (lastBreakingTicks < brokenTicks.get()) {
            return;
        }

        switch (stage) {
            case PRE -> {
                // prevent placing while its not in air thus failing
                if (!mc.world.getBlockState(breaking).isAir()) return;

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


                if (TarBlockUtils.place(breaking, false, true, Blocks.OBSIDIAN, callback)) {
                    // advance stage
                    setStageTo(Stage.POST);
                } else {
                    info("FAIL");
                }
            }
            case POST -> {
                if (stageTicks >= postDelay.get()) {
                    enableAttackingModules();
                    reset();
                    return;
                }
                stageTicks++;
            }
        }
    }

    private void handlePrimary(BlockPos breaking, FindItemResult obby) {
        boolean isValid = isValidCevPosition(breaking);

        if (!isValid) {
            reset();
            return;
        }

        boolean intersects = false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (Box.from(new BlockBox(breaking)).intersects(entity.getBoundingBox())) {
                intersects = true;
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), () -> attack(entity));
            }
        }

        if (intersects) {
            reset();
            return;
        }

        int lastBreakingTicks = getBreakingTicks();
        if (lastBreakingTicks < brokenTicks.get()) {
            return;
        }


        switch (stage) {
            case PRE -> {
                // prevent placing while its not in air thus failing
                if (!mc.world.getBlockState(breaking).isAir()) return;

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


                if (TarBlockUtils.place(breaking, false, true, Blocks.OBSIDIAN, callback)) {
                    // advance stage
                    setStageTo(Stage.POST);
                } else {
                    info("FAIL");
                }
            }
            case POST -> {
                if (stageTicks >= postDelay.get()) {
                    enableAttackingModules();
                    reset();
                    return;
                }
                stageTicks++;
            }
        }
    }

    private boolean isValidCevPosition(BlockPos pos) {
        if (mc.player == null) return false;

        if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return false;
        return isValidPlacePos(pos);
    }

    private boolean isValidPlacePos(BlockPos pos) {
        if (mc.world == null) return false;

        Direction placeSide = BlockUtils.getPlaceSide(pos);
        if (placeSide == null) return false;

        BlockPos up = pos.up();
        boolean isBlockValid = mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN;
        return !collidesWithPlayer(pos) && isBlockValid && mc.world.getBlockState(up).isAir();
    }

    private boolean collidesWithPlayer(BlockPos pos) {
        if (mc.world == null) return false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity)) continue;
            if (Box.from(new BlockBox(pos)).intersects(entity.getBoundingBox())) {
                return true;
            }
        }
        return false;
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
        FINDMODE,
        WAITFORSYNC,
        SYNC,
        PRE,
        PLACE,
        POST
    }

    private enum Mode {
        PRIMARY,
        SECONDARY,
        DOUBLE
    }
}
