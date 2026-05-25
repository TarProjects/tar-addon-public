package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.MioUtils;
import org.tarclient.addon.utils.TarBlockUtils;

import static org.tarclient.addon.utils.MiningUtils.attackWithCompatibility;
import static org.tarclient.addon.utils.MiningUtils.getBreakingBlockPos;

public class PlaceObsidian extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Boolean> clickBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("click-block")
        .description("If should click block that is being placed")
        .defaultValue(true)
        .build()
    );

    boolean hasSent = false;

    public PlaceObsidian() {
        super(TarAddon.CATEGORY, "place-obsidian", "Places obsidian silently");
    }

    @Override
    public void onActivate() {
        hasSent = false;
    }

    @Override
    public void onDeactivate() {
        MioUtils.enableAttackingModules();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        FindItemResult obby = InvUtils.find(Items.OBSIDIAN);
        if (!obby.found()) {
            error("No items found!");
            this.toggle();
            return;
        }

        if (!hasSent) {
            MioUtils.disableAttackingModules();
            hasSent = true;
            return;
        }

        HitResult hitResult = TarBlockUtils.raycastBlocks(range.get());
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            error("You need to face a block!");
            this.toggle();
            return;
        }

        BlockHitResult bhr = (BlockHitResult) hitResult;

        BlockPos target = bhr.getBlockPos().offset(bhr.getSide());
        BlockPos lastBroken = getBreakingBlockPos();
        if (clickBlock.get() && (lastBroken == null || !getBreakingBlockPos().equals(target))) {
            attackWithCompatibility(target, bhr.getSide());
        }

        swap(obby.slot());

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, bhr);
        if (result.isAccepted()) mc.player.swingHand(Hand.OFF_HAND);

        swap(obby.slot());

        this.toggle();
    }

    private void swap(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 40, SlotActionType.SWAP, mc.player);
    }
}



