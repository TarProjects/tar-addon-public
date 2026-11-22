package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.*;
import java.util.stream.Collectors;

public class AutoDupe extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance of interactions & search sphere")
        .defaultValue(3)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Integer> maxFrames = sgGeneral.add(new IntSetting.Builder()
        .name("max-frames")
        .description("How many item frames to dupe on")
        .defaultValue(4)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout")
        .description("Timeout on dupe failure")
        .defaultValue(100)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Every item to dupe")
        .defaultValue(Items.SHULKER_BOX)
        .build()
    );

    private final Map<UUID, FrameState> frameMap = new HashMap<>();

    private int tick;

    public AutoDupe() {
        super(TarAddon.CATEGORY, "auto-dupe", "Does the item frame dupe automatically");
    }

    @Override
    public void onActivate() {
        tick = 0;
        frameMap.clear();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.interactionManager == null || mc.player == null) return;
        // stale cleanup here
        frameMap.entrySet().removeIf(entry -> tick - entry.getValue().startTick() > timeout.get());

        List<ItemFrameEntity> entities = getClosest();
        if (entities.isEmpty()) return;

        for (ItemFrameEntity entity : entities) {
            if (!entity.isAlive() || entity.isRemoved()) continue;
            FrameState state = frameMap.get(entity.getUuid());
            if (state != null) {
                // wait until match!
                boolean hasStack = !entity.getHeldItemStack().isEmpty();
                if (hasStack == state.shouldHaveItem()) {
                    // expected, good!
                    frameMap.remove(entity.getUuid());
                } else {
                    // not good, still not synced
                    continue;
                }
            }
            // safely handle placing etc
            if (entity.getHeldItemStack().isEmpty()) {
                // empty, place!
                FindItemResult item = InvUtils.findInHotbar((stack -> items.get().contains(stack.getItem())));
                if (!item.found()) return;

                double yaw = Rotations.getYaw(entity);
                double pitch = Rotations.getPitch(entity);
                sendRotatePacket(yaw, pitch, RotationPacket.Full);

                InvUtils.swap(item.slot(), true);
                sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, false, item.getHand(), Vec3d.ZERO));
                // pseudo decrement to not duplicate use!
                mc.player.getStackInHand(item.getHand()).decrementUnlessCreative(1, mc.player);
                InvUtils.swapBack();

                frameMap.put(entity.getUuid(), new FrameState(true, tick));
            } else {
                // break
                double yaw = Rotations.getYaw(entity);
                double pitch = Rotations.getPitch(entity);
                sendRotatePacket(yaw, pitch, RotationPacket.Full);

                sendPacket(PlayerInteractEntityC2SPacket.attack(entity, false));
                mc.player.swingHand(Hand.MAIN_HAND);

                frameMap.put(entity.getUuid(), new FrameState(false, tick));
            }

        }

        tick++;
    }

    private List<ItemFrameEntity> getClosest() {
        if (mc.world == null || mc.player == null) return new ArrayList<>();
        List<ItemFrameEntity> itemFrames = new ArrayList<>();

        // TODO: dont do 2 lists, instead stream from entities directly!
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) continue;
            if (mc.player.getEyePos().squaredDistanceTo(entity.getEntityPos()) > range.get() * range.get()) continue;
            itemFrames.add(frame);
        }

        return itemFrames.stream()
            .sorted(Comparator.comparingDouble(e -> e.getEntityPos().squaredDistanceTo(mc.player.getEntityPos())))
            .limit(maxFrames.get())
            .collect(Collectors.toList());
    }

    private record FrameState(boolean shouldHaveItem, int startTick) {}
}
