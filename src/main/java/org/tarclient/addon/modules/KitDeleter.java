package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.*;

public class KitDeleter extends TarModule {
    final List<String> kits = new ArrayList<>();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgOther = this.settings.createGroup("Other", false);
    private final Setting<Integer> maxKits = sgGeneral.add(new IntSetting.Builder()
        .name("max-kits")
        .description("How many kits to show")
        .defaultValue(10)
        .sliderRange(1, 120)
        .build()
    );
    private final Setting<Integer> displayLength = sgGeneral.add(new IntSetting.Builder()
        .name("display-length")
        .description("How many characters to display on this screen of each kit")
        .defaultValue(30)
        .sliderRange(5, 245)
        .build()
    );
    private final Setting<Integer> deletionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("deletion-delay")
        .description("How many ticks to wait between deletion")
        .defaultValue(30)
        .sliderRange(10, 200)
        .build()
    );
    /* --- Other --- */
    private final Setting<Formatting> kitNameFormatting = sgOther.add(new EnumSetting.Builder<Formatting>()
        .name("kit-name-formatting")
        .description("What is the formatting used on kit names?")
        .defaultValue(Formatting.GREEN)
        .build()
    );
    private final Setting<List<String>> ignoredKits = sgGeneral.add(new StringListSetting.Builder()
        .name("ignored-kits")
        .description("Which kits to ignore")
        .defaultValue("SJBody", "864", "39Y")
        .build()
    );
    boolean shouldDelete = false;
    int counter = 0;

    public KitDeleter() {
        super(TarAddon.CATEGORY, "kit-deleter", "Deletes latest kit");
    }

    public static Set<TextColor> getTextColors(Text textComponent) {
        Set<TextColor> colors = new HashSet<>();
        Deque<Text> queue = new ArrayDeque<>();
        queue.add(textComponent);

        while (!queue.isEmpty()) {
            Text current = queue.poll();
            TextColor color = current.getStyle().getColor();
            if (color != null) {
                colors.add(color);
            }

            queue.addAll(current.getSiblings());
        }
        return colors;
    }

    @Override
    public void onActivate() {
        kits.clear();
        shouldDelete = false;
        counter = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof InventoryS2CPacket packet) {
            mc.execute(() -> {
                if (packet.contents().stream().anyMatch(itemStack ->
                    ignoredKits.get().contains(itemStack.getName().getString())))
                    return;

                for (ItemStack stack : packet.contents()) {
                    if (getTextColors(stack.getFormattedName()).contains(TextColor.fromFormatting(kitNameFormatting.get()))) {
                        if (kits.contains(stack.getName().getString())) continue;
                        kits.add(stack.getName().getString());
                    }
                }
            });
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            this.toggle();
            return;
        }

        if (shouldDelete) {
            if (counter >= deletionDelay.get()) {
                if (kits.isEmpty()) {
                    info("No kits in queue, disabling!");
                    shouldDelete = false;
                    counter = 0;
                    return;
                }

                String last = kits.removeLast();
                deleteKit(last);
                counter = 0;
            } else {
                counter++;
            }
        }
    }


    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        fillTable(theme, table);

        return table;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        int kitCount = 0;

        for (String kit : kits) {
            if (kitCount >= maxKits.get()) break;
            int maxChar = Math.min(displayLength.get(), kit.length());
            table.add(theme.label(kit.substring(0, maxChar)));
            WMinus delete = table.add(theme.minus()).expandCellX().right().widget();
            delete.action = () -> {
                kits.remove(kit);
                deleteKit(kit);
                table.clear();
                fillTable(theme, table);
            };
            table.row();
            kitCount++;
        }

        WButton deleteFirst = table.add(theme.button("Delete First")).expandCellX().right().widget();
        deleteFirst.action = () -> {
            if (kits.isEmpty()) {
                error("No kits to remove!");
                return;
            }
            String first = kits.removeFirst();
            deleteKit(first);

            table.clear();
            fillTable(theme, table);
        };

        WButton deleteLast = table.add(theme.button("Delete Last")).expandCellX().right().widget();
        deleteLast.action = () -> {
            if (kits.isEmpty()) {
                error("No kits to remove!");
                return;
            }

            String last = kits.removeLast();
            deleteKit(last);

            table.clear();
            fillTable(theme, table);
        };

        WButton startDeletion = table.add(theme.button("Start Deletion")).expandCellX().right().widget();
        startDeletion.action = () -> {
            shouldDelete = true;
            counter = 0;

            table.clear();
            fillTable(theme, table);
        };

        WButton stopDeletion = table.add(theme.button("Stop Deletion")).expandCellX().right().widget();
        stopDeletion.action = () -> {
            shouldDelete = false;
            counter = 0;

            table.clear();
            fillTable(theme, table);
        };

        WButton refresh = table.add(theme.button("Refresh")).expandCellX().right().widget();
        refresh.action = () -> {
            table.clear();
            fillTable(theme, table);
        };


        WButton clear = table.add(theme.button("Clear All")).right().widget();
        clear.action = () -> {
            kits.clear();

            table.clear();
            fillTable(theme, table);
        };
    }

    private void deleteKit(String kit) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand("deleteukit " + kit);
        }
    }
}
