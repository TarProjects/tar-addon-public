package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class CriticalsPlus extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The mode on how Criticals will function.")
        .defaultValue(Mode.Grim)
        .build()
    );

    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Only crits against players")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("only-weapon")
        .description("Only crits with a weapon")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> flagCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("flag-cooldown")
        .description("Cooldown for flagging")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private int cooldown;

    public CriticalsPlus() {
        super(TarAddon.CATEGORY, "criticals-plus", "Criticals but improved");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (cooldown > 0) return;
        if (mc.player == null) return;
        // normal crit
        if (mc.player.fallDistance > 0 && !mc.player.isOnGround()) return;

        if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.meteor$getType() == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
            ItemStack stack = mc.player.getInventory().getSelectedStack();
            if (onlyWeapon.get()) {
                if (!stack.isIn(ItemTags.SWORDS) && !stack.isIn(ItemTags.AXES)) return;
            }
            Entity entity = packet.meteor$getEntity();
            if (!isValid(entity)) return;

            switch (mode.get()) {
                case Grim -> {
                    info("Sending");
                    sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos().add(0, 1e-5, 0), mc.player.getYaw(), mc.player.getPitch(), false, mc.player.horizontalCollision));
                    sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getEntityPos().add(0, -1e-5, 0), mc.player.getYaw(), mc.player.getPitch(), false, mc.player.horizontalCollision));
                }
            }
        }
    }

    private boolean isValid(Entity target) {
        if (!(target instanceof LivingEntity)) return false;
        return !onlyPlayers.get() || target instanceof PlayerEntity;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            cooldown = flagCooldown.get();
        }
    }

    private enum Mode {
        Grim
    }
}
