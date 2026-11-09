package willow.train.hexie.interaction;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.api.interaction.BlockControlContext;

/** Loaded target access and logical coordinates shared by input transport and picking. */
public final class ControlInputTargets {
    private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final List<Coordinates> COORDINATES = new CopyOnWriteArrayList<>();
    private static final Coordinates WORLD = new Coordinates() {};

    private ControlInputTargets() {}

    /** A carrier adapter owns its target membership, storage frame and interaction permission. */
    public interface Adapter {
        boolean supports(Entity carrier);
        BlockControlContext world(Entity carrier, BlockPos position, float partialTick);
        /** Return the current identity owner without loading terrain or recreating a container. */
        Object container(BlockControlContext target);
        /** Read an existing BE only; a missing BE must not be constructed by this query. */
        BlockEntity blockEntity(BlockControlContext target);
        Vec3 toStorage(BlockControlContext target, Vec3 local);
        Vec3 toLocal(BlockControlContext target, Vec3 storage);
        /** Backend permissions only; surface interaction distance is checked on the client. */
        boolean canInteract(BlockControlContext target, Player player, Vec3 localPoint);
        /** Called before a seat provider is allowed to inspect neighbouring blocks. */
        boolean seatDiscoveryLoaded(Player player, Entity vehicle, BlockPos console);
    }

    /** A level-coordinate adapter composes outside the carrier's local/storage transform. */
    public interface Coordinates {
        default boolean supports(BlockControlContext target, Player player) { return true; }
        default Vec3 toWorld(BlockControlContext target, Player player, Vec3 storage) { return storage; }
        default Vec3 toStorage(BlockControlContext target, Player player, Vec3 world) { return world; }
        default Vec3 eye(BlockControlContext target, Player player) { return player.getEyePosition(); }
        /** A level clip can hit a different coordinate owner from the control target. */
        default boolean supportsBlock(Level level, BlockPos owner) { return false; }
        default Vec3 blockToWorld(Level level, BlockPos owner, Vec3 storage) { return storage; }
    }

    /** Register during common setup. The first supporting adapter owns the query. */
    public static void registerAdapter(Adapter adapter) { ADAPTERS.add(Objects.requireNonNull(adapter)); }
    public static void registerCoordinates(Coordinates coordinates) { COORDINATES.add(Objects.requireNonNull(coordinates)); }

    public static BlockControlContext seated(DrivingConsoleContext cab, float partialTick) {
        return cab.seatAccess().controlTarget(cab.console(), partialTick);
    }

    public static BlockControlContext world(Player player, int vehicleId, BlockPos position, float partialTick) {
        if (vehicleId < 0) return loadedChunk(player.level(), position) != null
                ? new BlockControlContext(player.level(), position, null, partialTick) : null;
        var carrier = player.level().getEntity(vehicleId);
        if (carrier == null || carrier.isRemoved()) return null;
        var adapter = adapter(carrier);
        return adapter == null ? null : adapter.world(carrier, position, partialTick);
    }

    public static Vec3 toWorld(BlockControlContext target, Player player, Vec3 local) {
        var adapter = adapter(target.carrier());
        Vec3 storage = adapter == null ? local : adapter.toStorage(target, local);
        return coordinates(target, player).toWorld(target, player, storage);
    }

    public static Vec3 toLocal(BlockControlContext target, Player player, Vec3 world) {
        Vec3 storage = coordinates(target, player).toStorage(target, player, world);
        var adapter = adapter(target.carrier());
        return adapter == null ? storage : adapter.toLocal(target, storage);
    }

    public static Vec3 eye(BlockControlContext target, Player player) {
        return coordinates(target, player).eye(target, player);
    }

    /** Convert a level clip's hit using its own block owner, independently of any control target. */
    public static Vec3 blockToWorld(Level level, BlockPos owner, Vec3 storage) {
        for (var coordinates : COORDINATES)
            if (coordinates.supportsBlock(level, owner)) return coordinates.blockToWorld(level, owner, storage);
        return storage;
    }

    /** An unloaded chunk, removed carrier or replaced carrier world has no current owner. */
    public static Object container(BlockControlContext target) {
        if (target.carrier() == null)
            return target.blocks() instanceof Level level ? loadedChunk(level, target.position()) : null;
        var adapter = adapter(target.carrier());
        return adapter == null ? null : adapter.container(target);
    }

    public static BlockEntity blockEntity(BlockControlContext target) {
        var owner = container(target);
        if (owner == null) return null;
        if (target.carrier() == null)
            return ((LevelChunk) owner).getBlockEntity(target.position(), LevelChunk.EntityCreationType.CHECK);
        return adapter(target.carrier()).blockEntity(target);
    }

    public static boolean canInteract(BlockControlContext target, Player player, Vec3 localPoint) {
        if (target.carrier() == null) return container(target) != null;
        var adapter = adapter(target.carrier());
        return adapter != null && adapter.container(target) != null && adapter.canInteract(target, player, localPoint);
    }

    public static boolean seatDiscoveryLoaded(Player player, Entity vehicle, BlockPos console) {
        if (vehicle == null || vehicle.isRemoved()) return false;
        var adapter = adapter(vehicle);
        if (adapter != null) return adapter.seatDiscoveryLoaded(player, vehicle, console);
        if (loadedChunk(player.level(), console) == null) return false;
        for (Direction facing : Direction.Plane.HORIZONTAL)
            if (loadedChunk(player.level(), vehicle.blockPosition().relative(facing)) == null) return false;
        return true;
    }

    private static LevelChunk loadedChunk(Level level, BlockPos position) {
        int x = position.getX() >> 4, z = position.getZ() >> 4;
        if (level instanceof ServerLevel server) return server.getChunkSource().getChunkNow(x, z);
        return level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk ? chunk : null;
    }

    private static Adapter adapter(Entity carrier) {
        if (carrier != null) for (var adapter : ADAPTERS) if (adapter.supports(carrier)) return adapter;
        return null;
    }

    private static Coordinates coordinates(BlockControlContext target, Player player) {
        for (var coordinates : COORDINATES) if (coordinates.supports(target, player)) return coordinates;
        return WORLD;
    }
}
