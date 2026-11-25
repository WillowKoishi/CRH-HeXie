package willow.train.hexie.vehicle.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.ActiveAssembly;
import willow.train.hexie.vehicle.assembly.CarriageWorldBlocks.PlacedBlock;

// split from willow/train/hexie/vehicle/assembly/CarriageSnapshotStore.java
final class CarriageAssemblyTransactions {
    private final CarriageSnapshotStore store;

    CarriageAssemblyTransactions(CarriageSnapshotStore store) {
        this.store = store;
    }

    ActiveAssembly assemble(ServerLevel level, UUID owner, BlockPos station,
                                   VehicleAssemblyPreparer.Prepared prepared) {
        return assemble(level, owner, station, prepared.forward(),
                prepared.carriages().stream().map(VehicleAssemblyPreparer.Carriage::structure).toList());
    }

    /** Preflight everything, retain snapshots, then remove the source blocks in this same tick. */
    ActiveAssembly assemble(ServerLevel level, UUID owner, BlockPos station, Direction forward,
                                   List<CarriageSnapshot> additions) {
        return assemble(level, UUID.randomUUID(), owner, station, forward, additions);
    }

    /** The caller may allocate ownership before mounting storage from still-present world entities. */
    ActiveAssembly assemble(ServerLevel level, UUID assemblyId, UUID owner, BlockPos station,
                                   Direction forward, List<CarriageSnapshot> additions) {
        requireLevel(level);
        if (store.active.containsKey(Objects.requireNonNull(assemblyId)))
            throw new IllegalArgumentException("Assembly identity already exists");
        additions = List.copyOf(additions);
        var entry = new ActiveAssembly(assemblyId, owner, station, forward,
                additions.stream().map(CarriageSnapshot::id).toList());
        List<PlacedBlock> blocks = store.blocks(additions);
        for (PlacedBlock block : blocks) {
            CarriageSnapshotStore.requireLoaded(level, block.position());
            if (!level.getBlockState(block.position()).equals(block.block().state()))
                throw new IllegalStateException("Assembly source changed: " + block.position());
            var entity = level.getBlockEntity(block.position());
            var expected = block.block().entityData();
            if (expected == null ? entity != null : entity == null
                    || !expected.equals(entity.saveWithFullMetadata(store.registries)))
                throw new IllegalStateException("Assembly block entity changed: " + block.position());
        }
        boolean wasDirty = store.isDirty();
        store.addAll(additions);
        store.active.put(entry.id(), entry);
        var changed = new ArrayList<PlacedBlock>();
        try {
            for (PlacedBlock block : blocks) {
                changed.add(block);
                CarriageSnapshotStore.replace(level, block.position(), CarriageSnapshotStore.vacantState(block.block().state()));
            }
        } catch (RuntimeException failure) {
            try {
                CarriageSnapshotStore.restore(level, changed);
                store.active.remove(entry.id());
                entry.carriages().forEach(store.snapshots::remove);
                store.setDirty(wasDirty);
                CarriageSnapshotStore.notifyNeighbours(level, changed, block -> CarriageSnapshotStore.vacantState(block.block().state()));
            } catch (RuntimeException rollbackFailure) {
                // Retain the only complete snapshots if a third-party block also rejects rollback.
                failure.addSuppressed(rollbackFailure);
                store.setDirty();
            }
            throw failure;
        }
        store.setDirty();
        CarriageSnapshotStore.notifyNeighbours(level, blocks, block -> block.block().state());
        return entry;
    }

    /** Restores at the captured positions only; a conflicting or unloaded target changes nothing. */
    void disassemble(ServerLevel level, UUID assemblyId) {
        disassemble(level, assemblyId, () -> { });
    }

    /** Action-time check before stopping behaviours; does not load chunks or mutate the world. */
    void checkDisassembly(ServerLevel level, UUID assemblyId) {
        requireLevel(level);
        var entry = store.active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        for (var block : store.blocks(entry.carriages().stream().map(store.snapshots::get).toList())) CarriageSnapshotStore.checkTarget(level, block);
    }

    /** Preflight a transformed subset without publishing its destination as the saved source. */
    void checkDisassembly(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targetSnapshots) {
        checkDisassembly(level, Map.of(assemblyId, targetSnapshots));
    }

    /** Check the complete consist, including overlaps between different source assemblies. */
    void checkDisassembly(ServerLevel level, Map<UUID, List<CarriageSnapshot>> targetSnapshots) {
        requireLevel(level);
        var targets = disassemblyTargets(targetSnapshots).values().stream().flatMap(List::stream).toList();
        for (var block : store.blocks(targets)) CarriageSnapshotStore.checkTarget(level, block);
    }

    /** The placement backend decides how occupied cells are replaced or dropped. */
    void checkDisassemblyUsing(ServerLevel level, Map<UUID, List<CarriageSnapshot>> targetSnapshots) {
        requireLevel(level);
        var targets = disassemblyTargets(targetSnapshots).values().stream().flatMap(List::stream).toList();
        for (var block : store.blocks(targets)) CarriageSnapshotStore.requireLoaded(level, block.position());
    }

    /** Restore mounted storage after all blocks/BE exist, while rollback still owns the snapshots. */
    void disassemble(ServerLevel level, UUID assemblyId, Runnable afterBlocksRestored) {
        requireLevel(level);
        ActiveAssembly entry = store.active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        disassemble(level, entry, entry.carriages().stream().map(store.snapshots::get).toList(), null, afterBlocksRestored);
    }

    /**
     * Restore only the supplied carriage identities, possibly at a new position/orientation.
     * Original snapshots and active membership remain authoritative until all blocks, entities
     * and the installation callback succeed. Successful targets release their saved snapshots.
     */
    void disassemble(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targetSnapshots,
                            Runnable afterInstall) {
        requireLevel(level);
        var entry = store.active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        disassemble(level, entry, disassemblyTargets(entry, targetSnapshots), null, afterInstall);
    }

    /**
     * The supplied backend performs the entire placement lifecycle using its live block data.
     * Targets describe occupied cells for preflight/rollback only; this store must not install
     * them first, reload their block entities or repeat the backend's neighbour notifications.
     * Existing world blocks are left to the backend's own replacement and drop rules.
     */
    void disassembleUsing(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targets,
                                 Runnable installer) {
        disassembleUsing(level, Map.of(assemblyId, targets), installer);
    }

    /** All source memberships commit together after one native installation/rollback boundary. */
    void disassembleUsing(ServerLevel level, Map<UUID, List<CarriageSnapshot>> targets,
                                 Runnable installer) {
        requireLevel(level);
        disassemble(level, disassemblyTargets(targets), Objects.requireNonNull(installer), () -> { });
    }

    private Map<ActiveAssembly, List<CarriageSnapshot>> disassemblyTargets(
            Map<UUID, List<CarriageSnapshot>> targetsByAssembly) {
        if (targetsByAssembly.isEmpty()) throw new IllegalArgumentException("Disassembly needs an assembly");
        var groups = new LinkedHashMap<ActiveAssembly, List<CarriageSnapshot>>();
        var identities = new HashSet<UUID>();
        for (var group : targetsByAssembly.entrySet()) {
            var entry = store.active(group.getKey()).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
            var targets = disassemblyTargets(entry, group.getValue());
            for (var target : targets)
                if (!identities.add(target.id())) throw new IllegalArgumentException("Duplicate disassembly carriage");
            groups.put(entry, targets);
        }
        return groups;
    }

    private List<CarriageSnapshot> disassemblyTargets(ActiveAssembly entry, List<CarriageSnapshot> targets) {
        targets = List.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("Disassembly needs a carriage");
        var identities = new HashSet<UUID>();
        for (var target : targets) {
            var original = store.snapshots.get(target.id());
            if (!entry.carriages().contains(target.id()) || original == null
                    || !identities.add(target.id()) || !store.dimension.equals(target.dimension()))
                throw new IllegalArgumentException("Disassembly target is not a unique member of this assembly");
            if (target.blocks().size() != original.blocks().size() || target.supports().size() != original.supports().size())
                throw new IllegalArgumentException("Disassembly transform cannot add or remove captured blocks/supports");
            var types = new java.util.HashMap<Block, Integer>();
            for (var block : original.blocks()) types.merge(block.state().getBlock(), 1, Integer::sum);
            for (var block : target.blocks()) {
                Integer count = types.get(block.state().getBlock());
                if (count == null) throw new IllegalArgumentException("Disassembly transform changed captured block types");
                if (count == 1) types.remove(block.state().getBlock());
                else types.put(block.state().getBlock(), count - 1);
            }
            target.validate(store.registries);
        }
        return targets;
    }

    private void disassemble(ServerLevel level, ActiveAssembly entry, List<CarriageSnapshot> targets,
                             Runnable installer, Runnable afterInstall) {
        disassemble(level, Map.of(entry, targets), installer, afterInstall);
    }

    private void disassemble(ServerLevel level, Map<ActiveAssembly, List<CarriageSnapshot>> groups,
                             Runnable installer, Runnable afterInstall) {
        Objects.requireNonNull(afterInstall);
        var targets = groups.values().stream().flatMap(List::stream).toList();
        List<PlacedBlock> blocks = store.blocks(targets);
        var previous = new LinkedHashMap<BlockPos, WorldBlock>();
        for (PlacedBlock block : blocks) {
            if (installer == null) CarriageSnapshotStore.checkTarget(level, block);
            else CarriageSnapshotStore.requireLoaded(level, block.position());
            BlockState current = level.getBlockState(block.position());
            BlockEntity entity = level.getBlockEntity(block.position());
            previous.put(block.position(), new WorldBlock(current,
                    entity == null ? null : entity.saveWithFullMetadata(store.registries)));
        }
        var changed = new ArrayList<PlacedBlock>();
        try {
            if (installer != null) {
                // The backend may throw after any cell. Back up its whole destination before entry.
                changed.addAll(blocks);
                installer.run();
            } else {
                // Install every state before loading block entities, so neighbouring parts already exist.
                for (PlacedBlock block : blocks) {
                    changed.add(block);
                    CarriageSnapshotStore.replace(level, block.position(), block.block().state());
                }
                CarriageSnapshotStore.restoreEntities(level, blocks);
            }
            afterInstall.run();
        } catch (RuntimeException failure) {
            // Attempt every changed cell even if a third-party callback rejects one rollback.
            // The authoritative snapshots/membership have not been touched at this point.
            for (PlacedBlock block : changed.reversed()) {
                try { CarriageSnapshotStore.replace(level, block.position(), previous.get(block.position()).state()); }
                catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            for (PlacedBlock block : changed) {
                var prior = previous.get(block.position());
                if (prior.entityData() == null) continue;
                try {
                    var entity = BlockEntity.loadStatic(block.position(), prior.state(), prior.entityData(), store.registries);
                    if (entity == null) throw new IllegalStateException("Rollback block entity is missing: " + block.position());
                    level.setBlockEntity(entity);
                    entity.setChanged();
                } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            CarriageSnapshotStore.notifyNeighbours(level, changed, block -> block.block().state());
            throw failure;
        }
        var removed = new HashSet<UUID>();
        for (var target : targets) removed.add(target.id());
        for (var entry : groups.keySet()) {
            var remaining = entry.carriages().stream().filter(id -> !removed.contains(id)).toList();
            if (remaining.isEmpty()) store.active.remove(entry.id());
            else store.active.put(entry.id(), new ActiveAssembly(entry.id(), entry.owner(), entry.station(), entry.forward(), remaining));
        }
        for (var target : targets) store.snapshots.remove(target.id());
        store.setDirty();
        if (installer == null) CarriageSnapshotStore.notifyNeighbours(level, blocks, block -> previous.get(block.position()).state());
    }

    record WorldBlock(BlockState state, CompoundTag entityData) { }

    private void requireLevel(ServerLevel level) {
        store.requireServerThread();
        CarriageSnapshotStore.requireServerThread(level);
        if (!store.dimension.equals(level.dimension()) || store.registries != level.registryAccess())
            throw new IllegalArgumentException("Assembly store belongs to another level");
    }
}
