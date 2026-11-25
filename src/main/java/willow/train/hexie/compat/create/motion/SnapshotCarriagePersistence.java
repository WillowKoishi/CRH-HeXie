package willow.train.hexie.compat.create.motion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.tuple.MutablePair;
import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageCargo;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageStructure;
import willow.train.hexie.compat.create.interaction.ContraptionInteractionTargets;
import willow.train.hexie.compat.create.interaction.CreatePassengerDisplayMovement;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetableStorage;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriagePersistence {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriagePersistence(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public boolean shouldBeSaved() {
        if (carriageEntity.nativeManaged) return false;
        // Legacy/unbound entities retain their existing chunk save until attached to a logical car.
        return (carriageEntity.getRemovalReason() == null || carriageEntity.getRemovalReason().shouldSave()) && !carriageEntity.isPassenger()
                && (!carriageEntity.isVehicle() || !carriageEntity.hasExactlyOnePlayerPassenger());
    }

    /** External entity copies must carry the structure, not only its world-local store key.
     * Create's normal carriage persistence calls saveAsPassenger instead. */
    public boolean save(CompoundTag tag) {
        if (!carriageEntity.baseSave(tag)) return false;
        if (carriageEntity.snapshot != null && carriageEntity.getContraption() != null) {
            tag.put("HexieStructure", carriageEntity.snapshot.save(carriageEntity.registryAccess()));
            tag.put("WorkingState", writeWorkingState(true));
            UUID owner = carriageEntity.sync.compatibilityCarriage == null ? carriageEntity.structureImport.importedOwner : carriageEntity.sync.compatibilityCarriage.train.owner;
            if (owner != null) tag.putUUID("StructureOwner", owner);
        }
        return true;
    }

    /** Original-world metadata for the station restore transaction; never modifies the immutable snapshot. */
    public Map<BlockPos, StructureBlockInfo> currentBlockData() {
        var result = new HashMap<BlockPos, StructureBlockInfo>();
        var actorData = actorBlockData();
        for (var info : carriageEntity.getContraption().getBlocks().values()) {
            CompoundTag data = actorData.getOrDefault(info.pos(), info.nbt());
            data = withCabControls(new StructureBlockInfo(info.pos(), info.state(), data)).nbt();
            data = data == null ? null : data.copy();
            if (data != null && carriageEntity.snapshot != null) {
                BlockPos world = carriageEntity.snapshot.origin().offset(info.pos());
                data.putInt("x", world.getX()); data.putInt("y", world.getY()); data.putInt("z", world.getZ());
                if (data.contains("Controller"))
                    data.put("Controller", NbtUtils.writeBlockPos(carriageEntity.snapshot.origin().offset(NBTHelper.readBlockPos(data, "Controller"))));
                if (data.contains("LastKnownPos")) data.put("LastKnownPos", NbtUtils.writeBlockPos(world));
            }
            result.put(info.pos(), new StructureBlockInfo(info.pos(), SnapshotCarriageStructure.SnapshotView.placedState(info.state()), data));
        }
        return result;
    }

    public StructureBlockInfo withCabControls(StructureBlockInfo info) {
        var owner = carriageEntity.motion();
        if (owner == null) return carriageEntity.sync.compatibilityCarriage == null ? info
                : CreateConsoleTimetableStorage.withProgress(info, carriageEntity.sync.compatibilityCarriage.train, carriageEntity.getUUID());
        var execution = owner.cabTimetable(new AssembledVehicleCabControl.ControlKey(carriageEntity.getUUID(), info.pos()));
        if (execution != null) info = execution.withBlockData(info, carriageEntity.registryAccess());
        var controls = owner.cabControlsForSave(carriageEntity.getUUID(), info.pos());
        if (controls == null) return info;
        var data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        data.put("CabControls", controls);
        return new StructureBlockInfo(info.pos(), info.state(), data);
    }

    public Map<BlockPos, CompoundTag> actorBlockData() {
        var result = new HashMap<BlockPos, CompoundTag>();
        for (var actor : carriageEntity.getContraption().getActors()) {
            var context = actor.right;
            if (carriageEntity.canRunBehaviours()) MovementBehaviour.REGISTRY.get(actor.left.state()).writeExtraData(context);
            CompoundTag data = context.blockEntityData == null ? null : context.blockEntityData.copy();
            // Create's actor save only writes the held item. Preserve the full live fake-player
            // inventory without stopping it or copying that private inventory into the spawn tag.
            if (data != null && context.temporaryData instanceof DeployerFakePlayer player)
                data.put("Inventory", player.getInventory().save(new ListTag()));
            result.put(actor.left.pos(), withCabControls(new StructureBlockInfo(actor.left.pos(), actor.left.state(), data)).nbt());
        }
        return result;
    }

    public CompoundTag writeWorkingState() {
        return writeWorkingState(true);
    }

    public CompoundTag writeWorkingState(boolean includeCargo) {
        var tag = new CompoundTag();
        tag.putBoolean("Active", carriageEntity.behavioursActive);
        tag.putBoolean("Stalled", carriageEntity.getContraption().stalled);
        if (includeCargo) carriageEntity.getContraption().writeStorage(tag, carriageEntity.registryAccess(), false);
        var actorData = actorBlockData();
        var actors = new ListTag();
        for (var actor : carriageEntity.getContraption().getActors()) {
            var data = actor.right.writeToNBT(new CompoundTag());
            data.put("Pos", NbtUtils.writeBlockPos(actor.left.pos()));
            data.putBoolean("Disabled", actor.right.disabled);
            if (actorData.get(actor.left.pos()) != null) data.put("BlockEntity", actorData.get(actor.left.pos()));
            actors.add(data);
        }
        tag.put("Actors", actors);
        tag.put("DisabledActors", NBTHelper.writeItemList(carriageEntity.getContraption().getDisabledActors(), carriageEntity.registryAccess()));
        var changed = new ListTag();
        if (carriageEntity.snapshot != null) for (var original : carriageEntity.snapshot.blocks()) {
            var current = withCabControls(carriageEntity.getContraption().getBlocks().get(original.position()));
            // Actor block data is saved above; static BE data need not be duplicated.
            if (current.state().equals(original.state()) && (actorData.containsKey(original.position())
                    || java.util.Objects.equals(current.nbt(), original.entityData()))) continue;
            var block = new CompoundTag();
            block.put("Pos", NbtUtils.writeBlockPos(current.pos()));
            block.put("State", NbtUtils.writeBlockState(current.state()));
            if (current.nbt() != null) block.put("BlockEntity", current.nbt().copy());
            changed.add(block);
        }
        tag.put("ChangedBlocks", changed);
        tag.put("Passengers", NBTHelper.writeCompoundList(carriageEntity.getContraption().getSeatMapping().entrySet(), e -> {
            var data = new CompoundTag(); data.putUUID("Id", e.getKey()); data.putInt("Seat", e.getValue()); return data;
        }));
        return tag;
    }

    public void readWorkingState(CompoundTag tag) {
        if (!(carriageEntity.getContraption() instanceof SnapshotCarriageStructure.SnapshotView view)) return;
        if (carriageEntity.level() instanceof ServerLevel server && carriageEntity.snapshot != null) {
            var missing = new java.util.HashSet<Tag>();
            for (var block : carriageEntity.snapshot.blocks()) if (block.state().isAir())
                missing.add(NbtUtils.writeBlockPos(block.position()));
            tag = HexieBridges.vehicle().missingBlockMigration().migrate(
                    server, tag, "working-state-" + carriageEntity.getUUID(), missing);
        }
        // Decode the complete update before installing it; malformed later actors must not leave
        // half of the structure replaced. Reuse the snapshot's block/property/type contract.
        var blocks = view.copyBlocks();
        tag = HexieBridges.carriageBlock().headBogeyMigration().working(tag, blocks, carriageEntity.registryAccess());
        var changedPositions = new HashSet<BlockPos>();
        for (var element : workingList(tag, "ChangedBlocks")) {
            var data = (CompoundTag) element;
            BlockPos pos = workingPosition(data, "Pos");
            var previous = blocks.get(pos);
            if (previous == null || !changedPositions.add(pos))
                throw new IllegalArgumentException("Unknown or duplicate working block " + pos);
            var state = SnapshotCarriageStructure.SnapshotView.movingState(HexieBridges.vehicle().carriageSnapshot().readState(data.getCompound("State"), carriageEntity.registryAccess()));
            if (state.getBlock() != previous.state().getBlock())
                throw new IllegalArgumentException("Working state cannot replace a captured block type " + pos);
            CompoundTag entityData = data.contains("BlockEntity") ? data.getCompound("BlockEntity").copy() : null;
            // Older ordinary panels had no BE. Keep the additive entity initialized by the
            // snapshot upgrade when an old state override still lacks that metadata.
            if (entityData == null && previous.nbt() != null
                    && previous.nbt().getString("id").equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                            .getKey(HexieBridges.worldRegistry().blockEntitiesInit().passengerPanel()).toString()))
                entityData = previous.nbt().copy();
            var info = new StructureBlockInfo(pos, state, entityData);
            validateWorkingBlock(info, previous);
            blocks.put(pos, info);
        }
        var actors = new ArrayList<MutablePair<StructureBlockInfo, MovementContext>>();
        var actorPositions = new HashSet<BlockPos>();
        for (var element : workingList(tag, "Actors")) {
            var data = (CompoundTag) element;
            BlockPos pos = workingPosition(data, "Pos");
            var info = blocks.get(pos);
            if (info == null || !actorPositions.add(pos) || MovementBehaviour.REGISTRY.get(info.state()) == null)
                throw new IllegalArgumentException("Unknown working actor " + pos);
            // Older setBlock synthesized empty actor NBT even when the captured block had no entity.
            if (data.contains("BlockEntity") && (info.nbt() != null || !data.getCompound("BlockEntity").isEmpty())) {
                var changed = new StructureBlockInfo(pos, info.state(), data.getCompound("BlockEntity").copy());
                validateWorkingBlock(changed, info);
                info = changed;
                blocks.put(pos, info);
            }
            workingVector(data, "Motion"); workingVector(data, "RelativeMotion");
            if (data.contains("Position")) workingVector(data, "Position");
            if (!data.contains("Data", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing actor data");
            var context = MovementContext.readNBT(carriageEntity.level(), info, data, view);
            context.disabled = data.getBoolean("Disabled");
            actors.add(MutablePair.of(info, context));
        }
        // This movement behaviour did not exist in old saves. Restore only its missing contexts,
        // including unmounted panels so attaching a sign later can bind its live timetable.
        for (var info : blocks.values())
            if (!actorPositions.contains(info.pos())
                    && MovementBehaviour.REGISTRY.get(info.state()) instanceof CreatePassengerDisplayMovement)
                actors.add(view.displayActor(carriageEntity.level(), info));
        var passengers = new HashMap<UUID, Integer>();
        for (var element : workingList(tag, "Passengers")) {
            var data = (CompoundTag) element;
            int seat = data.getInt("Seat");
            if (!data.hasUUID("Id") || !data.contains("Seat", Tag.TAG_INT) || seat < 0 || seat >= view.getSeats().size()
                    || passengers.put(data.getUUID("Id"), seat) != null)
                throw new IllegalArgumentException("Invalid working passenger seat");
        }
        var disabled = NBTHelper.readItemList(workingList(tag, "DisabledActors"), carriageEntity.registryAccess());
        SnapshotCarriageCargo.SnapshotCargoManager cargo = null;
        if (!tag.getBoolean("CargoInCarriage")) {
            cargo = new SnapshotCarriageCargo.SnapshotCargoManager();
            cargo.read(tag, carriageEntity.registryAccess(), false, view);
            verifyWorkingStorage(tag, "items", cargo.getAllItemStorages().keySet());
            verifyWorkingStorage(tag, "fluids", cargo.getFluids().storages.keySet());
        }
        view.restoreBlocks(blocks);
        blocks.values().forEach(info -> view.refreshDisplayTag(carriageEntity.level(), info));
        if (cargo != null) {
            view.replaceStorage(cargo);
            if (carriageEntity.sync.compatibilityCarriage != null) carriageEntity.sync.compatibilityCarriage.storage = cargo;
        }
        view.getActors().clear(); view.getActors().addAll(actors);
        view.getDisabledActors().clear();
        view.getDisabledActors().addAll(disabled);
        view.getSeatMapping().clear();
        view.getSeatMapping().putAll(passengers);
        carriageEntity.behavioursActive = view.active = tag.getBoolean("Active");
        view.stalled = tag.getBoolean("Stalled");
        view.invalidateColliders();
    }

    public void validateWorkingBlock(StructureBlockInfo info, StructureBlockInfo previous) {
        HexieBridges.vehicle().createCarriageSnapshotBlock(info.pos(), info.state(), info.nbt());
        if (info.nbt() == null) return;
        HexieBridges.vehicle().carriageSnapshot().validateEntityType(info.state(), info.nbt(), carriageEntity.registryAccess());
        if (previous.nbt() == null) throw new IllegalArgumentException("Unexpected working block entity");
        for (String axis : List.of("x", "y", "z"))
            if (info.nbt().getInt(axis) != previous.nbt().getInt(axis))
                throw new IllegalArgumentException("Working block entity coordinates changed " + info.pos());
    }

    public static ListTag workingList(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid working list " + key);
        return list;
    }

    public static BlockPos workingPosition(CompoundTag tag, String key) {
        BlockPos position = NBTHelper.readBlockPos(tag, key);
        if (!NbtUtils.writeBlockPos(position).equals(tag.get(key)))
            throw new IllegalArgumentException("Invalid working position " + key);
        return position;
    }

    public static void workingVector(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() != 3 || list.getElementType() != Tag.TAG_DOUBLE)
            throw new IllegalArgumentException("Invalid actor vector " + key);
        for (int i = 0; i < 3; i++) if (!Double.isFinite(list.getDouble(i)))
            throw new IllegalArgumentException("Non-finite actor vector " + key);
    }

    public static void verifyWorkingStorage(CompoundTag tag, String key, Set<BlockPos> restored) {
        // Legacy storage is decoded by Create; current entries must survive its permissive codec reader.
        if (!tag.contains(key)) return;
        var expected = new HashSet<BlockPos>();
        for (var item : workingList(tag, key)) {
            var data = (CompoundTag) item;
            if (!expected.add(workingPosition(data, "pos")) || !data.contains("storage", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid mounted storage entry");
        }
        if (!expected.equals(restored)) throw new IllegalArgumentException("Mounted storage entries failed to decode");
    }

    public void writeAdditional(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
        tag.putUUID("HexieAssembly", carriageEntity.assemblyId);
        tag.putUUID("TrainId", carriageEntity.trainId == null ? carriageEntity.assemblyId : carriageEntity.trainId);
        tag.putInt("CarriageIndex", carriageEntity.carriageIndex);
        if (carriageEntity.motionOwner != null) tag.putUUID("MotionOwner", carriageEntity.motionOwner);
        tag.putString("OriginalForward", carriageEntity.originalForward.getName());
        tag.putBoolean("BehavioursActive", carriageEntity.behavioursActive);
        if (spawnPacket) {
            carriageEntity.baseWriteAdditional(tag, registries, true);
        } else {
            // Native Carriage owns cargo and entity payload; structure data remains in SavedData.
            tag.putBoolean("NativeManaged", carriageEntity.nativeManaged);
            if (carriageEntity.structureImport.awaitingImportedRoute) tag.putBoolean("AwaitingImportedRoute", true);
            if (carriageEntity.structureImport.importedOwner != null) tag.putUUID("StructureOwner", carriageEntity.structureImport.importedOwner);
            if (carriageEntity.getContraption() != null) {
                var working = writeWorkingState(!carriageEntity.nativeManaged);
                if (carriageEntity.nativeManaged) {
                    working.putBoolean("CargoInCarriage", true);
                }
                tag.put("WorkingState", working);
            }
            if (!carriageEntity.nativeManaged) {
                if (carriageEntity.ownedMotion != null) tag.put("MotionState", carriageEntity.ownedMotion.writeState());
                else if (carriageEntity.pendingMotionState != null) tag.put("MotionState", carriageEntity.pendingMotionState.copy());
            }
        }
        tag.putFloat("BodyYaw", carriageEntity.yaw); tag.putFloat("BodyPitch", carriageEntity.pitch); tag.putFloat("BodyRoll", carriageEntity.roll);
        tag.put("BogeyAnimation", carriageEntity.bogeyAnimation.write());
        tag.put("CouplerAnimation", carriageEntity.couplerAnimation.write());
    }

    public void readAdditional(CompoundTag tag, boolean spawnPacket) {
        if (!spawnPacket && carriageEntity.level() instanceof ServerLevel server)
            tag = HexieBridges.vehicle().missingBlockMigration().migrate(
                    server, tag, "carriage-" + carriageEntity.getUUID());
        carriageEntity.assemblyId = tag.getUUID("HexieAssembly");
        carriageEntity.trainId = tag.hasUUID("TrainId") ? tag.getUUID("TrainId") : carriageEntity.assemblyId;
        carriageEntity.carriageIndex = tag.getInt("CarriageIndex");
        carriageEntity.sync.compatibilityCarriage = null;
        carriageEntity.nativeManaged = tag.getBoolean("NativeManaged");
        carriageEntity.restoringNativeEntity = carriageEntity.nativeManaged && !spawnPacket;
        carriageEntity.structureImport.awaitingImportedRoute = !spawnPacket && tag.getBoolean("AwaitingImportedRoute");
        carriageEntity.structureImport.importedOwner = tag.hasUUID("StructureOwner") ? tag.getUUID("StructureOwner") : null;
        carriageEntity.motionOwner = tag.hasUUID("MotionOwner") ? tag.getUUID("MotionOwner") : null;
        Direction forward = Direction.byName(tag.getString("OriginalForward"));
        carriageEntity.originalForward = forward != null && forward.getAxis().isHorizontal() ? forward : Direction.NORTH;
        carriageEntity.behavioursActive = tag.getBoolean("BehavioursActive");
        if (spawnPacket) {
            // A schematic can copy the entity without its SavedData snapshot. Create
            // omits Contraption for that carrier and discards it on the next tick.
            // Preserve the same null state instead of parsing an empty compound.
            if (!tag.contains("Contraption", Tag.TAG_COMPOUND)) {
                carriageEntity.clearStructureForRestore();
                carriageEntity.setStructureInitialized(false);
                carriageEntity.validForRender = false;
                return;
            }
            // The ordinary CARRIAGE factory creates a base view with global storage and
            // bogey-only rendering. Read directly into our one view instead of parsing twice.
            var view = new SnapshotCarriageStructure.SnapshotView();
            view.readNBT(carriageEntity.level(), tag.getCompound("Contraption"), true);
            ContraptionInteractionTargets.ensure(view);
            view.entity = carriageEntity;
            // readNBT alone omits Contraption.fromNBT's collision cache construction.
            // The native collider treats an empty cache as no solid blocks, including for players.
            view.invalidateColliders();
            view.active = carriageEntity.behavioursActive;
            view.configureDirection(carriageEntity.originalForward);
            carriageEntity.restoreStructure(view);
            carriageEntity.setStructureInitialized(tag.getBoolean("Initialized"));
            carriageEntity.setInitialOrientation(carriageEntity.originalForward.getClockWise());
            carriageEntity.xOld = carriageEntity.getX(); carriageEntity.yOld = carriageEntity.getY(); carriageEntity.zOld = carriageEntity.getZ();
            // Create's ordinary MovementContext reader does not serialize the disabled flag.
            if (!carriageEntity.behavioursActive) for (var actor : carriageEntity.getContraption().getActors()) SnapshotCarriageEntity.freeze(actor.right);
        } else if (carriageEntity.level() instanceof ServerLevel server) {
            Vec3 savedPosition = carriageEntity.position();
            var stored = HexieBridges.vehicle().carriageSnapshotStore().get(server).find(carriageEntity.getUUID());
            CompoundTag working = tag.getCompound("WorkingState");
            if (stored.isPresent()) {
                carriageEntity.attach(stored.get(), false);
            } else if (!carriageEntity.nativeManaged) {
                // Native carriage payloads reference one exact snapshot UUID. A retired body
                // must not become an external structure copy of another surviving carriage.
                CarriageSnapshotView source = tag.contains("HexieStructure", Tag.TAG_COMPOUND)
                        ? HexieBridges.vehicle().carriageSnapshot().load(tag.getCompound("HexieStructure"), carriageEntity.registryAccess()) : null;
                // Old blueprints lack a stable carriage key. A single-car source assembly
                // is unambiguous; a native carriage index may have changed after regrouping.
                var oldAssembly = HexieBridges.vehicle().carriageSnapshotStore().get(server).active(carriageEntity.assemblyId).orElse(null);
                if (source == null && oldAssembly != null && oldAssembly.carriages().size() == 1) {
                    UUID sourceId = oldAssembly.carriages().getFirst();
                    source = HexieBridges.vehicle().carriageSnapshotStore().get(server).find(sourceId).orElse(null);
                    if (source != null && working.getBoolean("CargoInCarriage")) {
                        var train = Create.RAILWAYS.trains.values().stream()
                                .filter(value -> value instanceof CompatibilityTrain compatible && compatible.carriageIds.contains(sourceId))
                                .map(value -> (CompatibilityTrain) value).findFirst().orElse(null);
                        if (train == null) source = null;
                        else {
                            working = working.copy();
                            train.carriages.get(train.carriageIds.indexOf(sourceId)).storage.write(working, carriageEntity.registryAccess(), false);
                            working.remove("CargoInCarriage");
                            carriageEntity.structureImport.importedOwner = train.owner;
                        }
                    }
                }
                if (source != null) {
                    carriageEntity.assemblyId = UUID.randomUUID();
                    carriageEntity.trainId = carriageEntity.assemblyId;
                    carriageEntity.carriageIndex = 0;
                    carriageEntity.motionOwner = null;
                    carriageEntity.nativeManaged = carriageEntity.restoringNativeEntity = false;
                    carriageEntity.structureImport.importingStructure = true;
                    carriageEntity.attach(HexieBridges.vehicle().carriageSnapshot().create(carriageEntity.getUUID(), server.dimension(), source.origin(), List.copyOf(source.blocks()),
                            source.supports(), source.glue()), false);
                    working = working.copy();
                    // A structure copy does not acquire passengers from the original vehicle.
                    working.put("Passengers", new ListTag());
                }
            }
            carriageEntity.setPos(savedPosition);
            if (carriageEntity.getContraption() != null && tag.contains("WorkingState")) readWorkingState(working);
            if (!carriageEntity.structureImport.importingStructure && carriageEntity.getContraption() != null && tag.contains("MotionState")) {
                try { carriageEntity.installMotion(AssembledVehicleMotionPersistence.readState(server, tag.getCompound("MotionState"))); }
                catch (AssembledVehicleMotion.RouteUnavailableException
                        | AssembledVehicleMotion.SourceUnavailableException temporarilyUnavailable) {
                    // Track/source loading must not make EntityType.load discard the vehicle and inventories.
                    carriageEntity.pendingMotionState = tag.getCompound("MotionState").copy();
                    carriageEntity.pendingSourceUnavailable = temporarilyUnavailable instanceof AssembledVehicleMotion.SourceUnavailableException;
                    carriageEntity.nextMotionRetryTick = 0;
                }
            }
            // A stale entity saved before dismantling has no snapshot and is discarded
            // by AbstractContraptionEntity's existing null-contraption check.
        }
        carriageEntity.yaw = carriageEntity.prevYaw = tag.getFloat("BodyYaw");
        carriageEntity.pitch = carriageEntity.prevPitch = tag.getFloat("BodyPitch");
        carriageEntity.roll = carriageEntity.prevRoll = tag.getFloat("BodyRoll");
        if (tag.contains("BogeyAnimation")) carriageEntity.readBogeyAnimation(carriageEntity.structureImport.importingStructure
                ? CarriageStructureTransform.copyAnimation(carriageEntity.getUUID(), tag.getCompound("BogeyAnimation"), net.minecraft.world.level.block.Mirror.NONE)
                : tag.getCompound("BogeyAnimation"), 0);
        if (tag.contains("CouplerAnimation")) carriageEntity.readCouplerAnimation(carriageEntity.structureImport.importingStructure
                ? CarriageStructureTransform.copyAnimation(carriageEntity.getUUID(), tag.getCompound("CouplerAnimation"), net.minecraft.world.level.block.Mirror.NONE)
                : tag.getCompound("CouplerAnimation"), 0);
        carriageEntity.validForRender = true;
        carriageEntity.firstPositionUpdate = false;
        if (carriageEntity.ownedMotion != null) {
            carriageEntity.ownedMotion.applyCurrent(carriageEntity);
            carriageEntity.prevYaw = carriageEntity.yaw; carriageEntity.prevPitch = carriageEntity.pitch; carriageEntity.prevRoll = carriageEntity.roll;
            carriageEntity.xo = carriageEntity.getX(); carriageEntity.yo = carriageEntity.getY(); carriageEntity.zo = carriageEntity.getZ();
        }
        if (!carriageEntity.level().isClientSide && !carriageEntity.structureImport.importingStructure) carriageEntity.syncCarriage();
        carriageEntity.updateRotatedBounds();
    }
}
