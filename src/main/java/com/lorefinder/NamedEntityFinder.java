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

import java.util.HashSet;
import java.util.Set;

public class NamedEntityFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to rescan entities, in ticks.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
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
        .defaultValue(new SettingColor(255, 200, 80, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color.")
        .defaultValue(new SettingColor(255, 200, 80, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to named entities.")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> matchIds = new HashSet<>();
    private int tickTimer;

    public NamedEntityFinder() {
        super(LoreFinderAddon.CATEGORY, "named-entity-finder", "ESP for entities with a custom name in render distance.");
    }

    @Override
    public void onActivate() {
        matchIds.clear();
        tickTimer = scanInterval.get();
        scan();
    }

    @Override
    public void onDeactivate() {
        matchIds.clear();
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

        for (Entity entity : mc.world.getEntities()) {
            if (!matchIds.contains(entity.getId())) continue;
            if (!entity.isAlive() || !entity.hasCustomName()) continue;

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
        return Integer.toString(matchIds.size());
    }

    private void scan() {
        if (!Utils.canUpdate()) return;

        matchIds.clear();
        double maxDistSq = Math.pow(mc.options.getClampedViewDistance() * 16.0, 2);

        for (Entity entity : mc.world.getEntities()) {
            if (!entity.isAlive() || !entity.hasCustomName()) continue;
            if (PlayerUtils.squaredDistanceTo(entity) > maxDistSq) continue;

            matchIds.add(entity.getId());
        }
    }
}
