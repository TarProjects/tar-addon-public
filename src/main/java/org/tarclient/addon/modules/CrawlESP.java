package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CrawlESP extends TarModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal-radius")
        .description("Horizontal radius in which to search for holes.")
        .defaultValue(10)
        .min(0)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> verticalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-radius")
        .description("Vertical radius in which to search for holes.")
        .defaultValue(5)
        .min(0)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> holeHeight = sgGeneral.add(new IntSetting.Builder()
        .name("min-height")
        .description("Minimum hole height required to be rendered.")
        .defaultValue(3)
        .min(1)
        .sliderMin(1)
        .build()
    );

    private final Setting<Boolean> ignoreOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-own")
        .description("Ignores rendering the hole you are currently standing in.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> ignoreAbove = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-above")
        .description("Ignores rendering the hole if it is above the player's Y level")
        .defaultValue(false)
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

    private final Pool<CrawlHole> holePool = new Pool<>(CrawlHole::new);
    private final List<CrawlHole> holes = new ArrayList<>();


    public CrawlESP() {
        super(TarAddon.CATEGORY, "crawl-esp", "Highlights optimal positions to crawl in");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        holePool.freeAll(holes);
        holes.clear();

        BlockIterator.register(horizontalRadius.get(), verticalRadius.get(), ((pos, blockState) -> {
            if (!isValid(pos, true)) return;

            int bedrock = 0;
            Direction offset = null;

            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP) continue;
                if (direction == Direction.DOWN) {
                    BlockPos down = pos.offset(Direction.DOWN);
                    int downBedrock = 0;
                    for (Direction direction1 : Direction.values()) {
                        if (direction1 == Direction.UP || direction1 == Direction.DOWN) continue;
                        if (getBlock(down.offset(direction1)) == Blocks.BEDROCK) downBedrock++;
                    }
                    if (downBedrock == 4) bedrock++;
                    continue;
                }
                BlockPos offsetPos = pos.offset(direction);
                Block block = mc.world.getBlockState(offsetPos).getBlock();
                if (block == Blocks.BEDROCK) bedrock++;
                else if (isValid(offsetPos, false)) {
                    int sideBedrock = 0;
                    for (Direction direction1 : Direction.values()) {
                        if (direction1 == direction.getOpposite()) continue;
                        if (getBlock(offsetPos.offset(direction1)) == Blocks.BEDROCK) sideBedrock++;
                    }

                    if (sideBedrock != 5) return;

                    offset = direction;
                }
            }

            // TODO: also count cornerclippable positions, but they are difficult
            if (bedrock == 4 && offset != null) {
                holes.add(holePool.get().set(pos, offset));
                holes.add(holePool.get().set(pos.offset(offset), offset.getOpposite()));
            }
        }));
    }

    private Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    private boolean isValid(BlockPos pos, boolean primary) {
        if (ignoreOwn.get() && mc.player.getBlockPos() == pos) return false;
        if (ignoreAbove.get() && mc.player.getBlockY() < pos.getY()) return false;
        // replicate mc.world.getBlockState() but store worldchunk
        WorldChunk worldChunk = mc.world.getChunk(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
        BlockState state = worldChunk.getBlockState(pos);
        Block block = state.getBlock();
        if (primary) {
            if (!state.isAir()) return false;

            for (int i = 0; i < holeHeight.get(); i++) {
                if (!worldChunk.getBlockState(pos.up(i)).isAir()) return false;
            }

            return true;
        } else {
            return block == Blocks.AIR || block == Blocks.OBSIDIAN;
        }
    }


    @EventHandler
    private void onRender(Render3DEvent event) {
        for (CrawlHole hole : holes) hole.render(event.renderer, shapeMode.get());
    }

    private static class CrawlHole {
        public BlockPos.Mutable blockPos = new BlockPos.Mutable();
        public Direction offset;


        public CrawlHole set(BlockPos blockPos, Direction offset) {
            this.blockPos.set(blockPos);
            this.offset = offset;
            return this;
        }

        public void render(Renderer3D renderer, ShapeMode mode) {
            int x = blockPos.getX();
            int y = blockPos.getY();
            int z = blockPos.getZ();

            if (mode.lines()) {
                Color color = getLineColor();
                if (offset != Direction.NORTH) renderer.line(x, y, z, x + 1, y, z, color);
                if (offset != Direction.SOUTH) renderer.line(x, y, z + 1, x + 1, y, z + 1, color);
                if (offset != Direction.WEST) renderer.line(x, y, z, x, y, z + 1, color);
                if (offset != Direction.EAST) renderer.line(x + 1, y, z, x + 1, y, z + 1, color);
            }

            if (mode.sides()) {
                Color color = getSideColor();
                renderer.quad(x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, color);
            }
        }

        private Color getLineColor() {
            return Objects.requireNonNull(Modules.get().get(CrawlESP.class)).lineColor.get();
        }

        private Color getSideColor() {
            return Objects.requireNonNull(Modules.get().get(CrawlESP.class)).sideColor.get();
        }
    }
}
