package com.lorefinder;

import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.Predicate;

/**
 * Heuristics for block states / placements that cannot survive in vanilla.
 */
public final class IllegalBlockChecks {
    private IllegalBlockChecks() {}

    public static boolean isIllegal(
        WorldView world,
        BlockPos pos,
        BlockState state,
        boolean invalidPlacement,
        boolean floatingLava,
        boolean floatingWater,
        boolean orphanBeds,
        boolean orphanDoubleBlocks,
        boolean floatingAttached
    ) {
        if (state.isAir()) return false;
        if (!isContextLoaded(world, pos)) return false;

        if (invalidPlacement && isInvalidPlacement(world, pos, state)) return true;
        if (floatingLava && isFloatingFluid(world, pos, state, true)) return true;
        if (floatingWater && isFloatingFluid(world, pos, state, false)) return true;
        if (orphanBeds && isOrphanBed(world, pos, state)) return true;
        if (orphanDoubleBlocks && isOrphanDoubleBlock(world, pos, state)) return true;
        if (floatingAttached && isFloatingAttachedBlock(world, pos, state)) return true;

        return false;
    }

    /** Fast section filter — skips empty-looking sections before per-block work. */
    public static boolean sectionMightContainIllegal(
        ChunkSection section,
        boolean invalidPlacement,
        boolean floatingLava,
        boolean floatingWater,
        boolean orphanBeds,
        boolean orphanDoubleBlocks,
        boolean floatingAttached
    ) {
        if (section == null || section.isEmpty()) return false;

        Predicate<BlockState> predicate = state -> {
            if (state.isAir()) return false;
            Block block = state.getBlock();

            if (floatingLava && (state.isOf(Blocks.LAVA) || state.isOf(Blocks.LAVA_CAULDRON))) return true;
            if (floatingWater && (state.isOf(Blocks.WATER) || state.isOf(Blocks.WATER_CAULDRON))) return true;
            if (orphanBeds && block instanceof BedBlock) return true;
            if (orphanDoubleBlocks && state.contains(Properties.DOUBLE_BLOCK_HALF)) return true;

            if (floatingAttached && isAttachedBlockType(block)) return true;

            if (invalidPlacement && mightBeInvalidPlacement(block, state)) return true;

            return false;
        };

        return section.hasAny(predicate);
    }

    private static boolean mightBeInvalidPlacement(Block block, BlockState state) {
        if (block instanceof BedBlock || block instanceof FluidBlock || block instanceof FallingBlock) return false;
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) return false;
        if (block instanceof TorchBlock || block instanceof WallMountedBlock || block instanceof AttachedStemBlock) {
            return false;
        }
        if (block instanceof PlantBlock && !(block instanceof CropBlock) && !(block instanceof SaplingBlock)) {
            return false;
        }

        return block instanceof SaplingBlock
            || block instanceof CropBlock
            || block instanceof FlowerBlock
            || block instanceof MushroomPlantBlock
            || block instanceof SugarCaneBlock
            || block instanceof CactusBlock
            || block instanceof BambooBlock
            || block instanceof NetherWartBlock
            || block instanceof SeaPickleBlock
            || block instanceof CoralBlock;
    }

    private static boolean isAttachedBlockType(Block block) {
        return block instanceof TorchBlock
            || block instanceof WallMountedBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof LadderBlock
            || block instanceof TripwireHookBlock
            || block instanceof BellBlock
            || block instanceof LanternBlock
            || block instanceof ChainBlock;
    }

    public static boolean isContextLoaded(WorldView world, BlockPos pos) {
        if (!(world instanceof World w)) return false;
        if (!w.isChunkLoaded(pos)) return false;

        for (Direction direction : Direction.values()) {
            if (!w.isChunkLoaded(pos.offset(direction))) return false;
        }

        return true;
    }

    /**
     * Neighbor chunks must be loaded and at {@link ChunkStatus#FULL} so placement checks
     * against blocks in adjacent chunks are not evaluated against stale or empty data.
     */
    public static boolean isChunkBorderContextLoaded(World world, ChunkPos chunkPos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(chunkPos.x + dx, chunkPos.z + dz)) return false;

                WorldChunk chunk = world.getChunk(chunkPos.x + dx, chunkPos.z + dz);
                if (chunk == null || chunk.isEmpty()) return false;
                if (!chunk.getStatus().isAtLeast(ChunkStatus.FULL)) return false;
            }
        }

        return true;
    }

    public static boolean isInvalidPlacement(WorldView world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (block instanceof BedBlock || block instanceof FluidBlock) return false;
        if (block instanceof FallingBlock) return false;
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) return false;

        if (block instanceof TorchBlock || block instanceof WallMountedBlock || block instanceof AttachedStemBlock) {
            return false;
        }

        if (block instanceof PlantBlock && !(block instanceof CropBlock) && !(block instanceof SaplingBlock)) {
            return false;
        }

        return !state.canPlaceAt(world, pos);
    }

    public static boolean isFloatingFluid(WorldView world, BlockPos pos, BlockState state, boolean lava) {
        if (lava) {
            if (!state.isOf(Blocks.LAVA) && !state.isOf(Blocks.LAVA_CAULDRON)) return false;
        } else {
            if (!state.isOf(Blocks.WATER) && !state.isOf(Blocks.WATER_CAULDRON)) return false;
        }

        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        if (below.isOf(Blocks.LAVA) || below.isOf(Blocks.WATER)) return false;

        return !below.isSolidBlock(world, belowPos);
    }

    public static boolean isOrphanBed(WorldView world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) return false;

        BedPart part = state.get(BedBlock.PART);
        Direction facing = state.get(BedBlock.FACING);
        Direction partnerDir = part == BedPart.FOOT ? facing : facing.getOpposite();
        BlockPos partnerPos = pos.offset(partnerDir);

        if (!(world instanceof World w) || !w.isChunkLoaded(partnerPos)) return false;

        BlockState partner = world.getBlockState(partnerPos);

        if (!(partner.getBlock() instanceof BedBlock)) return true;

        BedPart partnerPart = partner.get(BedBlock.PART);
        return partnerPart == part;
    }

    public static boolean isOrphanDoubleBlock(WorldView world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) return false;

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        BlockPos otherPos = half == DoubleBlockHalf.UPPER ? pos.down() : pos.up();

        if (!(world instanceof World w) || !w.isChunkLoaded(otherPos)) return false;

        BlockState other = world.getBlockState(otherPos);

        if (!other.contains(Properties.DOUBLE_BLOCK_HALF)) return true;

        DoubleBlockHalf otherHalf = other.get(Properties.DOUBLE_BLOCK_HALF);
        return otherHalf == half;
    }

    public static boolean isFloatingAttachedBlock(WorldView world, BlockPos pos, BlockState state) {
        if (!isAttachedBlockType(state.getBlock())) return false;
        return !state.canPlaceAt(world, pos);
    }
}
