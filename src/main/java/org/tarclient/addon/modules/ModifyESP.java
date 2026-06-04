package org.tarclient.addon.modules;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;
import org.tarclient.addon.utils.DuelChangeUtils;

public class ModifyESP extends TarModule {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range where blocks will be rendered")
        .defaultValue(10)
        .min(0)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> airRange = sgGeneral.add(new IntSetting.Builder()
        .name("air-range")
        .description("Range where air blocks will be rendered")
        .defaultValue(10)
        .min(0)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> airY = sgGeneral.add(new IntSetting.Builder()
        .name("air-y-level")
        .description("Y level of rendered air blocks. Set to 0 to disable")
        .defaultValue(10)
        .sliderRange(0, 128)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );

    private final Setting<SettingColor> airSideColor = sgRender.add(new ColorSetting.Builder()
        .name("air-side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70))
        .build()
    );

    private final Setting<SettingColor> airLineColor = sgRender.add(new ColorSetting.Builder()
        .name("air-line-color")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );
    // cheap af
    private final LongOpenHashSet packedAirBlocks = new LongOpenHashSet();

    String arena = null;
    int blockX, blockZ;

    public ModifyESP() {
        super(TarAddon.CATEGORY, "modify-esp", "Highlights modified blocks");
    }


    @Override
    public void onActivate() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

        arena = null;
        packedAirBlocks.clear();
        blockX = mc.player.getBlockX();
        blockZ = mc.player.getBlockZ();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) {
            arena = null;
            return;
        }
        arena = mc.world.getRegistryKey().getValue().getPath();
        if (arena.isEmpty()) arena = null; // dont render...

        packedAirBlocks.clear();

        if (airY.get() == 0) return;

        blockX = mc.player.getBlockX();
        blockZ = mc.player.getBlockZ();

        for (int dx = -airRange.get(); dx <= airRange.get(); dx++) {
            for (int dz = -airRange.get(); dz <= airRange.get(); dz++) {
                int x = blockX + dx;
                int z = blockZ + dz;
                BlockPos pos = new BlockPos(x, airY.get(), z);

                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir() || state.getBlock() == Blocks.LIGHT) {
                    long packed = ((long) dx & 0xFFFF) << 32 | ((long) dz & 0xFFFF); // 16 bits each, xz
                    packedAirBlocks.add(packed);
                }
            }
        }

    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (arena == null || mc.player == null) return;


        int blockY = airY.get();

        for (long packed : packedAirBlocks) {
            int dx = (short) (packed >> 32);    // short restores sign
            int dz = (short) (packed & 0xFFFF);
            int x = blockX + dx;
            int z = blockZ + dz;

            event.renderer.box(x, blockY, z, x + 1, blockY + 1, z + 1, airSideColor.get(), airLineColor.get(), shapeMode.get(), 0);
        }

        DuelChangeUtils.getArenaBlocks(arena).forEach((pos) -> {
            if (mc.player == null) return;
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return;
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        });
    }
}
