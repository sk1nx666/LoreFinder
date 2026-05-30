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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SignFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOldSign = settings.createGroup("Old Sign Finder");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to rescan signs in loaded chunks, in ticks.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> oldSignFinder = sgOldSign.add(new BoolSetting.Builder()
        .name("old-sign-finder")
        .description("Only highlight signs whose text contains dates matching the year filter.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> yearThreshold = sgOldSign.add(new IntSetting.Builder()
        .name("year-threshold")
        .description("Reference year for comparisons (2010–current year; e.g. 2025 + Any before = signs with a year before 2025).")
        .defaultValue(2025)
        .range(SignYearParser.MIN_YEAR, 2100)
        .sliderRange(SignYearParser.MIN_YEAR, SignYearParser.maxYear())
        .visible(oldSignFinder::get)
        .build()
    );

    private final Setting<SignYearParser.CompareMode> compareMode = sgOldSign.add(new EnumSetting.Builder<SignYearParser.CompareMode>()
        .name("compare-mode")
        .description("How parsed years are compared to the threshold.")
        .defaultValue(SignYearParser.CompareMode.AnyBefore)
        .visible(oldSignFinder::get)
        .build()
    );

    private final Setting<Boolean> includeNoYear = sgOldSign.add(new BoolSetting.Builder()
        .name("include-no-year")
        .description("Also highlight signs with no detectable year in the text.")
        .defaultValue(false)
        .visible(oldSignFinder::get)
        .build()
    );

    private final Setting<Boolean> bothSides = sgOldSign.add(new BoolSetting.Builder()
        .name("both-sides")
        .description("Read front and back text on waxed signs.")
        .defaultValue(true)
        .visible(oldSignFinder::get)
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
        .defaultValue(40)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color.")
        .defaultValue(new SettingColor(120, 180, 255, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color.")
        .defaultValue(new SettingColor(120, 180, 255, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to matching signs.")
        .defaultValue(false)
        .build()
    );

    private final Set<BlockPos> matches = new HashSet<>();
    private int tickTimer;

    public SignFinder() {
        super(LoreFinderAddon.CATEGORY, "sign-finder", "ESP for signs matching Old Sign Finder filters.");
    }

    @Override
    public void onActivate() {
        matches.clear();
        tickTimer = scanInterval.get();
        clampYearThreshold();
        scan();
    }

    private void clampYearThreshold() {
        int max = SignYearParser.maxYear();
        int value = yearThreshold.get();
        if (value > max) yearThreshold.set(max);
        if (value < SignYearParser.MIN_YEAR) yearThreshold.set(SignYearParser.MIN_YEAR);
    }

    private int effectiveThreshold() {
        return Math.min(Math.max(yearThreshold.get(), SignYearParser.MIN_YEAR), SignYearParser.maxYear());
    }

    @Override
    public void onDeactivate() {
        matches.clear();
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
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!Utils.canUpdate() || matches.isEmpty()) return;

        SettingColor side = new SettingColor(sideColor.get());
        side.a = fillOpacity.get();
        SettingColor line = lineColor.get();

        for (BlockPos pos : new HashSet<>(matches)) {
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
        return Integer.toString(matches.size());
    }

    private void scan() {
        if (!Utils.canUpdate()) return;

        Set<BlockPos> found = new HashSet<>();
        double maxDistSq = Math.pow(mc.options.getClampedViewDistance() * 16.0, 2);

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof SignBlockEntity sign)) continue;

            BlockPos pos = sign.getPos();
            if (PlayerUtils.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistSq) {
                continue;
            }

            if (shouldHighlight(sign)) {
                found.add(pos.toImmutable());
            }
        }

        matches.clear();
        matches.addAll(found);
    }

    private void scanChunk(WorldChunk chunk) {
        if (!Utils.canUpdate()) return;

        double maxDistSq = Math.pow(mc.options.getClampedViewDistance() * 16.0, 2);

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof SignBlockEntity sign)) continue;

            BlockPos pos = sign.getPos();
            if (PlayerUtils.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistSq) {
                continue;
            }

            if (shouldHighlight(sign)) mark(pos);
            else unmark(pos);
        }
    }

    private boolean shouldHighlight(SignBlockEntity sign) {
        if (!oldSignFinder.get()) return true;

        List<Integer> years = collectYears(sign);
        if (years.isEmpty()) return includeNoYear.get();

        return SignYearParser.matches(years, effectiveThreshold(), compareMode.get());
    }

    private List<Integer> collectYears(SignBlockEntity sign) {
        List<Integer> years = new ArrayList<>();
        appendYears(years, sign.getFrontText());

        if (bothSides.get()) {
            appendYears(years, sign.getBackText());
        }

        return years;
    }

    private void appendYears(List<Integer> years, SignText signText) {
        if (signText == null) return;

        for (int line = 0; line < 4; line++) {
            Text message = signText.getMessage(line, false);
            if (message == null) continue;

            years.addAll(SignYearParser.parseYears(message.getString()));
        }
    }

    private void mark(BlockPos pos) {
        matches.add(pos.toImmutable());
    }

    private void unmark(BlockPos pos) {
        matches.remove(pos);
        if (pos != null) matches.remove(pos.toImmutable());
    }
}
