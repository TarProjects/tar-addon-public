package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.mixin.HandledScreenAccessor;
import org.tarclient.addon.mixin.InventoryScreenAccessor;
import org.tarclient.addon.settings.IntRange;
import org.tarclient.addon.settings.impl.IntRangeListSetting;

import java.util.ArrayList;
import java.util.List;

public class InventoryFixes extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgLoot = this.settings.createGroup("Loot Management");

    // see ArmorSlotMixin
    public final Setting<Boolean> insertSlot = sgGeneral.add(new BoolSetting.Builder()
        .name("bypass-insert-slot")
        .description("Lets you place items on restricted slots")
        .defaultValue(true)
        .build()
    );

    /* --- Loot Management --- */
    public final Setting<Boolean> lootEnabled = sgLoot.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Use loot management features")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> maxDrag = sgLoot.add(new IntSetting.Builder()
        .name("max-drag")
        .description("Maximum slots to drag before forcefully sending packets. Set to 6 on cc")
        .defaultValue(6)
        .sliderRange(0, 27)
        .build()
    );
    private final Setting<Keybind> generic = sgLoot.add(new KeybindSetting.Builder()
        .name("generic")
        .defaultValue(Keybind.none())
        .build()
    );
    private final Setting<Keybind> crystals = sgLoot.add(new KeybindSetting.Builder()
        .name("crystals")
        .defaultValue(Keybind.none())
        .build()
    );
    private final Setting<Keybind> xp = sgLoot.add(new KeybindSetting.Builder()
        .name("experience-bottles")
        .defaultValue(Keybind.none())
        .build()
    );
    private final Setting<Keybind> obsidian = sgLoot.add(new KeybindSetting.Builder()
        .name("obsidian")
        .defaultValue(Keybind.none())
        .build()
    );
    private final Setting<Keybind> gapple = sgLoot.add(new KeybindSetting.Builder()
        .name("gapple")
        .defaultValue(Keybind.none())
        .build()
    );
    private final Setting<Keybind> squash = sgLoot.add(new KeybindSetting.Builder()
        .name("squash")
        .defaultValue(Keybind.none())
        .description("Squashes item stacks that can be squashed into 1")
        .build()
    );
    private final Setting<List<IntRange>> ignoreSlotIDs = sgLoot.add(new IntRangeListSetting.Builder()
        .name("ignore-slot-IDs")
        .description("Slot ranges to ignore when dragging")
        .defaultValue(List.of(new IntRange(36, 44)))
        .min(0)
        .max(45)
        .build()
    );
    private final Setting<List<Item>> ignoreItems = sgLoot.add(new ItemListSetting.Builder()
        .name("ignore-items")
        .description("Slot ranges to ignore when dragging")
        .defaultValue(List.of(Items.TOTEM_OF_UNDYING))
        .build()
    );

    private final static Item SQUASH_ITEM = Items.STRUCTURE_VOID;
    private final static Item GENERIC_ITEM = Items.AIR;

    private final static int OFFHAND_INDEX = 45;

    Item lastPressed = null; // hear me out this is good
    List<Slot> painted = new ArrayList<>();
    Slot source = null;
    Stage stage = null;


    public InventoryFixes() {
        super(TarAddon.CATEGORY, "inventory-fixes", "Modifies inventory behaviour");
    }

    @Override
    public void onActivate() {
        lastPressed = null;
        painted.clear();
        source = null;
        stage = null;
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        update();
    }


    // Passed from HandledScreenMixin.java and update()
    public void onMouseMovement(InventoryScreen screen, Slot hoveredSlot) {
        if (!lootEnabled.get()) return;

        ScreenHandler handler = screen.getScreenHandler();

        Item currentlyPressed = getPressed();

        stage = getStage(currentlyPressed, lastPressed);

        switch (stage) {
            case Press -> {
                if (!handler.getCursorStack().isEmpty()) break;
                if (currentlyPressed == GENERIC_ITEM) {
                    if (canDrag(hoveredSlot)) {
                        source = hoveredSlot;
                        painted.clear();
                    }
                } else if (currentlyPressed == SQUASH_ITEM) {
                    Slot best = findBestSquashSlot(handler);
                    if (best == null) break;

                    // exclude armor, crafting and offhand
                    Slot offhand = handler.getSlot(OFFHAND_INDEX);
                    ItemStack offhandStack = offhand == null ? ItemStack.EMPTY : offhand.getStack().copy();

                    pickupAllFromSlot(best, handler);

                    Slot newOffhand = handler.getSlot(OFFHAND_INDEX);
                    if (newOffhand == null) break;

                    // offhand stack wasn't empty, but now it is
                    if (!offhandStack.isEmpty() && newOffhand.getStack().isEmpty()) {
                        offhand(offhandStack, handler);
                    }
                } else {
                    for (int i = 9; i <= 44; i++) { // reverse search main inv
                        Slot slot = handler.getSlot(i);
                        if (canDrag(slot) && slot.getStack().getItem() == currentlyPressed) {
                            source = slot;
                            painted.clear();

                            // hard fix to a small issue
                            // if you don't move mouse, but you
                            // press a bind, the HOLD stage
                            // won't get called and nothing
                            // will happen. This is why
                            // we assume that the hovered
                            // slot should be painted with the
                            // users choice of items
                            if (hoveredSlot == null ||
                                ItemStack.areItemsEqual(hoveredSlot.getStack(), source.getStack()) ||
                                invalidDragDestination(hoveredSlot)) break;

                            painted.add(hoveredSlot);
                            break;
                        }
                    }
                }
            }

            case Hold -> {
                if (hoveredSlot == null) break;
                if (source == null || hoveredSlot == source) break;
                if (painted.contains(hoveredSlot)) break;
                if (invalidDragDestination(hoveredSlot)) break;

                // Hovering
                if (!ItemStack.areItemsEqual(hoveredSlot.getStack(), source.getStack()))
                    painted.add(hoveredSlot);

                if (painted.size() >= maxDrag.get()) {
                    sendDrag(source, handler);
                }
            }

            case Release -> {
                if (source != null) {
                    sendDrag(source, handler);
                    source = null;
                }
            }
        }

        // save last variables
        lastPressed = getPressed();
    }


    // TODO: dynamic items and binds?
    private Item getPressed() {
        if (lastPressed != null && isKeyPressed(lastPressed)) {
            return lastPressed;
        }

        Item[] priorityOrder = {GENERIC_ITEM, SQUASH_ITEM, Items.END_CRYSTAL, Items.EXPERIENCE_BOTTLE, Items.OBSIDIAN, Items.ENCHANTED_GOLDEN_APPLE};
        for (Item item : priorityOrder) {
            if (isKeyPressed(item)) {
                return item;
            }
        }

        return null;
    }

    private boolean isKeyPressed(Item item) {
        if (item == GENERIC_ITEM) return generic.get().isPressed();
        if (item == SQUASH_ITEM) return squash.get().isPressed();
        if (item == Items.END_CRYSTAL) return crystals.get().isPressed();
        if (item == Items.EXPERIENCE_BOTTLE) return xp.get().isPressed();
        if (item == Items.OBSIDIAN) return obsidian.get().isPressed();
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return gapple.get().isPressed();
        return false;
    }

    private Stage getStage(Item currentlyPressed, Item lastPressed) {
        boolean wasPressed = lastPressed != null;
        boolean isPressed = currentlyPressed != null;

        Stage stage;
        if (isPressed && !wasPressed) stage = Stage.Press;
        else if (isPressed) stage = Stage.Hold;
        else if (wasPressed) stage = Stage.Release;
        else stage = Stage.None;

        return stage;
    }

    private int computeEmptiedStacks(Slot slot, ScreenHandler handler) {
        ItemStack stack = slot.getStack();

        int remaining = stack.getMaxCount() - stack.getCount();

        int emptied = 0;

        for (int i = 1; i <= 44; i++) { // skip offhand because it's not a good heuristic
            Slot s = handler.getSlot(i);

            if (s == null || !s.hasStack()) continue;
            if (slot.id == s.id) continue;

            ItemStack st = s.getStack();

            if (!ItemStack.areItemsAndComponentsEqual(stack, st))
                continue;

            if (st.getCount() <= remaining) {
                emptied++;
            }
        }

        return emptied;
    }

    private Slot findBestSquashSlot(ScreenHandler handler) {
        int bestScore = 0;
        Slot bestSlot = null;

        // reverse search main inventory
        for (int i = 44; i >= 9; i--) {
            Slot slot = handler.getSlot(i);

            if (slot == null || !slot.hasStack())
                continue;

            ItemStack stack = slot.getStack();

            if (!stack.isStackable())
                continue;

            if (stack.getCount() >= stack.getMaxCount())
                continue;

            int score = computeEmptiedStacks(slot, handler);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private boolean invalidDragDestination(Slot slot) {
        if (slot == null) return true;
        for (IntRange range : ignoreSlotIDs.get()) {
            if (slot.id >= range.min && slot.id <= range.max) {
                return true;
            }
        }

        if (slot.hasStack()) {
            return ignoreItems.get().contains(slot.getStack().getItem());
        }
        return false;
    }

    private boolean canDrag(Slot slot) {
        if (mc.player == null || mc.interactionManager == null || slot == null) return false;

        ItemStack sourceStack = slot.getStack();
        if (sourceStack.isEmpty() || sourceStack.getCount() <= 1) return false;
        return !ignoreItems.get().contains(sourceStack.getItem());
    }

    private void offhand(ItemStack offhandStack, ScreenHandler handler) {
        if (mc.player == null || mc.interactionManager == null || handler == null) return;

        for (int j = 9; j <= 44; j++) {
            Slot donorForOffhandSlot = handler.slots.get(j);
            if (donorForOffhandSlot == null || !donorForOffhandSlot.hasStack()) continue;

            ItemStack donorForOffhandStack = donorForOffhandSlot.getStack();

            if (ItemStack.areItemsEqual(offhandStack, donorForOffhandStack)) {
                mc.interactionManager.clickSlot(handler.syncId, donorForOffhandSlot.id, 40, SlotActionType.SWAP, mc.player);
                break;
            }
        }
    }

    private void pickupAllFromSlot(Slot source, ScreenHandler handler) {
        if (mc.player == null || mc.interactionManager == null || handler == null) return;

        mc.interactionManager.clickSlot(handler.syncId, source.id, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, source.id, 0, SlotActionType.PICKUP_ALL, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, source.id, 0, SlotActionType.PICKUP, mc.player);
    }

    private void sendDrag(Slot source, ScreenHandler handler) {
        if (mc.player == null || mc.interactionManager == null || handler == null) return;
        for (Slot paintSlot : painted) {
            ItemStack paintStack = paintSlot.getStack();
            boolean shouldDrop = !paintStack.isEmpty() && (!ItemStack.areItemsEqual(source.getStack(), paintStack) ||
                paintStack.getCount() >= paintStack.getMaxCount());
            if (shouldDrop) {
                mc.interactionManager.clickSlot(handler.syncId, paintSlot.id, 1, SlotActionType.THROW, mc.player);
            }
        }

        mc.interactionManager.clickSlot(handler.syncId, source.id, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, -999, 4, SlotActionType.QUICK_CRAFT, mc.player);

        for (Slot paintSlot : painted) {
            mc.interactionManager.clickSlot(handler.syncId, paintSlot.id, 5, SlotActionType.QUICK_CRAFT, mc.player);
        }

        mc.interactionManager.clickSlot(handler.syncId, -999, 6, SlotActionType.QUICK_CRAFT, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, source.id, 0, SlotActionType.PICKUP, mc.player);

        painted.clear();
    }

    private void update() {
        if (mc.currentScreen instanceof InventoryScreen screen && this.isActive()) {
            float x = ((InventoryScreenAccessor) screen).tar$getMouseX();
            float y = ((InventoryScreenAccessor) screen).tar$getMouseY();
            Slot slot = ((HandledScreenAccessor) screen).tar$getSlotAt(x, y);

            onMouseMovement(screen, slot);
        }
    }

    private enum Stage {
        Press,
        Hold,
        Release,
        None
    }
}
