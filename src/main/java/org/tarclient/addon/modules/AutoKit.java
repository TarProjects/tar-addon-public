package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoKit extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("What height triggers this module")
        .defaultValue(120)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<String> kit = sgGeneral.add(new StringSetting.Builder()
        .name("kit")
        .description("What is the name of the kit you want to use")
        .defaultValue("kit")
        .build()
    );

    private final Setting<String> preKit = sgGeneral.add(new StringSetting.Builder()
        .name("pre-kit")
        .description("What is the name of the kit you want to use for xcarry & potions")
        .defaultValue("pre")
        .build()
    );

    private final Setting<String> worldName = sgGeneral.add(new StringSetting.Builder()
        .name("world-name")
        .description("Which world should we /kit in")
        .defaultValue("overworld")
        .build()
    );

    private final Setting<String> server = sgGeneral.add(new StringSetting.Builder()
        .name("server")
        .description("Which server is this module used in")
        .defaultValue("crystalpvp.cc")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How much time to wait before respawn to kit")
        .defaultValue(50)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Delay between each action")
        .defaultValue(20)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> kitWait = sgGeneral.add(new IntSetting.Builder()
        .name("kit-wait")
        .description("Time to wait for kit after killing")
        .defaultValue(40)
        .sliderRange(0, 80)
        .build()
    );

    private final Setting<List<Item>> xCarry = sgGeneral.add(new ItemListSetting.Builder()
        .name("x-carry")
        .description("Which items to put in X-Carry. Leave empty if you don't want to use this")
        .build()
    );

    private final Setting<List<StatusEffect>> potions = sgGeneral.add(new StatusEffectListSetting.Builder()
        .name("potions")
        .description("Should we throw potions? Leave as empty if not used.")
        .build()
    );

    private Stage stage;
    int ticks = 0;
    private final Set<StatusEffect> thrownPots = new HashSet<>();
    private final Set<Integer> movedSlots = new HashSet<>();

    public AutoKit() {
        super(TarAddon.CATEGORY, "auto-kit", "Module to automatically call /kit. Designed for crystalpvp.cc, so might not work for other servers.");
    }

    @Override
    public void onActivate() {
        stage = Stage.Wait;
        ticks = -1;
        thrownPots.clear();
        movedSlots.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Send event) {
        if (event.packet instanceof ClientStatusC2SPacket packet) {
            if (packet.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN && stage == Stage.Wait) {
                ticks = delay.get();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;

        String world = mc.world.getRegistryKey().getValue().getPath();
        if (!world.equals(worldName.get())) return;

        if (mc.getNetworkHandler().getServerInfo() == null) return;
        if (!mc.getNetworkHandler().getServerInfo().address.equals(server.get())) return;

        switch (stage) {
            case Wait -> {
                if (ticks <= -1) return;
                if (ticks > 0) { // countdown to 0
                    ticks--;
                    return;
                }

                stage = Stage.Kit;
                ticks = actionDelay.get();
                thrownPots.clear();
                movedSlots.clear();
            }
            case Kit -> {
                if (ticks > 0) {
                    ticks--;
                    return;
                }

                if (mc.player.getY() > height.get()) {
                    if (potions.get().isEmpty() && xCarry.get().isEmpty()) {
                        mc.getNetworkHandler().sendChatCommand("kit " + kit.get());
                        ticks = -1;
                        stage = Stage.Wait;
                    } else {
                        mc.getNetworkHandler().sendChatCommand("kit " + preKit.get());
                        // continue with XCarrying and getting stuff
                        ticks = actionDelay.get();
                        stage = Stage.Kill;
                    }
                }
            }
            case Kill -> {
                if (ticks > 0) {
                    ticks--;
                    return;
                }

                mc.getNetworkHandler().sendChatCommand("kill");
                ticks = actionDelay.get();
                stage = Stage.WaitForDeath;
            }
            case WaitForDeath -> {
                if (ticks > 0) {
                    ticks--;
                    return;
                }

                ticks = 0;
                stage = Stage.WaitForKit;
            }
            case WaitForKit -> {
                if (ticks > kitWait.get()) {
                    // counting up now!
                    error("Failed to get all items!");
                    stage = Stage.Wait;
                    ticks = delay.get();
                }
                ticks++;

                if (!potions.get().isEmpty()) {
                    for (StatusEffect effect : potions.get()) {
                        if (!InvUtils.find((itemStack) -> hasEffect(itemStack, effect)).found()) return; // not found
                    }
                }
                if (!xCarry.get().isEmpty()) {
                    int stackCount = 0;

                    for (int i = 0; i <= mc.player.getInventory().size(); i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (xCarry.get().contains(stack.getItem())) {
                            stackCount++;
                        }
                    }

                    if (stackCount < 4) return;
                }

                stage = Stage.XCarry;
                ticks = 0;
            }
            case XCarry -> {
                // vanilla limit
                if (ticks % 4 != 0) {
                    ticks++;
                    return;
                }
                int slot = ticks / 4;

                for (int i = 0; i < 40; i++) {
                    if (movedSlots.contains(i)) continue;
                    if (xCarry.get().contains(mc.player.getInventory().getStack(i).getItem())) {
                        InvUtils.move().from(i).toId(slot + 1);
                        movedSlots.add(i);
                        break;
                    }
                }


                if (slot >= 3) {
                    stage = Stage.Potion;
                    ticks = 0;
                } else {
                    ticks++;
                }
            }
            case Potion -> {
                if (ticks % 2 != 0) { // delay on swaps
                    ticks++;
                    return;
                }

                boolean found = false;

                for (int i = 0; i < 40; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    for (StatusEffectInstance instance : getEffects(stack)) {
                        StatusEffect effect = instance.getEffectType().value();
                        if (!thrownPots.contains(effect) && potions.get().contains(effect)) {
                            found = true;

                            InvUtils.quickSwap().fromId(i).toHotbar(mc.player.getInventory().getSelectedSlot());
                            sendRotatePacket(mc.player.getYaw(), -90, RotationPacket.Full);
                            sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), -90));

                            thrownPots.add(effect);
                            break;
                        }
                    }
                    if (found) break;
                }
                if (!found) {
                    stage = Stage.ReKit;
                    ticks = 0;
                } else {
                    ticks++;
                }
            }
            case ReKit -> {
                mc.getNetworkHandler().sendChatCommand("kit " + kit.get());

                ticks = -1;
                stage = Stage.Wait;
            }
        }
    }


    public Iterable<StatusEffectInstance> getEffects(ItemStack stack) {
        if (!(stack.getItem() instanceof SplashPotionItem)) return List.of();
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return List.of();
        return contents.getEffects();
    }

    private boolean hasEffect(ItemStack stack, StatusEffect effect) {
        for (StatusEffectInstance instance : getEffects(stack)) {
            if (instance.getEffectType().value().equals(effect)) return true;
        }
        return false;
    }

    private enum Stage {
        Wait,
        Kit,
        Kill,
        WaitForDeath,
        WaitForKit,
        XCarry,
        Potion,
        ReKit
    }
}
