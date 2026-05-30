package com.lorefinder;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AncientBuildsFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to look for in each chunk.")
        .defaultValue(
            Blocks.MOSSY_COBBLESTONE,
            Blocks.COBBLESTONE,
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.OAK_PLANKS,
            Blocks.OAK_LOG,
            Blocks.OAK_SIGN,
            Blocks.OAK_WALL_SIGN,
            Blocks.TORCH
        )
        .build()
    );

    private final Setting<Integer> requiredTypes = sgGeneral.add(new IntSetting.Builder()
        .name("required-types")
        .description("How many different blocks from the list must appear in a chunk.")
        .defaultValue(1)
        .min(1)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to rescan loaded chunks, in ticks.")
        .defaultValue(40)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How chunk outlines are drawn.")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("Fill opacity for chunk boxes.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines)
        .defaultValue(25)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color.")
        .defaultValue(new SettingColor(72, 140, 90, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color.")
        .defaultValue(new SettingColor(95, 175, 115, 255))
        .build()
    );

    private final Set<ChunkPos> matchingChunks = new HashSet<>();
    private int tickTimer;

    public AncientBuildsFinder() {
        super(LoreFinderAddon.CATEGORY, "ancient-builds-finder", "Highlights chunks containing ancient build blocks.");
    }

    @Override
    public void onActivate() {
        matchingChunks.clear();
        tickTimer = scanInterval.get();
        scan();
    }

    @Override
    public void onDeactivate() {
        matchingChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) return;

        if (++tickTimer < scanInterval.get()) return;
        tickTimer = 0;
        scan();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!Utils.canUpdate()) return;

        WorldChunk chunk = event.chunk();
        ChunkPos pos = chunk.getPos();

        if (chunkMatches(chunk)) matchingChunks.add(pos);
        else matchingChunks.remove(pos);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!Utils.canUpdate() || matchingChunks.isEmpty()) return;

        SettingColor side = new SettingColor(sideColor.get());
        side.a = fillOpacity.get();
        SettingColor line = lineColor.get();

        int bottom = mc.world.getBottomY();
        int top = mc.world.getTopYInclusive();

        for (ChunkPos chunkPos : new HashSet<>(matchingChunks)) {
            double x1 = chunkPos.getStartX();
            double z1 = chunkPos.getStartZ();
            double x2 = x1 + 16;
            double z2 = z1 + 16;

            event.renderer.box(x1, bottom, z1, x2, top, z2, side, line, shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return Integer.toString(matchingChunks.size());
    }

    private void scan() {
        if (!Utils.canUpdate() || mc.player == null) return;

        List<Block> targetBlocks = blocks.get();
        if (targetBlocks.isEmpty()) {
            matchingChunks.clear();
            return;
        }

        matchingChunks.clear();

        int viewDistance = mc.options.getClampedViewDistance();
        ChunkPos playerChunk = mc.player.getChunkPos();

        for (int cx = playerChunk.x - viewDistance; cx <= playerChunk.x + viewDistance; cx++) {
            for (int cz = playerChunk.z - viewDistance; cz <= playerChunk.z + viewDistance; cz++) {
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                if (chunk == null || chunk.isEmpty()) continue;

                if (chunkMatches(chunk)) {
                    matchingChunks.add(chunk.getPos());
                }
            }
        }
    }

    private boolean chunkMatches(WorldChunk chunk) {
        List<Block> targetBlocks = blocks.get();
        if (targetBlocks.isEmpty()) return false;

        int required = Math.min(requiredTypes.get(), targetBlocks.size());
        int found = 0;

        for (Block block : targetBlocks) {
            if (!chunkContainsBlock(chunk, block)) continue;

            found++;
            if (found >= required) return true;
        }

        return false;
    }

    private boolean chunkContainsBlock(WorldChunk chunk, Block block) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            if (section.hasAny(state -> state.isOf(block))) return true;
        }

        return false;
    }
}
