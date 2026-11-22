package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.mixin.PlayerInteractEntityC2SPacketAccessor;


public class MacePlus extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Cooldown between elytra swaps")
        .defaultValue(20)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> stopFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-flying")
        .description("Force stops flying")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> twoTick = sgGeneral.add(new BoolSetting.Builder()
        .name("two-tick")
        .description("Waits 1 tick before re-clicking")
        .defaultValue(false)
        .build()
    );


    int globalCooldown;
    boolean shouldClick;

    public MacePlus() {
        super(TarAddon.CATEGORY, "mace-plus", "Improves mace silent swapping, disable silent swaps from other clients!");
    }

    @Override
    public void onActivate() {
        globalCooldown = 0;
        shouldClick = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (globalCooldown > 0) {
            globalCooldown--;
        }

        if (shouldClick) {
            InvUtils.click().slotArmor(2);
            // start again
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            shouldClick = false;
            mc.player.startGliding();
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        if (!mc.isOnThread() || mc.player == null) return;
        if (globalCooldown > 0) return;

        if (event.packet instanceof PlayerInteractEntityC2SPacket packet && ((PlayerInteractEntityC2SPacketAccessor) packet).getType().getType() == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
            FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
            if (!mace.found()) return;

            if (mc.player.fallDistance > 1.5) {
                if (mc.player.isGliding() && stopFlying.get()) {
                    // chestplate elytra, remove and do stuff
                    if (LivingEntity.canGlideWith(mc.player.getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST)) {
                        event.cancel();


                        // stop flying, server stops instantly without elytra
                        InvUtils.click().slotArmor(2);
                        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        mc.player.stopGliding();

                        InvUtils.swap(mace.slot(), true);
                        event.sendSilently(event.packet);
                        InvUtils.swapBack();

                        if (twoTick.get()) {
                            shouldClick = true;
                        } else {
                            InvUtils.click().slotArmor(2);
                            // start again
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            mc.player.startGliding();
                        }

                        globalCooldown = cooldown.get();
                    }
                } else {
                    event.cancel();

                    InvUtils.swap(mace.slot(), true);
                    event.sendSilently(event.packet);
                    InvUtils.swapBack();
                }
            }
        }
    }
}
