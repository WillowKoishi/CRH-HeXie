package willow.train.hexie.sync.cache;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.Property;
import willow.train.hexie.bridge.sync.VehicleSyncData.Cell;
import willow.train.hexie.bridge.sync.VehicleSyncData.BlockData;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Local block and neighbour lookups for a single immutable body revision. */
final class VehicleBlockView implements BlockGetter {
    final Level level;
    private final Map<Cell, BlockData> cells;
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, Optional<BlockEntity>> entities = new HashMap<>();
    VehicleBlockView(Level level, Map<Cell, BlockData> cells) {
        this.level = level;
        this.cells = cells;
    }

    static BlockPos pos(Cell c) { return new BlockPos(c.x(), c.y(), c.z()); }

    static BlockState state(BlockData data) {
        var id = data.blockId();
        var state = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()))
                .defaultBlockState();
        for (var entry : data.properties().entrySet()) {
            var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property != null) state = withValue(state, property, entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var data = data(pos);
        return data == null ? Blocks.AIR.defaultBlockState()
                : states.computeIfAbsent(pos.immutable(), key -> state(data));
    }

    private BlockData data(BlockPos p) { return cells.get(new Cell(p.getX(), p.getY(), p.getZ())); }

    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        var data = data(pos);
        if (data == null || data.blockEntityData().isEmpty() || !getBlockState(pos).hasBlockEntity()) return null;
        return entities.computeIfAbsent(pos.immutable(), key -> {
            try (var in = new DataInputStream(new ByteArrayInputStream(
                    Base64.getDecoder().decode(data.blockEntityData().get())))) {
                var tag = NbtIo.read(in, NbtAccounter.unlimitedHeap());
                return Optional.ofNullable(BlockEntity.loadStatic(key, getBlockState(key), tag, level.registryAccess()));
            } catch (IOException | RuntimeException e) { return Optional.empty(); }
        }).orElse(null);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
}
