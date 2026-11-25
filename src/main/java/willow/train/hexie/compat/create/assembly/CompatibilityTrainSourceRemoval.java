package willow.train.hexie.compat.create.assembly;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.simibubi.create.Create;
import net.minecraft.server.MinecraftServer;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CompatibilityTrainPersistence;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from CompatibilityTrain.java
public final class CompatibilityTrainSourceRemoval {
    private final CompatibilityTrain compatibilityTrain;

    public CompatibilityTrainSourceRemoval(CompatibilityTrain compatibilityTrain) { this.compatibilityTrain = compatibilityTrain; }

    private static final java.util.Map<MinecraftServer, java.util.LinkedHashSet<CompatibilityTrain>> PENDING_REMOVALS
            = new java.util.WeakHashMap<>();

    /** Defer ownership changes until Create has finished iterating its train lists. */
    public void queueSourceRemoval() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && !compatibilityTrain.retiringProxy && compatibilityTrain.hasLifecycleData())
            PENDING_REMOVALS.computeIfAbsent(server, ignored -> new java.util.LinkedHashSet<>()).add(compatibilityTrain);
    }

    public static void finishSourceRemovals(MinecraftServer server) {
        var removed = PENDING_REMOVALS.remove(server);
        if (removed == null) return;
        var retained = new HashSet<UUID>();
        for (var value : Create.RAILWAYS.trains.values())
            if (value instanceof CompatibilityTrain train && !train.invalid) retained.addAll(train.carriageIds);
        var candidates = removed.stream().filter(train -> !train.retiringProxy && train.hasLifecycleData()
                && train.carriageIds.stream().noneMatch(retained::contains)).toList();
        var processed = new HashSet<CompatibilityTrain>();
        for (var train : candidates) {
            // Rebuilding proxies transfers the same carriage identities to new records.
            if (train.carriageIds.stream().allMatch(retained::contains)) continue;
            if (!train.hasLifecycleData()) continue;
            if (!processed.add(train)) continue;
            var group = candidates.stream().filter(other -> other == train
                    || train.bridge != null && other.bridge == train.bridge
                    || train.persistence.savedMotionOwner != null && train.bridge == null && other.bridge == null
                            && java.util.Objects.equals(train.persistence.savedMotionOwner, other.persistence.savedMotionOwner)).toList();
            processed.addAll(group);
            train.discardSourceCarriages(server, group);
        }
    }

    public void discardSourceCarriages(MinecraftServer server, List<CompatibilityTrain> removed) {
        ServerLevel level = compatibilityTrain.bridge != null ? compatibilityTrain.bridge.motion().level()
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(compatibilityTrain.persistence.motionDimension)));
        if (level == null) throw new IllegalStateException("Missing removed train dimension " + compatibilityTrain.persistence.motionDimension);
        Set<UUID> selected = removed.stream().flatMap(train -> train.carriageIds.stream())
                .collect(java.util.stream.Collectors.toSet());
        Runnable retain;
        if (compatibilityTrain.bridge != null) retain = compatibilityTrain.bridge.motion().prepareDisassembly(selected);
        else if (compatibilityTrain.importedWithoutRoute) retain = () -> {};
        else {
            var source = compatibilityTrain.persistence.savedMotion != null ? compatibilityTrain
                    : Create.RAILWAYS.trains.get(compatibilityTrain.persistence.savedMotionOwner) instanceof CompatibilityTrain owner ? owner : null;
            if (source == null) source = removed.stream().filter(train -> train.persistence.savedMotion != null).findFirst().orElse(null);
            if (source == null || source.persistence.savedMotion == null) {
                // Native remove also applies to orphan records from an interrupted save.
                // There is no motion domain to hand off, but their selected snapshots and
                // bodies still have to follow the removal instead of aborting the server tick.
                Create.LOGGER.warn("Removing orphan HeXie train {} without a saved motion owner", compatibilityTrain.id);
                source = null;
            }
            if (source == null) retain = () -> {};
            else {
                var pending = source.persistence.savedMotion.copy();
                var checkpoint = HexieBridges.vehicle().vehicleRuntimeNbt().readCheckpoint(pending.getCompound("Runtime"));
                var remaining = new HashSet<UUID>();
                var savedIds = checkpoint.carriages().stream().map(car -> car.definition().id())
                        .collect(java.util.stream.Collectors.toSet());
                // The saved domain can predate another native removal. Only currently registered
                // peer records can survive this one; do not recreate obsolete checkpoint members.
                UUID domainOwner = source.persistence.savedMotionOwner;
                for (var value : Create.RAILWAYS.trains.values())
                    if (value instanceof CompatibilityTrain peer && !peer.invalid && !peer.retiringProxy
                            && peer.bridge == null && (peer == source
                                    || domainOwner != null && domainOwner.equals(peer.persistence.savedMotionOwner)))
                        peer.carriageIds.stream().filter(savedIds::contains).forEach(remaining::add);
                remaining.removeAll(selected);
                if (remaining.isEmpty()) retain = () -> {};
                else {
                    pending.put("Runtime", HexieBridges.vehicle().vehicleRuntimeNbt().saveCheckpoint(
                            HexieBridges.vehicle().vehicleRuntime().subsetCheckpoint(checkpoint, remaining)));
                    for (String key : List.of("CarriageSources", "DerailedCarriages", "StalledCarriages", "CabModules", "AutomaticCabs")) {
                        if (!pending.contains(key)) continue;
                        var entries = new ListTag();
                        for (var value : pending.getList(key, Tag.TAG_COMPOUND))
                            if (remaining.contains(((CompoundTag) value).getUUID("Carriage"))) entries.add(value.copy());
                        pending.put(key, entries);
                    }
                    retain = CompatibilityTrainPersistence.preparePendingMotion(level, remaining, pending).commit();
                }
            }
        }
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        var oldAssemblies = store.active();
        store.discardCarriages(selected);
        retain.run();
        for (UUID id : selected) if (level.getEntity(id) instanceof SnapshotCarriageEntity entity) {
            entity.installMotion(null);
            entity.discard();
        }
        for (var train : removed) {
            var station = train.getCurrentStation();
            if (station != null) station.cancelReservation(train);
            if (train.navigation.destination != null) train.navigation.destination.cancelReservation(train);
            train.bridge = null;
            train.persistence.savedMotion = null;
            train.persistence.savedMotionOwner = null;
            train.persistence.pendingRelocation = null;
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToAllClients(
                    new com.simibubi.create.content.trains.entity.RemoveTrainPacket(train));
        }
        for (var entry : oldAssemblies)
            if (entry.carriages().stream().anyMatch(selected::contains) && store.active(entry.id()).isEmpty()
                    && level.isLoaded(entry.station()) && level.getBlockEntity(entry.station()) instanceof CreateAssemblyStationAccess access
                    && entry.id().equals(access.hexie$getAssemblyId())) access.hexie$setAssemblyId(null);
        Create.RAILWAYS.markTracksDirty();
    }
}
