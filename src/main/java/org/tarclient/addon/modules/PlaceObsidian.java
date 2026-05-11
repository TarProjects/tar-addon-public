package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
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
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.TarBlockUtils;

import java.util.List;

public class PlaceObsidian extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of block placements")
        .defaultValue(5.2)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<List<String>> preChat = sgGeneral.add(new StringListSetting.Builder()
        .name("pre-chat")
        .description("Chat commands to send each tick in its own line")
        .defaultValue("")
        .build()
    );

    private final Setting<List<String>> postChat = sgGeneral.add(new StringListSetting.Builder()
        .name("post-chat")
        .description("Chat commands to send each tick in its own line")
        .defaultValue("")
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
        for (String message : postChat.get()) {
            if (!message.isEmpty()) {
                ChatUtils.sendPlayerMsg(message, false);
            }
        }
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
            for (String message : preChat.get()) {
                if (!message.isEmpty()) {
                    ChatUtils.sendPlayerMsg(message, false);
                }
            }
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



