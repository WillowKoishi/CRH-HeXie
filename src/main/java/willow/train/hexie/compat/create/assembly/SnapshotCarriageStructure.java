package willow.train.hexie.compat.create.assembly;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.MutablePair;
import com.simibubi.create.AllTags.AllContraptionTypeTags;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.ContraptionBlockChangedPacket;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.contraptions.render.ClientContraption.RenderedBlocks;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageFloorViewView;
import willow.train.hexie.bridge.world.BlockEntityViews.BlockEntityAnimationView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageViewBlockEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.HexieBogeyBlockEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.ModelRenderBoundsView;
import willow.train.hexie.compat.create.interaction.ContraptionInteractionTargets;
import willow.train.hexie.compat.create.motion.SnapshotBogeyAnimation;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.core.api.cab.CabControlsHost;
import willow.train.hexie.core.api.cab.CabDisplayHost;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageStructure {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageStructure(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public void setBlock(BlockPos localPos, StructureBlockInfo info) {
        if (carriageEntity.getContraption() == null || !carriageEntity.getContraption().getBlocks().containsKey(localPos)) return;
        var state = SnapshotView.movingState(info.state());
        if (state != info.state()) info = new StructureBlockInfo(info.pos(), state, info.nbt());
        carriageEntity.getContraption().getBlocks().put(localPos, info);
        ContraptionInteractionTargets.update(carriageEntity.getContraption(), localPos, info.state());
        if (carriageEntity.getContraption() instanceof SnapshotView view) view.refreshDisplayTag(carriageEntity.level(), info);
        var actor = carriageEntity.getContraption().getActorAt(localPos);
        if (actor != null && actor.left.state().getBlock() == info.state().getBlock()) {
            actor.left = info;
            actor.right.state = info.state();
            actor.right.blockEntityData = info.nbt() == null ? null : info.nbt().copy();
        }
        carriageEntity.getContraption().invalidateColliders();
        if (!carriageEntity.level().isClientSide)
            CatnipServices.NETWORK.sendToClientsTrackingEntity(carriageEntity,
                    new ContraptionBlockChangedPacket(carriageEntity.getId(), localPos, info.state()));
    }

    public void attach(CarriageSnapshotView snapshot, boolean mountStorage) {
        carriageEntity.snapshot = snapshot;
        var view = new SnapshotView();
        ContraptionInteractionTargets.begin(view);
        view.anchor = snapshot.origin();
        view.setGlue(snapshot.glue());
        AABB bounds = null;
        for (var block : snapshot.blocks()) {
            var state = SnapshotView.movingState(block.state());
            var data = block.entityData();
            var info = new StructureBlockInfo(block.position(), state, data);
            view.getBlocks().put(block.position(), info);
            ContraptionInteractionTargets.update(view, block.position(), state);
            if (mountStorage)
                view.getStorage().addBlock(carriageEntity.level(), state, snapshot.origin().offset(block.position()),
                        block.position(), carriageEntity.level().getBlockEntity(snapshot.origin().offset(block.position())));
            var interaction = MovingInteractionBehaviour.REGISTRY.get(state);
            if (interaction != null) view.getInteractors().put(block.position(), interaction);
            if (state.getBlock() instanceof SeatBlock) {
                view.getSeats().add(block.position());
                if (mountStorage) {
                    // Same local seat lookup as Create's moveSeat, only for seats already captured by our scanner.
                    for (var seat : carriageEntity.level().getEntitiesOfClass(SeatEntity.class,
                            new AABB(snapshot.origin().offset(block.position())))) {
                        if (seat.getPassengers().isEmpty()) continue;
                        view.initialSeatPassengers.put(block.position(), seat.getPassengers().getFirst());
                        break;
                    }
                }
            }
            if (data != null) {
                // Match Create's ordinary spawn payload: send the visual update tag,
                // not inventories/full block entity saves to every tracking client.
                var blockEntity = BlockEntity.loadStatic(snapshot.origin().offset(block.position()), state, data,
                        carriageEntity.level().registryAccess());
                if (blockEntity == null) throw new IllegalStateException("Could not restore carriage block entity");
                blockEntity.setLevel(carriageEntity.level());
                view.update(block.position(), SnapshotView.displayTag(carriageEntity.level(), blockEntity, data));
                view.localizeMultiblock(info, blockEntity);
            }
            view.addDisplayActor(carriageEntity.level(), info);
            var box = new AABB(block.position());
            if (state.getBlock() instanceof CRH2HeadBlockView)
                box = box.minmax(HexieBridges.carriageBlock().headGeometry().bounds(
                        state.getValue(HexieBridges.carriageBlock().crh2HeadBlock().facing())).move(block.position()));
            if (state.getBlock() instanceof ModelRenderBoundsView model)
                box = box.minmax(model.modelRenderBounds(state).move(block.position()));
            var hook = HexieBridges.couplerBlock().couplerBlock().of(state);
            if (hook != null && hook.geometry() != null)
                box = box.minmax(hook.geometry().bounds(hook.facing(state)).move(block.position()));
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        view.bounds = bounds;
        // Keep the same persistent envelope as a native station-assembled carriage.
        // Create reuses contraption.bounds when aligning its compatibility entity;
        // updating only this entity's box loses the interaction reach on that pass.
        view.expandBoundsAroundAxis(Direction.Axis.Y);
        // Create rounds the preserved Y interval and derives its radius from block cells.
        // Retain the original model envelope too, including fractional height and overhangs.
        view.bounds = view.bounds.minmax(bounds);
        view.configureDirection(carriageEntity.originalForward);
        carriageEntity.setInitialOrientation(Direction.SOUTH);
        carriageEntity.startAtInitialYaw();
        carriageEntity.installStructure(view);
        if (carriageEntity.sync.compatibilityCarriage != null) carriageEntity.sync.compatibilityCarriage.storage = view.cargo();
        carriageEntity.validForRender = true;
        carriageEntity.firstPositionUpdate = false;
        BlockPos origin = snapshot.origin();
        carriageEntity.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        carriageEntity.updateRotatedBounds();
        carriageEntity.xo = carriageEntity.getX(); carriageEntity.yo = carriageEntity.getY(); carriageEntity.zo = carriageEntity.getZ();
    }

    public static final class SnapshotView extends CarriageContraption {
        public boolean active;
        private Direction direction = Direction.NORTH;
        private boolean forwardControls;
        private boolean backwardControls;
        public final Map<BlockPos, Entity> initialSeatPassengers = new HashMap<>();
        public SnapshotView() { storage = new SnapshotCarriageCargo.SnapshotCargoManager(); }

        // Match Create's capture/placement rules for every sliding or folding door, without block-ID lists.
        public static BlockState movingState(BlockState state) {
            return state.hasProperty(SlidingDoorBlock.VISIBLE) ? state.setValue(SlidingDoorBlock.VISIBLE, false) : state;
        }

        public static BlockState placedState(BlockState state) {
            return state.hasProperty(SlidingDoorBlock.VISIBLE)
                    ? state.setValue(SlidingDoorBlock.VISIBLE, !state.getValue(SlidingDoorBlock.OPEN))
                        .setValue(SlidingDoorBlock.POWERED, false)
                    : state;
        }

        @Override public ContraptionWorld getContraptionWorld() {
            if (!(collisionLevel instanceof FloorWorld)) collisionLevel = new FloorWorld(entity.level(), this);
            return collisionLevel;
        }
        void update(BlockPos position, CompoundTag tag) { updateTags.put(position, tag); }

        public Map<BlockPos, StructureBlockInfo> copyBlocks() {
            var copy = new HashMap<BlockPos, StructureBlockInfo>();
            for (var info : getBlocks().values()) {
                CompoundTag data = info.nbt();
                copy.put(info.pos(), new StructureBlockInfo(info.pos(), info.state(),
                        data == null ? null : data.copy()));
            }
            return copy;
        }

        public void restoreBlocks(Map<BlockPos, StructureBlockInfo> saved) {
            getBlocks().clear();
            ContraptionInteractionTargets.begin(this);
            saved.forEach((pos, info) -> {
                getBlocks().put(pos, new StructureBlockInfo(pos, info.state(), info.nbt() == null ? null : info.nbt().copy()));
                ContraptionInteractionTargets.update(this, pos, info.state());
            });
        }

        Map<BlockPos, List<BlockPos>> multiblockPositions() {
            var positions = new HashMap<BlockPos, List<BlockPos>>();
            capturedMultiblocks.asMap().forEach((controller, parts) ->
                    positions.put(controller, parts.stream().map(StructureBlockInfo::pos).toList()));
            return positions;
        }

        void bindMultiblocks(Map<BlockPos, List<BlockPos>> positions) {
            capturedMultiblocks.clear();
            positions.forEach((controller, parts) -> parts.forEach(pos -> {
                var info = getBlocks().get(pos);
                if (info != null) capturedMultiblocks.put(controller, info);
            }));
        }

        List<AABB> glue() { return List.copyOf(superglue); }
        void setGlue(List<AABB> boxes) { superglue.clear(); superglue.addAll(boxes); }

        @Override public void addBlocksToWorld(Level world, StructureTransform transform) {
            // The transaction installed the live view already. The later native entity
            // disassembly still performs passengers, notifications and final removal, without
            // entering mod hooks on the base placement method a second time.
            if (disassembled) return;
            try { super.addBlocksToWorld(world, transform); }
            finally { cargo().clearDisassemblyView(); }
        }

        @Override protected void translateMultiblockControllers(StructureTransform transform) {
            var controllerData = new HashMap<BlockPos, CompoundTag>();
            capturedMultiblocks.keySet().forEach(pos -> {
                var info = blocks.get(pos);
                if (info != null && info.nbt() != null) controllerData.put(pos, info.nbt());
            });
            super.translateMultiblockControllers(transform);
            // Create moves a controller's NBT to the new minimum corner, but leaves mounted
            // storage keyed at the old corner. Follow the actual native NBT move, without
            // duplicating its orientation/controller rules or replacing storage objects.
            var positionsByData = new IdentityHashMap<CompoundTag, BlockPos>();
            capturedMultiblocks.values().forEach(part -> {
                var info = blocks.get(part.pos());
                if (info != null && info.nbt() != null) positionsByData.put(info.nbt(), info.pos());
            });
            var movedControllers = new HashMap<BlockPos, BlockPos>();
            controllerData.forEach((before, data) -> {
                var after = positionsByData.get(data);
                if (after != null && !after.equals(before)) movedControllers.put(before, after);
            });
            if (!movedControllers.isEmpty())
                cargo().prepareDisassemblyView(multiblockPositions(), movedControllers);
        }

        public void refreshDisplayTag(Level level, StructureBlockInfo info) {
            updateTags.remove(info.pos());
            if (info.nbt() == null || !info.state().hasBlockEntity()) return;
            var blockEntity = BlockEntity.loadStatic(info.pos(), info.state(), info.nbt(), level.registryAccess());
            if (blockEntity == null) return;
            blockEntity.setLevel(level);
            updateTags.put(info.pos(), displayTag(level, blockEntity, info.nbt()));
        }

        static CompoundTag displayTag(Level level, BlockEntity blockEntity, CompoundTag saved) {
            var update = blockEntity.getUpdateTag(level.registryAccess());
            if (blockEntity instanceof DeployerBlockEntity) {
                update.remove("Inventory");
                update.remove("Overflow");
                if (saved.contains("HeldItem")) update.put("HeldItem", saved.get("HeldItem").copy());
            }
            return update;
        }

        public SnapshotCarriageCargo.SnapshotCargoManager cargo() { return (SnapshotCarriageCargo.SnapshotCargoManager) storage; }
        public void replaceStorage(SnapshotCarriageCargo.SnapshotCargoManager cargo) { storage = cargo; }
        public void storageFromCarriage(Carriage carriage) { storage = carriage.storage; }

        @Override public MountedStorageManager getStorage() { return storage; }

        @Override public void writeStorage(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
            storage.write(tag, registries, spawnPacket);
        }

        @Override public void swapStorageAfterAssembly(CarriageContraptionEntity entity) {
            if (entity.getCarriage() != null) entity.getCarriage().storage = cargo();
        }

        @Override protected ClientContraption createClientContraption() {
            // Retain BEs for their styles/data. Only supports with animation data leave the
            // ordinary BER pass; old saves keep their static models until motion is available.
            return new ClientContraption(this) {
                private final java.util.BitSet staticBlocks = new java.util.BitSet();
                private final java.util.BitSet visible = new java.util.BitSet();
                private int maskedVersion = -1;

                @Override public BlockEntity readBlockEntity(net.minecraft.world.level.Level level,
                        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info,
                        boolean legacy) {
                    var be = super.readBlockEntity(level, info, legacy);
                    if (be instanceof HexieBogeyBlockEntityView bogey) bogey.markCarriageView();
                    if (be instanceof CRH2HeadEntityView head) head.markCarriageView();
                    if (be instanceof CarriageViewBlockEntityView view) view.markCarriageView();
                    // ClientContraption's constructor creates BEs without calling resetRenderLevel.
                    // Bind at creation so the first render and every rebuild read live state.
                    if (be instanceof CabDisplayHost console)
                        console.displaySource(() -> ((SnapshotCarriageEntity) SnapshotView.this.entity).cabDisplayState(info.pos()));
                    if (be instanceof CabControlsHost console)
                        console.controlsSource(() -> ((SnapshotCarriageEntity) SnapshotView.this.entity).cabControls(info.pos()));
                    return be;
                }

                @Override public void resetRenderLevel() {
                    // CRH2 doors read their actor's progress when the display BE is created.
                    // Other animated blocks carry their progress across rebuilds in the display BE.
                    var previousDoors = new java.util.HashMap<BlockPos, BlockEntity>();
                    for (var be : renderedBlockEntityView)
                        if (be instanceof BlockEntityAnimationView
                                && !(be.getBlockState().getBlock() instanceof CRH2DoorBlockView)
                                && !(be.getBlockState().getBlock() instanceof CRH1DoorBlockView))
                            previousDoors.put(be.getBlockPos(), be);
                    super.resetRenderLevel();
                    for (var entry : previousDoors.entrySet()) {
                        var before = entry.getValue();
                        var after = getBlockEntity(entry.getKey());
                        if (after == null || before.getBlockState().getBlock() != after.getBlockState().getBlock()) continue;
                        if (after instanceof BlockEntityAnimationView animation)
                            animation.restoreAnimation(before);
                    }
                }

                @Override public RenderedBlocks getRenderedBlocks() {
                    // Snapshots store the ground state; Create opens the controls when capturing a carriage.
                    boolean openControls = AllContraptionTypeTags.OPENS_CONTROLS.matches(SnapshotView.this.getType());
                    return new RenderedBlocks(pos -> {
                        var info = SnapshotView.this.getBlocks().get(pos);
                        if (info == null) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                        if (openControls && info.state().getBlock() instanceof ControlsBlock)
                            return info.state().setValue(ControlsBlock.OPEN, true);
                        var animation = ((SnapshotCarriageEntity) entity).couplerAnimation();
                        var visual = animation.visual(pos);
                        if (visual != null) return HexieBridges.coupling().couplerDataRegistry().renderState(
                                animation.hasShiftedBase(pos) ? visual.shiftedFixed() : visual.fixed());
                        var hook = info.state().getBlock() instanceof CouplerBlockView registered ? registered : null;
                        return hook != null
                                ? hook.carriageRenderState(info.state(),
                                        ((SnapshotCarriageEntity) entity).couplerAnimation().hasShiftedBase(pos))
                                : info.state();
                    }, SnapshotView.this.getBlocks().keySet());
                }

                @Override public java.util.BitSet getAndAdjustShouldRenderBlockEntities() {
                    if (maskedVersion != childrenVersion()) {
                        var animated = ((SnapshotCarriageEntity) entity).bogeyAnimation().frames().stream()
                                .map(SnapshotBogeyAnimation.Frame::position).collect(java.util.stream.Collectors.toSet());
                        var animatedHooks = ((SnapshotCarriageEntity) entity).couplerAnimation().frames().stream()
                                .map(SnapshotBogeyAnimation.Frame::position).collect(java.util.stream.Collectors.toSet());
                        staticBlocks.clear();
                        for (int i = 0; i < renderedBlockEntityView.size(); i++) {
                            var blockEntity = renderedBlockEntityView.get(i);
                            boolean independentSupport = blockEntity instanceof AbstractBogeyBlockEntity
                                    && animated.contains(blockEntity.getBlockPos());
                            boolean independentHook = blockEntity instanceof CouplerBlockEntityView
                                    && animatedHooks.contains(blockEntity.getBlockPos());
                            if (!independentSupport && !independentHook)
                                staticBlocks.set(i);
                        }
                        maskedVersion = childrenVersion();
                    }
                    visible.clear();
                    visible.or(super.getAndAdjustShouldRenderBlockEntities());
                    visible.and(staticBlocks);
                    return visible;
                }
            };
        }

        @Override public Direction getAssemblyDirection() { return direction; }
        @Override public boolean hasForwardControls() { return forwardControls; }
        @Override public boolean hasBackwardControls() { return backwardControls; }

        public void configureDirection(Direction direction) {
            this.direction = direction;
            forwardControls = backwardControls = false;
            blockConductors = Couple.create(false, false);
            conductorSeats.clear();
            for (var info : getBlocks().values()) {
                if (info.state().getBlock() instanceof ControlsBlock) {
                    var facing = info.state().getValue(ControlsBlock.FACING);
                    forwardControls |= facing == direction;
                    backwardControls |= facing == direction.getOpposite();
                }
                var interaction = MovingInteractionBehaviour.REGISTRY.get(info.state());
                if (interaction instanceof ConductorBlockInteractionBehavior conductor
                        && conductor.isValidConductor(info.state())) {
                    for (Direction facing : List.of(direction, direction.getOpposite()))
                        if (inControl(info.pos(), facing)) blockConductors.set(facing != direction, true);
                }
            }
            for (var seat : getSeats())
                for (Direction facing : List.of(direction, direction.getOpposite()))
                    if (inControl(seat, facing))
                        conductorSeats.computeIfAbsent(seat, p -> Couple.create(false, false)).set(facing != direction, true);
        }

        void localizeMultiblock(StructureBlockInfo info, BlockEntity blockEntity) {
            if (!(blockEntity instanceof IMultiBlockEntityContainer)) return;
            // Existing Create conversion operates on this view's copies, never the stored snapshot.
            captureMultiblock(info.pos(), info, blockEntity);
            var update = updateTags.get(info.pos());
            if (update == null || !update.contains("Controller")) return;
            // The view never ticks connectivity. Do not leave an original-world relocation marker.
            update.remove("LastKnownPos");
            if (info.nbt().contains("LastKnownPos"))
                update.put("LastKnownPos", info.nbt().get("LastKnownPos").copy());
        }

        void addDisplayActor(Level level, StructureBlockInfo info) {
            if (MovementBehaviour.REGISTRY.get(info.state()) == null) return;
            actors.add(displayActor(level, info));
        }

        public MutablePair<StructureBlockInfo, MovementContext> displayActor(Level level, StructureBlockInfo info) {
            var context = new MovementContext(level, info, this);
            SnapshotCarriageEntity.freeze(context);
            context.position = Vec3.atCenterOf(anchor.offset(info.pos()));
            return MutablePair.of(info, context);
        }

        @Override public void stop(Level world) {
            if (!active) return;
            active = false;
            super.stop(world);
        }

        @Override
        public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
            if (active && entity instanceof SnapshotCarriageEntity carrier && carrier.canRunBehaviours()) {
                var tag = super.writeNBT(registries, spawnPacket);
                tag.putBoolean("FrontControls", forwardControls);
                tag.putBoolean("BackControls", backwardControls);
                return tag;
            }
            // The normal actor writer calls writeExtraData, which may create a deployer fake player
            // or change its inventory. Reuse every other Create field and write only inert context.
            var displayActors = actors;
            CompoundTag tag;
            actors = List.of();
            try { tag = super.writeNBT(registries, spawnPacket); }
            finally { actors = displayActors; }
            var serializedActors = new ListTag();
            for (var actor : displayActors) {
                var data = new CompoundTag();
                data.put("Pos", NbtUtils.writeBlockPos(actor.left.pos()));
                serializedActors.add(actor.right.writeToNBT(data));
            }
            tag.put("Actors", serializedActors);
            tag.putBoolean("FrontControls", forwardControls);
            tag.putBoolean("BackControls", backwardControls);
            return tag;
        }
    }

    /** Exposes the carriage floor to native shape queries without altering saved block states. */
    private static final class FloorWorld extends ContraptionWorld implements CarriageFloorViewView {
        private final SnapshotView view;

        FloorWorld(Level level, SnapshotView view) { super(level, view); this.view = view; }

        @Override public double floorOffset() {
            return view.entity instanceof SnapshotCarriageEntity carrier ? carrier.couplerAnimation.floorOffset() : 0;
        }
    }
}
