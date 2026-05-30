package com.lorefinder;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapFrameMarker;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves map IDs from client map states (authoritative for framed maps) and verified item stacks.
 */
public final class MapIdHelper {
    private static Method worldGetMapStates;
    private static Field mapStateFrames;

    private MapIdHelper() {}

    /**
     * Item frame entity ID → map ID from the client's loaded {@link MapState} frame markers.
     */
    public static Map<Integer, Integer> buildFrameEntityToMapId(World world) {
        Map<MapIdComponent, MapState> states = getClientMapStates(world);
        if (states == null || states.isEmpty()) return Collections.emptyMap();

        Map<Integer, Integer> entityToMapId = new HashMap<>();

        for (Map.Entry<MapIdComponent, MapState> entry : states.entrySet()) {
            int mapId = entry.getKey().id();
            Map<String, MapFrameMarker> frames = getMapStateFrames(entry.getValue());
            if (frames == null) continue;

            for (MapFrameMarker marker : frames.values()) {
                entityToMapId.put(marker.entityId(), mapId);
            }
        }

        return entityToMapId;
    }

    public static Integer resolveMapId(ItemStack stack, ItemFrameEntity frame, World world, Map<Integer, Integer> frameIndex) {
        if (world == null) return null;

        if (frame != null && frameIndex != null) {
            Integer fromState = frameIndex.get(frame.getId());
            if (fromState != null) return fromState;
        }

        return getVerifiedStackMapId(stack, world);
    }

    public static Integer getVerifiedStackMapId(ItemStack stack, World world) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) return null;
        if (!stack.getComponents().contains(DataComponentTypes.MAP_ID)) return null;

        MapIdComponent component = stack.get(DataComponentTypes.MAP_ID);
        if (component == null) return null;

        int id = component.id();
        if (id <= 0) return null;
        if (FilledMapItem.getMapState(component, world) == null) return null;

        return id;
    }

    public static boolean isOldMap(int mapId, int minIdInclusive, int maxIdExclusive) {
        return mapId >= minIdInclusive && mapId < maxIdExclusive;
    }

    @SuppressWarnings("unchecked")
    private static Map<MapIdComponent, MapState> getClientMapStates(World world) {
        if (!(world instanceof ClientWorld)) return null;

        try {
            if (worldGetMapStates == null) {
                worldGetMapStates = World.class.getDeclaredMethod("getMapStates");
                worldGetMapStates.setAccessible(true);
            }

            Object result = worldGetMapStates.invoke(world);
            if (result instanceof Map<?, ?> map) {
                return (Map<MapIdComponent, MapState>) map;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, MapFrameMarker> getMapStateFrames(MapState state) {
        if (state == null) return null;

        try {
            if (mapStateFrames == null) {
                mapStateFrames = MapState.class.getDeclaredField("frames");
                mapStateFrames.setAccessible(true);
            }

            Object result = mapStateFrames.get(state);
            if (result instanceof Map<?, ?> map) {
                return (Map<String, MapFrameMarker>) map;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }
}
