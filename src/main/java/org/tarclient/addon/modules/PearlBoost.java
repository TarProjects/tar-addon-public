package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;


public class PearlBoost extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> crystalAmount = sgGeneral.add(new IntSetting.Builder()
        .name("crystals")
        .description("Crystal amount")
        .defaultValue(1)
        .range(0, 5)
        .build()
    );

    private final Setting<Integer> onSneakCrystals = sgGeneral.add(new IntSetting.Builder()
        .name("on-sneak-crystals")
        .description("Boost if player is sneaking")
        .defaultValue(2)
        .range(0, 5)
        .build()
    );

    public PearlBoost() {
        super(TarAddon.CATEGORY, "pearl-boost", "Boosts a pearl throw if possible");
    }

    private int pearlID;
    private Stage stage;
    private BlockPos obbyPosition;
    private int remainingCrystals;

    @Override
    public void onActivate() {
        pearlID = -999;
        stage = Stage.None;
        obbyPosition = null;
        remainingCrystals = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;
        if (!(event.packet instanceof EntitySpawnS2CPacket packet)) return;
        if (packet.getEntityType() != EntityType.ENDER_PEARL) return;

        if (pearlID == -999 || stage == Stage.None) {
            if (packet.getEntityData() == mc.player.getId()) {
                int crystals = mc.player.isSneaking() ? onSneakCrystals.get() : crystalAmount.get();
                if (crystals == 0) return;
                pearlID = packet.getEntityId();
                stage = Stage.ShouldPlace;
                obbyPosition = null;
                remainingCrystals = crystals;
            }
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (pearlID == -999) {
            stage = Stage.None;
            obbyPosition = null;
            return;
        }

        Entity entityById = mc.world.getEntityById(pearlID);
        if (!(entityById instanceof EnderPearlEntity enderPearl)) {
            pearlID = -999;
            stage = Stage.None;
            obbyPosition = null;
            return;
        }


        if (!enderPearl.isAlive()) {
            pearlID = -999;
            stage = Stage.None;
            return;
        }

        if (enderPearl.getVelocity().length() < 0.01)
            return;


        switch (stage) {
            case ShouldPlace -> {
                if (mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) return;
                BlockPos pos = findBoostPosFixed();
                if (pos != null) {
                    Rotations.rotate(Rotations.getYaw(pos.toCenterPos()), Rotations.getPitch(pos.toCenterPos()),
                        () -> {
                            BlockHitResult bhr = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);

                            mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, bhr);
                        });


                    obbyPosition = pos;
                    stage = Stage.ShouldBreak;
                }
            }
            case ShouldBreak -> {
                if (obbyPosition != null) {
                    boolean found = false;
                    for (Entity entity : mc.world.getEntities()) {
                        if (entity instanceof EndCrystalEntity) {
                            if (Box.from(new BlockBox(obbyPosition.up())).intersects(entity.getBoundingBox())) {
                                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity),
                                    () -> attack(entity));
                                found = true;
                            }
                        }
                    }

                    if (found) {
                        remainingCrystals--;

                        if (remainingCrystals > 0) {
                            stage = Stage.ShouldPlace;
                        } else {
                            stage = Stage.None;
                            pearlID = -999;
                            obbyPosition = null;
                        }
                    }
                }
            }
        }
    }

    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.player == null) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public BlockPos findBoostPosFixed() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d dir = Vec3d.fromPolar(0, mc.player.getYaw());
        dir = new Vec3d(dir.x, 0, dir.z);

        if (dir.lengthSquared() < 0.001) return null;
        dir = dir.normalize();

        BlockPos playerPos = mc.player.getBlockPos();

        int dx = (int) Math.round(dir.x);
        int dz = (int) Math.round(dir.z);

        BlockPos front = playerPos.add(dx, 0, dz);

        if (!isStrictFront(mc.player, front, dir)) return null;

        if (!canPlaceCrystal(front)) return null;

        return front;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);

        if (!(state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK))) {
            return false;
        }

        if (!mc.world.getBlockState(pos.up()).isAir()) return false;

        Box box = new Box(pos.up());
        return mc.world.getOtherEntities(null, box).isEmpty();
    }

    private boolean isStrictFront(PlayerEntity player, BlockPos pos, Vec3d dir) {
        Vec3d to = Vec3d.ofCenter(pos).subtract(player.getEntityPos());

        Vec3d flat = new Vec3d(to.x, 0, to.z);

        if (flat.lengthSquared() < 0.001) return false;

        flat = flat.normalize();

        return flat.dotProduct(dir) > 0.98;
    }


    private enum Stage {
        None,
        ShouldPlace,
        ShouldBreak,
    }
}
