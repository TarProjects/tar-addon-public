package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.TarBlockUtils;

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
        double distance = 3;
        HitResult hitResult = TarBlockUtils.raycastBlocks(distance);

        return hitResult != null && hitResult.getType() == HitResult.Type.BLOCK;
    }

    // return true if pearl hits entity
    private boolean hitsEntity() {
        if (mc.player == null) return false;

        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        double maxDistance = range.get();

        Vec3d cameraPos = mc.player.getCameraPosVec(tickProgress);
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

    private void cancelInfo(String reason) {
        info("Cancelled: " + reason);
    }

}
