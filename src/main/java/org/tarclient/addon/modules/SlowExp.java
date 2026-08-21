package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.MioUtils;

import java.util.List;

public class SlowExp extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay of xp when not buffering")
        .defaultValue(0)
        .sliderRange(0, 10)
        .min(0)
        .build()
    );

    private final Setting<Integer> frequency = sgGeneral.add(new IntSetting.Builder()
        .name("frequency")
        .description("Frequency of exp throwing (at yourself, not counting waste)")
        .defaultValue(4)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> bufferDelay = sgGeneral.add(new IntSetting.Builder()
        .name("buffer-delay")
        .description("Delay of buffered throws")
        .defaultValue(0)
        .sliderRange(0, 10)
        .min(0)
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

    private final Setting<Swing> swing = sgGeneral.add(new EnumSetting.Builder<Swing>()
        .name("swing")
        .description("Swings hand depending on mode")
        .defaultValue(Swing.All)
        .build()
    );

    private final Setting<Offhand> offhand = sgGeneral.add(new EnumSetting.Builder<Offhand>()
        .name("offhand")
        .description("How should we use the offhand?")
        .defaultValue(Offhand.Never)
        .build()
    );

    private final Setting<Integer> offhandPercentage = sgGeneral.add(new IntSetting.Builder()
        .name("offhand-percentage")
        .description("Mends the offhand stack to this percentage")
        .defaultValue(30)
        .sliderRange(0, 100)
        .visible(() -> offhand.get() != Offhand.Never)
        .build()
    );

    private final Setting<Double> offhandMinHp = sgGeneral.add(new DoubleSetting.Builder()
        .name("offhand-min-hp")
        .description("Min health for replacing offhand")
        .defaultValue(20)
        .sliderRange(0, 20)
        .visible(() -> offhand.get() == Offhand.Toggle)
        .build()
    );

    public final Setting<List<String>> toCustomOffhand = sgGeneral.add(new StringListSetting.Builder()
        .name("to-custom-offhand")
        .description("How to get to custom offhand")
        .defaultValue("offhand Custom clear", "offhand Custom <armor>", "offhand Item custom")
        .visible(() -> offhand.get() == Offhand.Custom)
        .build()
    );

    public final Setting<List<String>> fromCustomOffhand = sgGeneral.add(new StringListSetting.Builder()
        .name("from-custom-offhand")
        .description("How to get back from custom offhand")
        .defaultValue("offhand Custom clear", "offhand Item crystal")
        .visible(() -> offhand.get() == Offhand.Custom)
        .build()
    );

    private int tickCounter;
    private boolean offhandState;
    private boolean hasCustom;

    public SlowExp() {
        super(TarAddon.CATEGORY, "slow-exp", "Alternative autoexp to get <= 88% of the xp in holes, without depending on player id");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;

        // internal state for offhand options
        offhandState = true;
        hasCustom = false;
    }

    @Override
    public void onDeactivate() {
        if (!offhandState) {
            MioUtils.toggleOffhand(true);
        }

        if (hasCustom) {
            fromCustom();
        }
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

        handleOffhand(mc.player);

        if (!shouldMend(mc.player)) {
            info("Hit durability cap!");
            this.toggle();
            return;
        }

        if (!mc.player.isOnGround()) {
            reset();
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
            // goofy ahh math to do mod in order to loop consistently
            int mod = 3 + bufferDelay.get();
            int normalizedCycle = modPositive((tickCounter - stage.cycle), mod);

            if (normalizedCycle == 0) throwExp(result, bufferFrequency.get(), rotateBuffer.get());
            if (normalizedCycle == mod - 2 && stage.cycle == 2) throwExp(result, 1, rotateBuffer.get());
            if (normalizedCycle == mod - 1 && stage.cycle >= 1) throwExp(result, 1, rotateBuffer.get());
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
                if (swing.get() == Swing.All)
                    mc.player.swingHand(result.getHand());

            }

            if (swing.get() == Swing.Once)
                mc.player.swingHand(result.getHand());

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
        // force mend
        if (!offhandState || hasCustom) return true;

        for (int i = 0; i < 4; i++) {
            int slot = SlotUtils.ARMOR_START + i;
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            if (!Utils.hasEnchantment(stack, Enchantments.MENDING)) continue;

            double percentage = getPercentage(stack);

            if (percentage * 100 < stopAt.get()) return true;
        }

        return false;
    }

    private double getPercentage(ItemStack stack) {
        int maxDmg = stack.getMaxDamage();

        int dmg = stack.getDamage();
        return (double) (maxDmg - dmg) / maxDmg;
    }

    private void handleOffhand(PlayerEntity player) {
        switch (offhand.get()) {
            case Toggle -> {
                if (offhandState) {
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = player.getInventory().getStack(i);
                        if (!validArmor(stack) || invalidPercentageOffhand(stack)) continue;

                        MioUtils.toggleOffhand(false);
                        offhandState = false;

                        InvUtils.quickSwap().from(i).toOffhand();
                        break;
                    }
                } else {
                    ItemStack offhandStack = player.getInventory().getStack(SlotUtils.OFFHAND);
                    if (player.getHealth() < offhandMinHp.get() || (validArmor(offhandStack) && invalidPercentageOffhand(offhandStack))) {
                        MioUtils.toggleOffhand(true);
                        offhandState = true;
                    }
                }
            }

            case Custom -> {
                if (!hasCustom) {
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = player.getInventory().getStack(i);
                        if (!validArmor(stack) || invalidPercentageOffhand(stack)) continue;

                        toCustom(stack);
                        hasCustom = true;
                        break;
                    }
                } else {
                    ItemStack offhandStack = player.getInventory().getStack(SlotUtils.OFFHAND);
                    if (validArmor(offhandStack) && invalidPercentageOffhand(offhandStack)) {
                        fromCustom();
                        hasCustom = false;
                    }
                }
            }

        }
    }

    private boolean validArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!Utils.hasEnchantment(stack, Enchantments.MENDING)) return false;
        // strict armor check
        return stack.getComponents().contains(DataComponentTypes.EQUIPPABLE);
    }

    private boolean invalidPercentageOffhand(ItemStack stack) {
        double percentage = getPercentage(stack);
        return !(percentage * 100 <= offhandPercentage.get());
    }

    private void toCustom(ItemStack stack) {
        String itemName = Registries.ITEM.getId(stack.getItem()).getPath();
        for (String command : toCustomOffhand.get()) {
            if (!command.isEmpty()) MioUtils.sendMioMessage(command.replace("<armor>", itemName));
        }
    }

    private void fromCustom() {
        for (String command : fromCustomOffhand.get()) {
            if (!command.isEmpty()) MioUtils.sendMioMessage(command);
        }
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

    private enum Swing {
        None,
        All,
        Once
    }

    private enum Offhand {
        Never,
        Toggle,
        Custom
    }

    private record State(int cycle, int count) {}
}
