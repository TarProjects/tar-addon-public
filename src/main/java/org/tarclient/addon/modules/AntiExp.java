package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.PredictUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class AntiExp extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range of interactions")
        .defaultValue(5.2)
        .range(1, 6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay")
        .defaultValue(5)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size")
        .description("Size of box to check within")
        .defaultValue(3)
        .range(0, 1.5)
        .build()
    );

    /* --- Render --- */
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

    public AntiExp() {
        super(TarAddon.CATEGORY, "anti-exp", "Prevents opponents from exping");
    }

    int cooldown;
    BlockPos placing;
    private final Map<BlockPos, Double> renderQueue = new HashMap<>();

    @Override
    public void onActivate() {
        cooldown = 0;
        placing = null;
        renderQueue.clear();
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
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (placing == null) return;

        FindItemResult result = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!result.found()) return;

        TarBlockUtils.place(placing, false, true, Blocks.OBSIDIAN, (blockHitResult) -> {
            double yaw = Rotations.getYaw(blockHitResult.getPos());
            double pitch = Rotations.getPitch(blockHitResult.getPos());

            sendRotatePacket(yaw, pitch, RotationPacket.Full);

            InvUtils.swap(result.slot(), true);
            BlockUtils.interact(blockHitResult, result.getHand(), true);
            InvUtils.swapBack();
            renderQueue.put(placing, fadeTime.get());
        });

        cooldown = delay.get();
        placing = null; // consume
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (placing != null || cooldown > 0) return;
        if (event.packet instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.EXPERIENCE_BOTTLE) {
            mc.execute(() -> handleEntitySpawn(packet));
        }
    }

    private void handleEntitySpawn(EntitySpawnS2CPacket packet) {
        if (placing != null || cooldown > 0) return;
        if (mc.world == null || mc.player == null) return;

        Entity entity = mc.world.getEntityById(packet.getEntityId());
        if (!(entity instanceof ProjectileEntity projectile)) return;
        if (mc.player.squaredDistanceTo(projectile) > 6*6) return; // cheap skip

        if (!(projectile.getOwner() instanceof PlayerEntity owner) || owner == mc.player) return;
        if (!Friends.get().shouldAttack(owner)) return;

        Vec3d firstLandPos = PredictUtils.predictLandingPoint(projectile, 5);
        if (firstLandPos != null) {
            Box box = createBox(firstLandPos);
            if (box.intersects(mc.player.getBoundingBox())) return; // already intersects, no need to place!
        }

        // firstlandpos null or box doesnt intersect, calculate with directionals
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos offset = projectile.getBlockPos().offset(direction);

            // spoof
            BlockState old = mc.world.getBlockState(offset);
            mc.world.setBlockState(offset, Blocks.OBSIDIAN.getDefaultState());
            Vec3d nextLandPos = PredictUtils.predictLandingPoint(projectile, 5);
            mc.world.setBlockState(offset, old);

            if (nextLandPos != null) {
                Box box = createBox(nextLandPos);
                if (box.intersects(mc.player.getBoundingBox())) {
                    // golden, first didnt intersect but now does -> profit!
                    if (BlockUtils.canPlace(offset)) {
                        if (mc.player.squaredDistanceTo(offset.toCenterPos()) > range.get() * range.get()) continue;
                        Direction side = BlockUtils.getClosestPlaceSide(offset);
                        if (side == null) continue;
                        this.placing = offset;
                        return;
                    }
                }
            }
        }
    }

    private Box createBox(Vec3d vec3d) {
        double half = size.get() / 2;
        return new Box(vec3d.getX() - half, vec3d.getY() - half, vec3d.getZ() - half, vec3d.getX() + half, vec3d.getY() + half, vec3d.getZ() + half);
    }
}
