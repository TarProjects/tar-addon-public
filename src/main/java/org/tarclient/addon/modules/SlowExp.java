package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
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
        .description("Delay of xp when not buffering")
        .defaultValue(0)
        .sliderRange(0, 10)
        .min(1)
        .build()
    );

    private final Setting<Integer> frequency = sgGeneral.add(new IntSetting.Builder()
        .name("frequency")
        .description("Frequency of exp throwing (at yourself, not counting waste)")
        .defaultValue(4)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> bufferFrequency = sgGeneral.add(new IntSetting.Builder()
        .name("buffer-frequency")
        .description("How much should we freq when buffering? (delay is always 2 ticks)")
        .defaultValue(8)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> stopAt = sgGeneral.add(new IntSetting.Builder()
        .name("stop-at")
        .description("Stops at specific percentage. Set to 0 to disable")
        .defaultValue(80)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<GuiMode> gui = sgGeneral.add(new EnumSetting.Builder<GuiMode>()
        .name("gui")
        .description("What to do on gui screen?")
        .defaultValue(GuiMode.None)
        .build()
    );

    private final Setting<Double> boxSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("box-size")
        .description("Size of the box to check entities within. Note that this is half of the true size of the box!")
        .defaultValue(0.75)
        .sliderRange(0, 1.5)
        .build()
    );

    private final Setting<Rotation> rotate = sgGeneral.add(new EnumSetting.Builder<Rotation>()
        .name("rotate")
        .description("Rotations")
        .defaultValue(Rotation.Normal)
        .build()
    );

    private final Setting<Rotation> rotateBuffer = sgGeneral.add(new EnumSetting.Builder<Rotation>()
        .name("rotate-buffer")
        .description("How to rotate when buffering?")
        .defaultValue(Rotation.Normal)
        .build()
    );

    private final Setting<Boolean> bufferFallback = sgGeneral.add(new BoolSetting.Builder()
        .name("buffer-fallback")
        .description("Fallbacks to throwing without rots in dire need of xp")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing on item use")
        .defaultValue(true)
        .build()
    );

    private int tickCounter;

    public SlowExp() {
        super(TarAddon.CATEGORY, "slow-exp", "Alternative autoexp to get <= 88% of the xp in holes, without depending on player id");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
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
            reset();
            return;
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
                    reset();
                    return;
                }
                case Toggle -> {
                    this.toggle();
                    return;
                }
            }
        }

        State stage = getCycle();
        if (stage == null) return;
        if (stage.cycle > 2) {
            // over 2 players, there is 0 way to get any xp for ourselves
            if (bufferFallback.get()) {
                if (tickCounter % (delay.get() + 1) == 0) {
                    // dont rot, rotation down = we arent gonna get any
                    throwExp(result, frequency.get(), Rotation.None);
                }
                tickCounter++;
            } else {
                error("Too many players colliding for xp!");
                this.toggle();
            }
            return;
        }

        if (stage.count == 0) {
            // normal xp
            if (tickCounter % (delay.get() + 1) == 0) {
                // every n ticks throw
                throwExp(result, frequency.get(), rotate.get());
            }
        } else {
            int normalizedCycle = modPositive((tickCounter - stage.cycle), 3); // dunno how to explain this, just offset the tickcounter by our cycle with 1 tick wait
            // this means that 0 -> our throw
            if (normalizedCycle == 0) throwExp(result, bufferFrequency.get(), rotateBuffer.get());
            if (normalizedCycle == 1 && stage.cycle == 2) throwExp(result, 1, rotateBuffer.get()); // scuffed ass coding due to the negative numbers by tickcounter - cycle
            if (normalizedCycle == 2 && stage.cycle >= 1) throwExp(result, 1, rotateBuffer.get()); // but this will just make it throw if there are more ppl in here
        }

        tickCounter++;
    }

    private void throwExp(FindItemResult result, int amount, Rotation rotation) {
        if (mc.player == null) return;

        Runnable runnable = () -> {
            InvUtils.swap(result.slot(), true);
            int xpCount = mc.player.getInventory().getStack(result.slot()).getCount();

            for (int i = 0; i < amount; i++) {
                if (xpCount - i <= 0) break;
                sendPacket(new PlayerInteractItemC2SPacket(result.getHand(), 0, mc.player.getYaw(), 90));
                if (swing.get()) {
                    mc.player.swingHand(result.getHand());
                }
            }
            InvUtils.swapBack();
        };

        switch (rotation) {
            case Normal -> Rotations.rotate(mc.player.getYaw(), 90, runnable);
            case Silent -> {
                sendRotatePacket(mc.player.getYaw(), 90, RotationPacket.Full);
                runnable.run();
            }
            case None -> runnable.run();
        }
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

    private State getCycle() {
        if (mc.player == null || mc.world == null) return null;
        int cycle = 0; // by default 0, increase by 1 for every waste xp needed
        int count = 0; // colliding players

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
                count++; // countttt
                if (player.getId() < mc.player.getId()) {
                    cycle++;
                }
            }
        }

        return new State(cycle, count);
    }

    private void reset() {
        tickCounter = 0;
    }

    @SuppressWarnings("SameParameterValue")
    private int modPositive(int a, int b) {
        return (a % b + b) % b;
    }

    private enum GuiMode {
        None,
        Toggle,
        Pause
    }

    private enum Rotation {
        Normal,
        Silent,
        None
    }

    private record State(int cycle, int count) {}
}
