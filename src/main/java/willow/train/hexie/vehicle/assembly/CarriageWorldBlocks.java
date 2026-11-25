package willow.train.hexie.vehicle.assembly;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import willow.train.hexie.bridge.world.WorldBlockBridge;

// split from willow/train/hexie/vehicle/assembly/CarriageSnapshotStore.java
final class CarriageWorldBlocks {
    private final CarriageSnapshotStore store;

    CarriageWorldBlocks(CarriageSnapshotStore store) {
        this.store = store;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MOVE_FLAGS = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;

    static void checkTarget(ServerLevel level, PlacedBlock block) {
        requireLoaded(level, block.position());
        BlockState current = level.getBlockState(block.position());
        if ((!current.isAir() && !current.equals(vacantState(block.block().state())))
                || level.getBlockEntity(block.position()) != null)
            throw new IllegalStateException("Disassembly target is occupied: " + block.position());
    }

    List<PlacedBlock> blocks(List<CarriageSnapshot> values) {
        var result = new ArrayList<PlacedBlock>();
        var positions = new HashSet<BlockPos>();
        for (CarriageSnapshot snapshot : values) {
            if (snapshot == null || !store.dimension.equals(snapshot.dimension()))
                throw new IllegalArgumentException("Assembly snapshot dimension or reference is invalid");
            for (var block : snapshot.blocks()) {
                if (block.state().getBlock() instanceof WorldBlockBridge.StandardTrackBlockView || block.state().getBlock() instanceof BaseRailBlock)
                    throw new IllegalArgumentException("Tracks cannot be part of an assembled carriage");
                BlockPos position = new BlockPos(Math.addExact(snapshot.origin().getX(), block.position().getX()),
                        Math.addExact(snapshot.origin().getY(), block.position().getY()),
                        Math.addExact(snapshot.origin().getZ(), block.position().getZ()));
                if (!positions.add(position)) throw new IllegalArgumentException("Overlapping assembly snapshots");
                result.add(new PlacedBlock(position, block));
            }
        }
        return result;
    }

    static BlockState vacantState(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
                ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    static void replace(ServerLevel level, BlockPos position, BlockState state) {
        // Removing the entity first prevents container onRemove hooks from dropping copied inventory.
        level.removeBlockEntity(position);
        level.setBlock(position, state, MOVE_FLAGS);
        if (!level.getBlockState(position).equals(state))
            throw new IllegalStateException("Block refused assembly state change: " + position);
    }

    static void restore(ServerLevel level, List<PlacedBlock> blocks) {
        for (PlacedBlock block : blocks) replace(level, block.position(), block.block().state());
        restoreEntities(level, blocks);
    }

    static void restoreEntities(ServerLevel level, List<PlacedBlock> blocks) {
        for (PlacedBlock block : blocks) {
            var data = block.block().entityData();
            if (data == null) continue;
            var entity = level.getBlockEntity(block.position());
            // A removal callback can fail after dropping the entity but before changing its state.
            // Re-setting that same state need not create an entity, so restore it explicitly here.
            if (entity == null) {
                entity = BlockEntity.loadStatic(block.position(), block.block().state(), data, level.registryAccess());
                if (entity == null) throw new IllegalStateException("Restored block entity is missing: " + block.position());
                level.setBlockEntity(entity);
            } else entity.loadWithComponents(data, level.registryAccess());
            entity.setChanged();
            level.sendBlockUpdated(block.position(), block.block().state(), block.block().state(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The delayed neighbour/shape phase used after moving a complete structure. setBlock already
     * sent clients and updated POIs; repeating markAndNotifyBlock would process POI removal twice.
     * These are its remaining neighbour, comparator and shape updates, after the batch commits.
     * External neighbour callbacks can create drops or scheduled ticks, so their exceptions cannot
     * undo the committed batch. Log them and continue notifying its other positions instead.
     */
    static void notifyNeighbours(ServerLevel level, List<PlacedBlock> blocks,
                                         Function<PlacedBlock, BlockState> previous) {
        int shapeFlags = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS;
        for (PlacedBlock block : blocks) {
            try {
                BlockState oldState = previous.apply(block);
                BlockState current = level.getBlockState(block.position());
                level.blockUpdated(block.position(), oldState.getBlock());
                if (current.hasAnalogOutputSignal())
                    level.updateNeighbourForOutputSignal(block.position(), current.getBlock());
                oldState.updateIndirectNeighbourShapes(level, block.position(), shapeFlags);
                current.updateNeighbourShapes(level, block.position(), shapeFlags);
                current.updateIndirectNeighbourShapes(level, block.position(), shapeFlags);
            } catch (RuntimeException failure) {
                LOGGER.error("Neighbour notification failed after committed carriage block change at {}", block.position(), failure);
            }
        }
    }

    static void requireLoaded(ServerLevel level, BlockPos position) {
        if (level.isOutsideBuildHeight(position) || !level.isLoaded(position))
            throw new IllegalStateException("Assembly target is outside the loaded world: " + position);
    }

    record PlacedBlock(BlockPos position, CarriageSnapshot.Block block) { }
}
