package com.lorefinder;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

public class IllegalsFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChecks = settings.createGroup("Checks");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Chunk radius to scan (smaller = much better FPS). Not the same as render distance.")
        .defaultValue(4)
        .range(1, 16)
        .sliderRange(2, 8)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many chunks to scan per tick. Keep at 1–2 on join / low-end PCs.")
        .defaultValue(1)
        .range(1, 8)
        .sliderMax(4)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("Re-queue all chunks in range every N ticks (0 = only scan new chunks).")
        .defaultValue(400)
        .range(0, 2400)
        .sliderRange(100, 1200)
        .build()
    );

    private final Setting<Integer> maxMarkers = sgGeneral.add(new IntSetting.Builder()
        .name("max-markers")
        .description("Maximum illegal blocks to track at once.")
        .defaultValue(256)
        .range(32, 4096)
        .sliderRange(64, 512)
        .build()
    );

    private final Setting<Boolean> invalidPlacement = sgChecks.add(new BoolSetting.Builder()
        .name("invalid-placement")
        .description("Blocks that cannot be placed on their support (e.g. flowers on gravel, saplings on stone).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> floatingLava = sgChecks.add(new BoolSetting.Builder()
        .name("floating-lava")
        .description("Lava with no solid block beneath.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> floatingWater = sgChecks.add(new BoolSetting.Builder()
        .name("floating-water")
        .description("Water source blocks with no solid block beneath.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> orphanBeds = sgChecks.add(new BoolSetting.Builder()
        .name("orphan-beds")
        .description("Bed halves missing their partner.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> orphanDoubleBlocks = sgChecks.add(new BoolSetting.Builder()
        .name("orphan-double-blocks")
        .description("Tall plants missing their upper or lower half.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> floatingAttached = sgChecks.add(new BoolSetting.Builder()
        .name("floating-attached")
        .description("Torches, ladders, levers, buttons, lanterns, etc. without valid support.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How boxes are drawn.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("Fill opacity for boxes.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines)
        .defaultValue(35)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color.")
        .defaultValue(new SettingColor(220, 70, 70, 35))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color.")
        .defaultValue(new SettingColor(255, 100, 100, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to illegal blocks.")
        .defaultValue(false)
        .build()
    );

    private final Set<BlockPos> markers = new HashSet<>();
    private final Queue<ChunkPos> scanQueue = new ArrayDeque<>();
    private final Set<ChunkPos> queued = new HashSet<>();
    private int rescanTimer;
    private double maxDistSq;

    public IllegalsFinder() {
        super(LoreFinderAddon.CATEGORY, "illegals-finder", "ESP for illegal block states and placements in render distance.");
    }

    @Override
    public void onActivate() {
        markers.clear();
        scanQueue.clear();
        queued.clear();
        rescanTimer = 0;
        updateMaxDistSq();
        queueChunksInRange();
    }

    @Override
    public void onDeactivate() {
        markers.clear();
        scanQueue.clear();
        queued.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.player == null) return;

        updateMaxDistSq();

        pruneMarkers();

        int budget = chunksPerTick.get();
        while (budget-- > 0 && !scanQueue.isEmpty()) {
            ChunkPos chunkPos = scanQueue.poll();
            queued.remove(chunkPos);

            if (!IllegalBlockChecks.isChunkBorderContextLoaded(mc.world, chunkPos)) {
                enqueueChunk(chunkPos);
                continue;
            }

            WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
            if (chunk == null || chunk.isEmpty()) continue;

            scanChunk(chunk);
            if (markers.size() >= maxMarkers.get()) {
                scanQueue.clear();
                queued.clear();
                break;
            }
        }

        if (scanInterval.get() > 0 && ++rescanTimer >= scanInterval.get()) {
            rescanTimer = 0;
            queueChunksInRange();
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!Utils.canUpdate()) return;
        enqueueChunk(event.chunk().getPos());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!Utils.canUpdate() || markers.isEmpty()) return;

        SettingColor side = new SettingColor(sideColor.get());
        side.a = fillOpacity.get();
        SettingColor line = lineColor.get();

        Iterator<BlockPos> it = markers.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockState state = mc.world.getBlockState(pos);

            if (!isIllegalAt(pos, state)) {
                it.remove();
                continue;
            }

            if (tracers.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    line
                );
            }

            RenderUtils.renderTickingBlock(pos, side, line, shapeMode.get(), 0, 8, true, false);
        }
    }

    @Override
    public String getInfoString() {
        if (!scanQueue.isEmpty()) return markers.size() + "+" + scanQueue.size();
        return Integer.toString(markers.size());
    }

    private void updateMaxDistSq() {
        int radius = scanRadius.get() * 16;
        maxDistSq = radius * radius;
    }

    private void queueChunksInRange() {
        if (mc.player == null) return;

        ChunkPos playerChunk = mc.player.getChunkPos();
        int radius = scanRadius.get();

        for (int cx = playerChunk.x - radius; cx <= playerChunk.x + radius; cx++) {
            for (int cz = playerChunk.z - radius; cz <= playerChunk.z + radius; cz++) {
                enqueueChunk(new ChunkPos(cx, cz));
            }
        }
    }

    private void enqueueChunk(ChunkPos chunkPos) {
        if (mc.player == null) return;

        int dx = Math.abs(chunkPos.x - mc.player.getChunkPos().x);
        int dz = Math.abs(chunkPos.z - mc.player.getChunkPos().z);
        if (dx > scanRadius.get() || dz > scanRadius.get()) return;

        if (queued.add(chunkPos)) {
            scanQueue.offer(chunkPos);
        }
    }

    private void pruneMarkers() {
        Iterator<BlockPos> it = markers.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockState state = mc.world.getBlockState(pos);

            if (state.isAir()) {
                it.remove();
                continue;
            }

            if (PlayerUtils.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistSq) {
                it.remove();
                continue;
            }

            if (!isIllegalAt(pos, state)) it.remove();
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (!Utils.canUpdate() || markers.size() >= maxMarkers.get()) return;

        ChunkPos chunkPos = chunk.getPos();
        int baseX = chunkPos.getStartX();
        int baseZ = chunkPos.getStartZ();

        boolean checkInvalid = invalidPlacement.get();
        boolean checkLava = floatingLava.get();
        boolean checkWater = floatingWater.get();
        boolean checkBeds = orphanBeds.get();
        boolean checkDouble = orphanDoubleBlocks.get();
        boolean checkAttached = floatingAttached.get();

        ChunkSection[] sections = chunk.getSectionArray();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (!IllegalBlockChecks.sectionMightContainIllegal(
                section, checkInvalid, checkLava, checkWater, checkBeds, checkDouble, checkAttached
            )) {
                continue;
            }

            int sectionBottom = chunk.sectionIndexToCoord(sectionIndex);

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (markers.size() >= maxMarkers.get()) return;

                        BlockState sectionState = section.getBlockState(x, y, z);
                        if (sectionState.isAir()) continue;

                        int wx = baseX + x;
                        int wy = sectionBottom + y;
                        int wz = baseZ + z;

                        double distSq = PlayerUtils.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                        if (distSq > maxDistSq) continue;

                        BlockPos pos = new BlockPos(wx, wy, wz);
                        if (!IllegalBlockChecks.isContextLoaded(mc.world, pos)) continue;

                        BlockState state = mc.world.getBlockState(pos);
                        if (state.isAir() || state.getBlock() != sectionState.getBlock()) continue;

                        if (isIllegalAt(pos, state)) {
                            markers.add(pos.toImmutable());
                        }
                    }
                }
            }
        }
    }

    private boolean isIllegalAt(BlockPos pos, BlockState state) {
        return IllegalBlockChecks.isIllegal(
            mc.world,
            pos,
            state,
            invalidPlacement.get(),
            floatingLava.get(),
            floatingWater.get(),
            orphanBeds.get(),
            orphanDoubleBlocks.get(),
            floatingAttached.get()
        );
    }
}
