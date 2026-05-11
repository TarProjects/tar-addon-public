package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class DropKit extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Target range")
        .defaultValue(3)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );
    Stage stage;
    int counter;
    private PlayerEntity target;

    public DropKit() {
        super(TarAddon.CATEGORY, "drop-kit", "Utility tool that drops your entire inventory step by step");
    }


    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) {
            this.toggle();
            return;
        }

        target = TargetUtils.getPlayerTarget(range.get(), priority.get());
        if (target == null) {
            error("No good target!");
            this.toggle();
            return;
        }
        counter = 0;
        stage = Stage.DropArmor;
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            this.toggle();
            return;
        }

        if (TargetUtils.isBadTarget(target, range.get())) {
            error("No good target!");
            this.toggle();
            return;
        }

        switch (stage) {
            case DropArmor -> {
                // skip ticks
                if (counter % 2 == 0) {
                    int realCounter = counter / 2;
                    Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body), () -> {
                        int slot = SlotUtils.ARMOR_START + realCounter;
                        if (!mc.player.getInventory().getStack(slot).isEmpty()) {
                            InvUtils.drop().slotArmor(realCounter);
                        }
                    });
                    if (realCounter == 3) {
                        stage = Stage.WaitForArmor;
                        counter = 0;
                        return;
                    }
                }
                counter++;
            }

            case WaitForArmor -> {
                int notEmpty = 0;
                for (EquipmentSlot slot : AttributeModifierSlot.ARMOR) {
                    ItemStack stack = target.getEquippedStack(slot);

                    if (!stack.isEmpty()) notEmpty++;
                }

                if (notEmpty == 4) {
                    info("Player wearing armor!");
                    stage = Stage.DropOffhand;
                    counter = 0;
                }
            }

            case DropOffhand -> {
                InvUtils.drop().slotOffhand();

                stage = Stage.WaitForOffhand;
                counter = 0;
            }

            case WaitForOffhand -> {
                ItemStack offhand = target.getEquippedStack(EquipmentSlot.OFFHAND);

                if (!offhand.isEmpty()) {
                    info("Player wearing offhand!");
                    stage = Stage.DropInventory;
                    counter = 0;
                }
            }

            case DropInventory -> {
                if (counter % 2 == 0) {
                    int realCounter = counter / 2;
                    Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body), () -> {
                        int slot = SlotUtils.HOTBAR_START + realCounter;
                        if (!mc.player.getInventory().getStack(slot).isEmpty()) {
                            InvUtils.drop().slot(slot);
                        }
                    });
                    if (realCounter == SlotUtils.MAIN_END) {
                        info("Dropped everything!");
                        this.toggle();
                        return;
                    }
                }
                counter++;
            }
        }

    }

    enum Stage {
        DropArmor,
        WaitForArmor,
        DropOffhand,
        WaitForOffhand,
        DropInventory
    }
}
