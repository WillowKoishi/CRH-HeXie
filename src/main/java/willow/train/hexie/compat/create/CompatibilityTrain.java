package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.simibubi.create.Create;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Create's public train identity and control state, never a second movement or storage owner. */
@EventBusSubscriber(modid = "hexie")
public final class CompatibilityTrain extends Train {
    private static final java.util.Map<MinecraftServer, java.util.LinkedHashSet<CompatibilityTrain>> PENDING_TICKS
            = new java.util.WeakHashMap<>();
    public static final String ASSEMBLY_KEY = "HeXieAssembly";
    public final UUID assemblyId;
    public final List<UUID> carriageIds;
    CreateTrainBridge bridge;
    double requestedSpeed = Double.NaN;
    int mailTransferTicks;
    UUID unresolvedGraphId;
    private CompoundTag savedMotion;
    private UUID savedMotionOwner;
    private String motionDimension;
    private long retryAt;
    private long pendingTick = Long.MIN_VALUE;
    private PendingVehicleRelocation pendingRelocation;
    boolean hasLifecycleData() { return bridge != null || savedMotionOwner != null; }

    CompatibilityTrain(UUID id, UUID owner, UUID assembly, List<UUID> ids, TrackGraph graph,
                       List<Carriage> carriages, List<Integer> spacing) {
        super(id, owner, graph, carriages, spacing, true, 0);
        assemblyId = assembly;
        carriageIds = List.copyOf(ids);
        updateSignalBlocks = true;
    }

    /** Create's global railway tick also runs outside entity-ticking chunks. */
    @Override public void tick(Level level) {
        if (!(level instanceof ServerLevel server) || Create.RAILWAYS.trains.get(id) != this) return;
        // Restoring/regrouping can add/remove native trains. Run after Create finishes iterating
        // its live train lists, on this same server tick, rather than mutating those iterators.
        PENDING_TICKS.computeIfAbsent(server.getServer(), ignored -> new java.util.LinkedHashSet<>()).add(this);
    }

    @SubscribeEvent
    public static void finishLogicalTicks(ServerTickEvent.Post event) {
        var pending = PENDING_TICKS.remove(event.getServer());
        if (pending == null) return;
        for (var train : pending) train.restoreForTick(event.getServer().overworld());
        // Bring contacting domains together before either side advances. Preparation does not
        // move a carriage or create a link; the runtime resolves the exact swept contact.
        VehicleContactPreparation.prepare();
        for (var value : List.copyOf(Create.RAILWAYS.trains.values()))
            if (value instanceof CompatibilityTrain train) train.tickLogical(event.getServer().overworld());
    }

    private void restoreForTick(ServerLevel server) {
        if (Create.RAILWAYS.trains.get(id) != this) return;
        if (bridge == null && savedMotionOwner != null) {
            if (Create.RAILWAYS.trains.get(savedMotionOwner) instanceof CompatibilityTrain ownerTrain
                    && ownerTrain != this) ownerTrain.restoreMotion(server);
            else restoreMotion(server);
        }
    }

    private void tickLogical(ServerLevel server) {
        restoreForTick(server);
        var current = Create.RAILWAYS.trains.get(id);
        if (!(current instanceof CompatibilityTrain train)) return;
        if (train.bridge != null) train.bridge.motion().tickLogic();
        else {
            // Like native Train.tick with no graph: materialize the saved body in active
            // chunks even while its route is unavailable. This does not move or load chunks.
            train.carriages.forEach(carriage -> carriage.manageEntities(server));
        }
    }

    private void restoreMotion(ServerLevel server) {
        if (bridge != null || savedMotion == null) return;
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(motionDimension));
        var world = server.getServer().getLevel(dimension);
        if (world == null) return;
        if (pendingRelocation == null) pendingRelocation = new PendingVehicleRelocation(world, savedMotion);
        var pending = pendingRelocation;
        if (pendingTick != world.getGameTime()) {
            pendingTick = world.getGameTime();
            if (pending.tick()) {
                // Partial recovery may have assigned this same train a new remainder checkpoint.
                // Do not discard the new detector installed by retainPendingMotion in that case.
                if (pendingRelocation == pending) pendingRelocation = null;
                return;
            }
        }
        if (server.getGameTime() < retryAt) return;
        retryAt = server.getGameTime() + 100;
        try { AssembledVehicleMotion.readState(world, savedMotion).ensureCompatibility(); }
        catch (AssembledVehicleMotion.RouteUnavailableException unavailable) {
            // The graph may be loading or temporarily disconnected; retain the complete save.
        }
    }

    /**
     * Keep a pending domain's unselected complete consists in their existing native records.
     * No entity is required: one native train owns the filtered motion save, and its peers refer
     * to that train. Returns the saved owner carriage for any loaded carriers' fallback links.
     */
    static UUID retainPendingMotion(ServerLevel level, Set<UUID> remainingIds, CompoundTag pending) {
        Set<UUID> remaining = Set.copyOf(remainingIds);
        var sources = AssembledVehicleMotion.readSources(level, pending);
        if (remaining.isEmpty() || !remaining.equals(sources.keySet())
                || !level.dimension().location().toString().equals(pending.getCompound("Route").getString("Dimension")))
            throw new IllegalArgumentException("Pending remainder does not match its carriages or dimension");
        var trains = new ArrayList<CompatibilityTrain>();
        var covered = new HashSet<UUID>();
        var owners = new HashMap<UUID, CompatibilityTrain>();
        for (var value : Create.RAILWAYS.trains.values()) {
            if (!(value instanceof CompatibilityTrain train)
                    || train.carriageIds.stream().noneMatch(remaining::contains)) continue;
            if (train.bridge != null || !remaining.containsAll(train.carriageIds)
                    || !train.assemblyId.equals(pending.getUUID("Assembly")))
                throw new IllegalStateException("Pending remainder overlaps another live or connected native train");
            for (UUID id : train.carriageIds) {
                if (!covered.add(id)) throw new IllegalStateException("A pending carriage has duplicate native owners");
                owners.put(id, train);
            }
            trains.add(train);
        }
        if (!covered.equals(remaining)) throw new IllegalStateException("Pending remainder is missing native carriage records");
        UUID ownerCarriage = pending.getCompound("Runtime").getList("Carriages", Tag.TAG_COMPOUND).getCompound(0).getUUID("Id");
        var owner = owners.get(ownerCarriage);
        var saved = pending.copy();
        // Finish all decoding before mutating ownership, so malformed subsets cannot consume
        // the old owner or discard the only saved motion of an unloaded consist.
        var detector = new PendingVehicleRelocation(level, saved);
        for (var train : trains) {
            train.savedMotionOwner = owner.id;
            train.savedMotion = train == owner ? saved : null;
            train.motionDimension = level.dimension().location().toString();
            train.pendingRelocation = train == owner ? detector : null;
            train.pendingTick = Long.MIN_VALUE;
            train.retryAt = level.getGameTime() + 100;
        }
        return ownerCarriage;
    }

    @Override public void earlyTick(Level level) {
        if (Create.RAILWAYS.trains.get(id) != this) return;
        // Keep occupied/reserved groups visible even while the owning entity is unloaded.
        super.earlyTick(level);
        if (graph != null) unresolvedGraphId = null;
    }

    @Override public void approachTargetSpeed(float accelerationMod) {
        requestedSpeed = targetSpeed;
        if (manualTick) leaveStation();
    }

    @Override public float acceleration() {
        return bridge == null ? super.acceleration() : bridge.brakingAcceleration(this);
    }

    @Override public void crash() {
        unresolvedGraphId = null;
        super.crash();
        if (bridge != null) bridge.crashed(this);
    }

    @Override public void detachFromTracks() {
        unresolvedGraphId = null;
        super.detachFromTracks();
    }

    /** World restoration always remains in the snapshot transaction. */
    @Override public boolean disassemble(Direction direction, BlockPos position) {
        return bridge != null && bridge.disassemble(this);
    }
    @Override public boolean canDisassemble() { return bridge != null && bridge.canDisassemble(this); }

    @Override public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
        CompoundTag tag = super.write(dimensions, registries);
        if (graph == null && unresolvedGraphId != null) tag.putUUID("Graph", unresolvedGraphId);
        tag.putUUID(ASSEMBLY_KEY, assemblyId);
        ListTag ids = new ListTag();
        for (UUID id : carriageIds) { var entry = new CompoundTag(); entry.putUUID("Id", id); ids.add(entry); }
        tag.put("HeXieCarriages", ids);
        if (bridge != null) {
            var ownerTrain = bridge.trains().getFirst();
            tag.putUUID("HeXieMotionOwner", ownerTrain.id);
            tag.putString("HeXieMotionDimension", bridge.motion().level().dimension().location().toString());
            if (ownerTrain == this) tag.put("HeXieMotion", bridge.motion().writeState());
        } else if (savedMotionOwner != null) {
            tag.putUUID("HeXieMotionOwner", savedMotionOwner);
            tag.putString("HeXieMotionDimension", motionDimension);
            if (savedMotion != null) tag.put("HeXieMotion", savedMotion.copy());
        }
        return tag;
    }

    /** Loaded before entities; the saved logical owner can resume without loading their chunks. */
    public static CompatibilityTrain fromSaved(Train source, CompoundTag tag, HolderLookup.Provider registries,
                                               DimensionPalette dimensions) {
        var ids = new ArrayList<UUID>();
        for (var entry : tag.getList("HeXieCarriages", Tag.TAG_COMPOUND)) ids.add(((CompoundTag) entry).getUUID("Id"));
        if (ids.size() != source.carriages.size()) throw new IllegalArgumentException("Invalid compatibility carriage identities");
        // Preserve Create's saved entity/passenger payload and live cargo, including unloaded cars.
        var carriages = source.carriages;
        var result = new CompatibilityTrain(source.id, source.owner, tag.getUUID(ASSEMBLY_KEY), ids,
                source.graph, carriages, source.carriageSpacing);
        // A saved graph reference can be read before the railway graph itself is loaded.
        // This differs from Create deliberately detaching a live train from its tracks.
        if (source.graph == null && tag.hasUUID("Graph")) result.unresolvedGraphId = tag.getUUID("Graph");
        result.name = source.name; result.icon = source.icon; result.mapColorIndex = source.mapColorIndex;
        result.speed = source.speed; result.targetSpeed = source.targetSpeed; result.throttle = source.throttle;
        result.speedBeforeStall = source.speedBeforeStall;
        result.carriageWaitingForChunks = source.carriageWaitingForChunks;
        result.migrationCooldown = source.migrationCooldown;
        ((CreateTrainAccess) (Object) result).hexie$getMigratingPoints()
                .addAll(((CreateTrainAccess) (Object) source).hexie$getMigratingPoints());
        result.currentStation = source.currentStation; result.currentlyBackwards = source.currentlyBackwards;
        result.derailed = source.derailed; result.updateSignalBlocks = source.updateSignalBlocks;
        result.occupiedSignalBlocks.putAll(source.occupiedSignalBlocks);
        result.reservedSignalBlocks.addAll(source.reservedSignalBlocks);
        result.occupiedObservers.addAll(source.occupiedObservers);
        result.runtime.read(registries, tag.getCompound("Runtime"));
        result.navigation.read(tag.getCompound("Navigation"), result.graph, dimensions);
        result.savedMotionOwner = tag.hasUUID("HeXieMotionOwner") ? tag.getUUID("HeXieMotionOwner") : null;
        result.savedMotion = tag.contains("HeXieMotion") ? tag.getCompound("HeXieMotion").copy() : null;
        result.motionDimension = tag.getString("HeXieMotionDimension");
        var station = result.getCurrentStation();
        if (station != null) { station.cancelReservation(source); station.reserveFor(result); }
        return result;
    }

    /** Native Carriage serialization owns entity, passenger and cargo lifecycle. */
    static final class ReferenceCarriage extends Carriage {
        ReferenceCarriage(CarriageBogey first, CarriageBogey last, int spacing) { super(first, last, spacing); }

        @Override public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
            return super.write(dimensions, registries);
        }
    }
}
