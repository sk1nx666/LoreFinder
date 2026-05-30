package com.lorefinder;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LowMapIDFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSources = settings.createGroup("Sources");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> mapIdMax = sgGeneral.add(new IntSetting.Builder()
        .name("map-id-max")
        .description("Highlight maps with an ID strictly below this value.")
        .defaultValue(50000)
        .min(2)
        .sliderRange(100, 200000)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to rescan, in ticks.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> itemFrames = sgSources.add(new BoolSetting.Builder()
        .name("item-frames")
        .description("Scan item frames and glow item frames.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> groundItems = sgSources.add(new BoolSetting.Builder()
        .name("ground-items")
        .description("Scan dropped filled map items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showMapId = sgGeneral.add(new BoolSetting.Builder()
        .name("show-map-id")
        .description("Show map ID in the module info string when one match is selected.")
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
        .defaultValue(40)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color.")
        .defaultValue(new SettingColor(210, 175, 120, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color.")
        .defaultValue(new SettingColor(235, 200, 140, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to low map ID targets.")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> matchIds = new HashSet<>();
    private Integer nearestMapId;
    private int tickTimer;

    public LowMapIDFinder() {
        super(LoreFinderAddon.CATEGORY, "low-map-id-finder", "ESP for filled maps with a low map ID in render distance.");
    }

    @Override
    public void onActivate() {
        matchIds.clear();
        nearestMapId = null;
        tickTimer = scanInterval.get();
        scan();
    }

    @Override
    public void onDeactivate() {
        matchIds.clear();
        nearestMapId = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) return;

        if (++tickTimer < scanInterval.get()) return;
        tickTimer = 0;
        scan();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!Utils.canUpdate() || matchIds.isEmpty()) return;

        SettingColor side = new SettingColor(sideColor.get());
        side.a = fillOpacity.get();
        SettingColor line = lineColor.get();

        Map<Integer, Integer> frameIndex = itemFrames.get()
            ? MapIdHelper.buildFrameEntityToMapId(mc.world)
            : Collections.emptyMap();

        for (Entity entity : mc.world.getEntities()) {
            if (!matchIds.contains(entity.getId())) continue;
            if (!stillMatches(entity, frameIndex)) continue;

            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            if (tracers.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    x, y + entity.getHeight() / 2.0, z,
                    line
                );
            }

            var box = entity.getBoundingBox();
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                side, line, shapeMode.get(), 0
            );
        }
    }

    @Override
    public String getInfoString() {
        if (showMapId.get() && nearestMapId != null && matchIds.size() == 1) {
            return nearestMapId.toString();
        }
        return Integer.toString(matchIds.size());
    }

    private void scan() {
        if (!Utils.canUpdate()) return;

        matchIds.clear();
        nearestMapId = null;

        double maxDistSq = Math.pow(mc.options.getClampedViewDistance() * 16.0, 2);
        double nearestDist = Double.MAX_VALUE;
        int threshold = mapIdMax.get();

        Map<Integer, Integer> frameIndex = itemFrames.get()
            ? MapIdHelper.buildFrameEntityToMapId(mc.world)
            : Collections.emptyMap();

        for (Entity entity : mc.world.getEntities()) {
            if (!entity.isAlive()) continue;
            if (!isSourceEnabled(entity)) continue;

            ItemFrameEntity frame = entity instanceof ItemFrameEntity f ? f : null;
            ItemStack stack = getMapStack(entity, frame);
            if (stack == null) continue;

            Integer mapId = MapIdHelper.resolveMapId(stack, frame, mc.world, frameIndex);
            if (mapId == null || !MapIdHelper.isOldMap(mapId, 1, threshold)) continue;

            if (PlayerUtils.squaredDistanceTo(entity) > maxDistSq) continue;

            matchIds.add(entity.getId());

            double dist = PlayerUtils.squaredDistanceTo(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestMapId = mapId;
            }
        }
    }

    private boolean stillMatches(Entity entity, Map<Integer, Integer> frameIndex) {
        if (!isSourceEnabled(entity)) return false;

        ItemFrameEntity frame = entity instanceof ItemFrameEntity f ? f : null;
        ItemStack stack = getMapStack(entity, frame);
        if (stack == null) return false;

        Integer mapId = MapIdHelper.resolveMapId(stack, frame, mc.world, frameIndex);
        return mapId != null && MapIdHelper.isOldMap(mapId, 1, mapIdMax.get());
    }

    private boolean isSourceEnabled(Entity entity) {
        if (entity instanceof ItemFrameEntity) return itemFrames.get();
        if (entity instanceof ItemEntity) return groundItems.get();
        return false;
    }

    private ItemStack getMapStack(Entity entity, ItemFrameEntity frame) {
        if (frame != null) {
            ItemStack stack = frame.getHeldItemStack();
            if (!stack.isEmpty() && stack.isOf(Items.FILLED_MAP)) return stack;
            return null;
        }

        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getStack();
            if (!stack.isEmpty() && stack.isOf(Items.FILLED_MAP)) return stack;
        }

        return null;
    }
}
