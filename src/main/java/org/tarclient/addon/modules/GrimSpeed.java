package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.MovementRotationEvent;
import org.tarclient.addon.mixin.LivingEntityAccessor;
import org.tarclient.addon.utils.BurrowUtils;
import org.tarclient.addon.utils.PredictUtils;
import org.tarclient.addon.utils.SimulationState;

public class GrimSpeed extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> lagCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("lag-cooldown")
        .description("Cooldown on setback")
        .defaultValue(20)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Boolean> constant = sgGeneral.add(new BoolSetting.Builder()
        .name("constant")
        .description("Constantly boosts you")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ticks = sgGeneral.add(new IntSetting.Builder()
        .name("ticks")
        .description("Ticks to manipulate before landing")
        .defaultValue(3)
        .sliderRange(0, 20)
        .visible(() -> !constant.get())
        .build()
    );

    private final Setting<Integer> glidePackets = sgGeneral.add(new IntSetting.Builder()
        .name("glide-packets")
        .description("How many start gliding packets to send")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Double> glidingRotation = sgGeneral.add(new DoubleSetting.Builder()
        .name("gliding-rotation")
        .description("Rotation when gliding")
        .defaultValue(45)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .build()
    );

    private final Setting<Double> startGlidingRotation = sgGeneral.add(new DoubleSetting.Builder()
        .name("start-gliding-rotation")
        .description("Rotation when starting to glide")
        .defaultValue(45)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .build()
    );

    private boolean boosting;
    private double rotation;
    private int cooldown;

    public GrimSpeed() {
        super(TarAddon.CATEGORY, "grim-speed", "Speeds up using elytra in inventory");
    }

    @Override
    public void onActivate() {
        boosting = false;
        rotation = 0;
        cooldown = 0;
    }

    @EventHandler
    private void onMovementRotationEvent(MovementRotationEvent event) {
        if (!boosting) return;
        event.pitch = (float) rotation;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (mc.player.isOnGround() || BurrowUtils.isPlayerPhased(mc.player)) {
            boosting = false;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!elytra.found()) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        if (!mc.player.isJumping()) return;
        if (mc.player.input.getMovementInput().y <= 0 || mc.player.input.getMovementInput().x != 0) return;

        // jump cd just incase
        ((LivingEntityAccessor) mc.player).tar$setJumpCooldown(0);

        // boost anyways from here
        if (constant.get()) boosting = true;

        // should we start boosting? boost until ground / skip on constat
        if (!boosting) {
            SimulationState state = new SimulationState(mc.player.getEntityPos(), mc.player.getVelocity());
            double lastY = state.pos.y;
            for (int i = 0; i < ticks.get(); i++) {
                PredictUtils.advanceOneTick(mc.player, state, 0);
                if (Math.abs(lastY - state.pos.y) < 1e-5) {
                    boosting = true;
                    break;
                }
                lastY = state.pos.getY();
            }
        }

        if (boosting) {
            if (mc.player.isGliding()) {
                Rotations.rotate(mc.player.getYaw(), glidingRotation.get());
                rotation = glidingRotation.get();
            } else {
                // NOTE: optimal clicking saves 2 swaps
                InvUtils.click().slot(elytra.slot());
                InvUtils.click().slotArmor(2);

                for (int i = 0; i < glidePackets.get(); i++) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }

                mc.player.startGliding();
                InvUtils.click().slotArmor(2);
                InvUtils.click().slot(elytra.slot());

                Rotations.rotate(mc.player.getYaw(), startGlidingRotation.get());
                rotation = startGlidingRotation.get();
            }
        }
    }

    @EventHandler
    private void onPost(TickEvent.Post event) {
        if (mc.player == null) return;
        if (mc.player.isGliding() && cooldown == 0) mc.player.setPose(EntityPose.STANDING);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            boosting = false;
            cooldown = lagCooldown.get();
        }
    }
 }

