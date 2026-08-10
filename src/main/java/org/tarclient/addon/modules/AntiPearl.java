package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.BurrowUtils;
import org.tarclient.addon.utils.TarBlockUtils;
import org.tarclient.addon.utils.TarPlayerUtils;

import java.util.function.Predicate;


public class AntiPearl extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> entities = sgGeneral.add(new BoolSetting.Builder()
        .name("entities")
        .description("Cancels pearl throw if entity collision is raytraced.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range of entity collision checks")
        .defaultValue(6)
        .range(0, 100)
        .sliderRange(0, 15)
        .visible(entities::get)
        .build()
    );

    private final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Raytrace 3 blocks and prevents bad pearl throws")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> checkPearlBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("check-pearl-boost")
        .description("Raytrace 3 blocks and prevents bad pearl throws")
        .defaultValue(true)
        .build()
    );

    public AntiPearl() {
        super(TarAddon.CATEGORY, "anti-pearl", "Cancels/modifies pearl throw depending on scenario");
    }

    @EventHandler
    private void onPacketSend(final PacketEvent.Send event) {
        if (mc.player == null) return;

        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            if (!mc.player.getStackInHand(packet.getHand()).isOf(Items.ENDER_PEARL)) return;

            if (entities.get() && hitsEntity()) {
                event.cancel();
                cancelInfo("Entity in the way!");
            }

            if (blocks.get() && hitsBlock()) {
                event.cancel();
                cancelInfo("Block is in the way!");
            }
        }
    }


    // return true if pearl hits block with distance <= 3
    private boolean hitsBlock() {
        if (mc.player == null || mc.world == null) return false;
        double distance = 3;

        Vec3d cameraPos = getCameraPos(mc.player, mc.world);

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        HitResult hitResult = TarBlockUtils.raycastBlocks(distance, yaw, pitch, cameraPos);

        return hitResult != null && hitResult.getType() == HitResult.Type.BLOCK;
    }

    // return true if pearl hits entity
    private boolean hitsEntity() {
        if (mc.player == null || mc.world == null) return false;

        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        double maxDistance = range.get();

        Vec3d cameraPos = getCameraPos(mc.player, mc.world);
        Vec3d rotation = mc.player.getRotationVec(tickProgress);
        Vec3d lookVector = rotation.multiply(maxDistance);
        Vec3d endPos = cameraPos.add(lookVector);

        Box searchBox = mc.player.getBoundingBox().stretch(lookVector).expand(1.0, 1.0, 1.0);

        Predicate<Entity> entityPredicate = (entity) -> {
            if (entity == mc.player) return false;
            return !entity.getBoundingBox().contains(mc.player.getEyePos());
        };

        EntityHitResult entityRayCast = ProjectileUtil.raycast(
            mc.player,
            cameraPos,
            endPos,
            searchBox,
            entityPredicate,
            maxDistance * maxDistance
        );

        return entityRayCast != null; // returns true on hit
    }

    private Vec3d getCameraPos(PlayerEntity player, ClientWorld world) {
        Vec3d cameraPos = player.getCameraPosVec(1);

        if (checkPearlBoost.get() && !BurrowUtils.isPlayerPhased(player)) {
            PearlBoost pearlBoost = Modules.get().get(PearlBoost.class);
            if (pearlBoost != null && pearlBoost.isActive()) {
                Vec3d clipPos = TarPlayerUtils.findStepPosition(player, world, pearlBoost.stepHeight.get());
                if (clipPos != null) return cameraPos.add(clipPos.subtract(player.getEntityPos()));
            }
        }

        return cameraPos;
    }

    private void cancelInfo(String reason) {
        info("Cancelled: " + reason);
    }

}
