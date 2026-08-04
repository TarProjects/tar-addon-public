package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class SlowExp extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between exp throws")
        .defaultValue(2)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Integer> frequency = sgGeneral.add(new IntSetting.Builder()
        .name("frequency")
        .description("Frequency of exp throwing")
        .defaultValue(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> stopAt = sgGeneral.add(new IntSetting.Builder()
        .name("stop-at")
        .description("Stops at specific percentage. Set to 0 to disable")
        .defaultValue(85)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<GuiMode> gui = sgGeneral.add(new EnumSetting.Builder<GuiMode>()
        .name("gui")
        .description("What to do on gui screen?")
        .defaultValue(GuiMode.None)
        .build()
    );

    private final Setting<Boolean> checkOpponents = sgGeneral.add(new BoolSetting.Builder()
        .name("check-opponents")
        .description("Checks nearby opponents for contesting")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> boxSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("box-size")
        .description("Size of the box to check entities within. Note that this is half of the true size of the box!")
        .defaultValue(0.75)
        .sliderRange(0, 1.5)
        .build()
    );

    private final Setting<Boolean> packet = sgGeneral.add(new BoolSetting.Builder()
        .name("packet")
        .description("Extra packet for rotation")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing on item use")
        .defaultValue(true)
        .build()
    );

    private int cooldown;

    public SlowExp() {
        super(TarAddon.CATEGORY, "slow-exp", "Alternative autoexp, easily configurable to be slow");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        FindItemResult result = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!result.found()) {
            error("No exp!");
            this.toggle();
            return;
        }

        if (!mc.player.isOnGround()) {
            error("Not on ground, disabling!");
            this.toggle();
            return;
        }

        if (checkOpponents.get()) {
            double size = boxSize.get();
            Vec3d orbPos = mc.player.getEntityPos().add(0,size,0);
            Box orbBox = new Box(
                orbPos.x - size,
                orbPos.y - size,
                orbPos.z - size,
                orbPos.x + size,
                orbPos.y + size,
                orbPos.z + size
            );

            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player.isSpectator() || player == mc.player) continue;

                if (player.getBoundingBox().intersects(orbBox)) {
                    if (player.getId() < mc.player.getId()) {
                        error("Lower entity id able to pick up, disabling!");
                        this.toggle();
                        return;
                    }
                }
            }
        }

        if (!shouldMend(mc.player)) {
            info("Hit durability cap!");
            this.toggle();
            return;
        }

        if (mc.currentScreen != null) {
            // gui open
            switch (gui.get()) {
                case Pause -> {
                    return;
                }
                case Toggle -> {
                    this.toggle();
                    return;
                }
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (packet.get()) {
            sendRotatePacket(mc.player.getYaw(), 90, RotationPacket.Full);
        }

        InvUtils.swap(result.slot(), true);
        int xpCount = mc.player.getInventory().getStack(result.slot()).getCount();

        for (int i = 0; i < frequency.get(); i++) {
            if (xpCount - i <= 0) break;
            sendPacket(new PlayerInteractItemC2SPacket(result.getHand(), 0, mc.player.getYaw(), 90));
            if (swing.get()) {
                mc.player.swingHand(result.getHand());
            }
        }

        InvUtils.swapBack();
        cooldown = delay.get();
    }

    private boolean shouldMend(PlayerEntity player) {
        if (stopAt.get() == 0) return true;

        for (int i = 0; i < 4; i++) {
            int slot = SlotUtils.ARMOR_START + i;
            ItemStack stack = player.getInventory().getStack(slot);

            int maxDmg = stack.getMaxDamage();
            if (maxDmg == 0 || !Utils.hasEnchantment(stack, Enchantments.MENDING)) continue; // fallback or no mending

            int dmg = stack.getDamage();
            double percentage = (double) (maxDmg - dmg) / maxDmg;

            if (percentage * 100 < stopAt.get()) return true;
        }

        return false;
    }

    private enum GuiMode {
        None,
        Toggle,
        Pause
    }
}
