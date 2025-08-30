package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.events.PlayerJumpEvent;

import static org.tarclient.addon.utils.BurrowUtility.burrowedObsidian;
import static org.tarclient.addon.utils.BurrowUtility.checkHead;

public class PhaseFix extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();


    private final Setting<Boolean> onground = sgGeneral.add(new BoolSetting.Builder()
        .name("on-ground")
        .description("Spoofs on-ground value. Set this to whichever you want the onground value to be.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> slowdown = sgGeneral.add(new BoolSetting.Builder()
        .name("slow-down")
        .description("Slows you down inside blocks")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> slowspeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("slow-speed")
        .description("Speed which you go when slowed.")
        .defaultValue(10)
        .sliderRange(-10, 10)
        .visible(slowdown::get)
        .build()
    );

    private final Setting<Boolean> vclip = sgGeneral.add(new BoolSetting.Builder()
        .name("vclip")
        .description("VClips when jumping inside phase.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> packet = sgGeneral.add(new BoolSetting.Builder()
        .name("packet")
        .description("Sends extra packet on vclip")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> clipBind = sgGeneral.add(new KeybindSetting.Builder()
        .name("vclip-bind")
        .description("Alternative bind to vclipping")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Integer> delaySet = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Clipbind delay. Only here because some clients dont work properly...")
        .defaultValue(2)
        .sliderRange(0, 20)
        .visible(() -> clipBind.get().isSet())
        .build()
    );
    int delay = 0;

    public PhaseFix() {
        super(TarAddon.CATEGORY, "phase-fix", "Fixes some issues regarding phase.");
    }

    @Override
    public void onActivate() {
        delay = 0;
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action == KeyAction.Press && match(clipBind.get(), Keybind.fromButton(event.button)) && Utils.canUpdate()) {
            // So goofy omfg
            delay = delaySet.get();
        }
    }

    private boolean match(Keybind one, Keybind two) {
        return one.getValue() == two.getValue() && one.isKey() == two.isKey();
    }


    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (!Utils.canUpdate() || isNotSurvival()) return;

        if (burrowedObsidian() && slowdown.get()) {
            Vec3d vel = PlayerUtils.getHorizontalVelocity(slowspeed.get());
            ((IVec3d) event.movement).meteor$set(vel.x, event.movement.y, vel.z);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // if (Utils.canUpdate() && !isNotSurvival() && burrowedObsidian()) {
        //    mc.player.setOnGround(onground.get());
        // }

        // stupid
        if (delay > 0) {
            delay--;
            if (delay == 0) {
                if (burrowedObsidian() && checkHead() && !isNotSurvival()) {
                    mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ());
                    // Packet will be sent on tick anyways, stop flaggin with this?
                    if (packet.get()) {
                        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
                    }
                }
            }
        }
    }


    @EventHandler
    private void onJump(PlayerJumpEvent event) {
        if (!Utils.canUpdate() || isNotSurvival()) return;

        if (burrowedObsidian()) {
            if (vclip.get() && checkHead()) {
                // TP 1 block up
                event.cancel();
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ());
                // Packet will be sent on tick anyways, stop flaggin with this?
                if (packet.get()) {
                    sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!Utils.canUpdate() || isNotSurvival()) return;

        if (event.packet instanceof PlayerMoveC2SPacket && burrowedObsidian()) {
            ((PlayerMoveC2SPacketAccessor) event.packet).setOnGround(onground.get());
        }
    }

    private boolean isNotSurvival() {
        return mc.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL;
    }
}
