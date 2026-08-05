package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.ClickSlotEvent;
import org.tarclient.addon.utils.MiningUtils;

import java.util.*;

public class IAmAFailure extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> larp = sgGeneral.add(new BoolSetting.Builder()
        .name("larp")
        .description("larpppppppp")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Interact reach, dont mess this up")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> progress = sgGeneral.add(new DoubleSetting.Builder()
        .name("progress")
        .description("Progress check for valid swaps")
        .defaultValue(0.9)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("The global cooldown")
        .defaultValue(20)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> depth = sgGeneral.add(new IntSetting.Builder()
        .name("depth")
        .description("Depth of the larp")
        .defaultValue(3)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> minDmg = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-dmg")
        .description("Minimum damage to start the larp")
        .defaultValue(5)
        .sliderRange(0, 15)
        .build()
    );

    private final Setting<Boolean> swap = sgGeneral.add(new BoolSetting.Builder()
        .name("swap")
        .description("Swaps...")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How many seconds should rendering take?")
        .defaultValue(0.2)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
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

    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    private int globalCooldown = 0;
    private float lastYaw;
    private float lastPitch;

    boolean ignoreSwap;

    public IAmAFailure() {
        super(TarAddon.CATEGORY, "i-am-a-failure", "Spreads the agenda");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        globalCooldown = 0;

        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        renderQueue.clear();

        ignoreSwap = false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Iterator<Map.Entry<BlockPos, Double>> it = renderQueue.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, Double> entry = it.next();
            double remaining = entry.getValue();

            if (remaining <= 0) {
                it.remove();
                continue;
            }

            double alphaMultip = Math.clamp(remaining / fadeTime.get(), 0, 1);

            // uhh multiply alpha ig?
            Color side = sideColor.get().copy().a((int) (sideColor.get().a * alphaMultip));
            Color line = lineColor.get().copy().a((int) (lineColor.get().a * alphaMultip));

            event.renderer.box(entry.getKey(), side, line, shapeMode.get(), 0);

            entry.setValue(remaining - (float) event.frameTime);
        }
    }

    @EventHandler
    private void onClick(ClickSlotEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        // recursion
        if (ignoreSwap) return;
        if (globalCooldown > 0) return;

        // are we on ground?
        if (!mc.player.isOnGround()) return;

        BlockPos breakingPos = MiningUtils.getBreakingBlockPos();

        if (MiningUtils.getBreakingProgress() < progress.get()) return;
        if (breakingPos == null) return;
        if (breakingPos.toCenterPos().squaredDistanceTo(mc.player.getEyePos()) > reach.get() * reach.get()) return;


        // we dont care about this swap..
        if (event.actionType != SlotActionType.SWAP) return;
        if (event.button != mc.player.getInventory().getSelectedSlot()) return; // swap to current hand
        ItemStack stack = mc.player.currentScreenHandler.getSlot(event.slotId).getStack();
        if (stack == null || stack.isEmpty()) return;

        // check if we should break this
        BlockState state = mc.world.getBlockState(breakingPos);

        // 1 last check for item: is suitable?
        if (!stack.isSuitableFor(state)) return;

        // replaceable: mining not truly finished
        if (state.isReplaceable()) return;

        mc.world.setBlockState(breakingPos, Blocks.AIR.getDefaultState());
        if (hasPotential(breakingPos, state, minDmg.get(), depth.get())) {
            ssdfg_00000(breakingPos);
        }
        mc.world.setBlockState(breakingPos, state);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null) return;
        if (globalCooldown > 0) {
            globalCooldown--;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            lastYaw = packet.getYaw(lastYaw);
            lastPitch = packet.getPitch(lastPitch);
        }
    }

    // Megumi Fushiguro
    private boolean hasPotential(BlockPos breakPos, BlockState oldState, double minDmg, int floodDepth) {
        if (mc.world == null) return false;

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        List<AbstractClientPlayerEntity> candidates = mc.world.getPlayers().stream()
            .filter(p -> p != mc.player)
            .filter(p -> !p.isSpectator())
            .filter(p -> !p.isCreative())
            .filter(p -> Friends.get().shouldAttack(p))
            .filter(p -> p.squaredDistanceTo(Vec3d.ofCenter(breakPos)) <= 12 * 12)
            .toList();

        if (candidates.isEmpty()) return false;

        queue.add(breakPos);
        visited.add(breakPos);

        Set<BlockPos> crystalBases = new HashSet<>();

        while (!queue.isEmpty()) {
            BlockPos airPos = queue.poll();

            if (airPos.getSquaredDistance(breakPos) > floodDepth * floodDepth)
                continue;

            BlockState base = mc.world.getBlockState(airPos.down());

            if ((base.isOf(Blocks.OBSIDIAN) || base.isOf(Blocks.BEDROCK))
                && mc.world.getBlockState(airPos).isAir()) {
                if (mc.world.getOtherEntities(null, new Box(airPos), (entity -> {
                    // goofy predicate to get all entities except spectators & end crystals already placed in the position
                    if (entity.isSpectator()) return false;
                    return !entity.getType().equals(EntityType.END_CRYSTAL) || !entity.getBlockPos().equals(airPos);
                })).isEmpty())
                    crystalBases.add(airPos.down());
            }


            for (Direction dir : Direction.values()) {
                BlockPos next = airPos.offset(dir);

                if (!visited.add(next))
                    continue;

                if (!mc.world.getBlockState(next).isAir())
                    continue;

                queue.add(next);
            }

            // stupid af
            if (visited.size() > 1000)
                break;
        }

        for (BlockPos base : crystalBases) {
            BlockPos crystalPos = base.up();
            Vec3d crystalVec = Vec3d.ofBottomCenter(crystalPos);

            for (PlayerEntity player : candidates) {
                if (player.squaredDistanceTo(crystalVec) > 8 * 8)
                    continue;

                // as air
                mc.world.setBlockState(breakPos, Blocks.AIR.getDefaultState());
                double damageAir = DamageUtils.crystalDamage(player, crystalVec);

                if (damageAir < minDmg) {
                    // fah no
                    continue;
                }

                // bugged tf out unless we guard this
                if (!base.up().equals(breakPos)) {
                    mc.world.setBlockState(breakPos, oldState);
                    double damageObby = DamageUtils.crystalDamage(player, crystalVec);

                    if (damageAir - damageObby < minDmg) {
                        // difference too low, probably invalid
                        continue;
                    }
                }

                // differences good enough OR breakPos deals damageAir
                return true;
            }
        }

        return false;
    }

    private void ssdfg_00000(BlockPos blockPos) {
        if (mc.player == null) return;
        // do module
        // rely on chipped anvils on mainhand, or swap to them
        // store slot
        int slot = -1;
        if (swap.get()) {
            FindItemResult result = InvUtils.find(Items.CHIPPED_ANVIL);
            if (!result.found()) return;

            // found, swap
            swap(result.slot());
            slot = result.slot();
        } else {
            // check if we have anvil in hand rn
            if (mc.player.getInventory().getSelectedStack().getItem() != Items.CHIPPED_ANVIL) return;
        }

        // rotate down
        double yaw = Rotations.getYaw(mc.player.getBlockPos().toBottomCenterPos());
        double pitch = Rotations.getPitch(mc.player.getBlockPos().toBottomCenterPos());
        sendRotatePacket(yaw, pitch, RotationPacket.Full);
        BlockHitResult bhr = new BlockHitResult(mc.player.getBlockPos().toBottomCenterPos(), Direction.UP, mc.player.getBlockPos().down(), false);

        // magic v2
        for (int i = 0; i < 25; i++) {
            sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 0));
        }

        // swap back
        if (swap.get()) {
            swap(slot);
        }

        if (larp.get()) {
            addMsg(Text.of(String.format("Changed the block at %d, %d, %d", blockPos.getX(), blockPos.getY(), blockPos.getZ())), 0);
        }

        renderQueue.put(blockPos, fadeTime.get());
        globalCooldown = cooldown.get();
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        ignoreSwap = true;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
        ignoreSwap = false;
    }
}
