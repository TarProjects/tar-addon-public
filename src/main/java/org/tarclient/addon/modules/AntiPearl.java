package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.util.Hand;
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
        .description("Raytrace 3 blocks, prevent anti-phase from being triggered from normal pearls")
        .defaultValue(true)
        .build()
    );
    int realSlot = -1; // i hate silent swaps

    public AntiPearl() {
        super(TarAddon.CATEGORY, "anti-pearl", "Cancels/modifies pearl throw depending on scenario");
    }

    @EventHandler
    private void onPacketSend(final PacketEvent.Send event) {
        if (!Utils.canUpdate()) return;

        if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {
            realSlot = packet.getSelectedSlot();
            return;
        }
        if (event.packet instanceof UpdateSelectedSlotS2CPacket(int slot)) {
            realSlot = slot;
            return;
        }


        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            if (packet.getHand() == Hand.OFF_HAND) return;
            if (realSlot == -1) return;
            if (mc.player.getInventory().getStack(realSlot).getItem() != Items.ENDER_PEARL) return;

            if (entities.get() && hitsEntity()) {
                event.cancel();
                cancelInfo("Entity in the way!");
                return;
            }

            if (blocks.get() && hitsBlock()) {
                event.cancel();
                cancelInfo("Block is in the way!");
                return;
            }
            return;
        }
        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            if (packet.getHand() == Hand.OFF_HAND) return;
            if (realSlot == -1) return;
            if (mc.player.getInventory().getStack(realSlot).getItem() != Items.ENDER_PEARL) return;

            if (blocks.get()) {
                event.cancel(); // pray that silent swap doesnt cancel block place ig
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
