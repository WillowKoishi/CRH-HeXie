package willow.train.hexie.client.interaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHit;
import willow.train.hexie.api.interaction.WorldInteractionTargets;

/** Client input ports. Integrations contribute targets and rendered frames; selection stays shared. */
public final class SurfaceInputSources {
    public record Ray(Vec3 from, Vec3 to) {}
    public interface Frame {
        Vec3 toWorld(Vec3 point);
        Ray toLocal(Vec3 from, Vec3 to);
    }
    @FunctionalInterface public interface Source {
        void collect(Minecraft client, Ray ray, float partialTick, Query query);
    }
    public record Obstruction(int carrierId, BlockPos owner, Vec3 point) {
        public Obstruction { owner = owner.immutable(); }
        public boolean hides(BlockControlContext target, Vec3 origin, double distance) {
            int targetId = target.carrier() == null ? -1 : target.carrier().getId();
            return point.distanceTo(origin) + 1e-4 < distance
                    && (carrierId != targetId || !owner.equals(target.position()));
        }
    }
    public static final class Query {
        private final List<BlockControlContext> candidates = new ArrayList<>();
        private final List<Obstruction> obstructions = new ArrayList<>();
        private final Map<BlockControlContext, BlockHitResult> selected = new HashMap<>();
        public void add(BlockControlContext candidate) { candidates.add(candidate); }
        /** Reuse a native selection already made in this candidate's storage coordinates. */
        public void add(BlockControlContext candidate, BlockHitResult hit) {
            add(candidate);
            if (hit != null && hit.getBlockPos().equals(candidate.position()) && BlockControlHit.controlOf(hit) != null)
                selected.put(candidate, hit);
        }
        public BlockHitResult selected(BlockControlContext candidate) { return selected.get(candidate); }
        public void obstruct(int carrierId, BlockPos owner, Vec3 worldPoint) {
            obstructions.add(new Obstruction(carrierId, owner, worldPoint));
        }
        public List<BlockControlContext> candidates() { return List.copyOf(candidates); }
        public List<Obstruction> obstructions() { return List.copyOf(obstructions); }
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final List<Function<BlockControlContext, Frame>> FRAMES = new ArrayList<>();
    private static final List<BooleanSupplier> BLOCKERS = new ArrayList<>();
    private static final List<BiFunction<Player, Float, Vec3>> EYE_OFFSETS = new ArrayList<>();
    private static BiFunction<Player, Float, Vec3> eye = (player, partial) -> player.getEyePosition(partial);
    private static final Frame WORLD = new Frame() {
        @Override public Vec3 toWorld(Vec3 point) { return point; }
        @Override public Ray toLocal(Vec3 from, Vec3 to) { return new Ray(from, to); }
    };

    private SurfaceInputSources() {}
    /** Register on the client setup work queue, before entering a level. */
    public static void registerSource(Source source) { SOURCES.add(java.util.Objects.requireNonNull(source)); }
    /** Return null for a context this integration does not own. */
    public static void registerFrame(Function<BlockControlContext, Frame> provider) { FRAMES.add(java.util.Objects.requireNonNull(provider)); }
    public static void registerBlocker(BooleanSupplier blocker) { BLOCKERS.add(java.util.Objects.requireNonNull(blocker)); }
    public static void registerEye(BiFunction<Player, Float, Vec3> provider) { eye = java.util.Objects.requireNonNull(provider); }
    /** Add a world-space render displacement to the base player eye; return zero when not applicable. */
    public static void registerEyeOffset(BiFunction<Player, Float, Vec3> provider) {
        EYE_OFFSETS.add(java.util.Objects.requireNonNull(provider));
    }
    public static boolean blocked() { return BLOCKERS.stream().anyMatch(BooleanSupplier::getAsBoolean); }
    public static Vec3 eye(Player player, float partial) {
        Vec3 result = eye.apply(player, partial);
        for (var offset : EYE_OFFSETS) result = result.add(offset.apply(player, partial));
        return result;
    }
    public static Frame frame(BlockControlContext target) {
        for (var provider : FRAMES) {
            var frame = provider.apply(target);
            if (frame != null) return frame;
        }
        return WORLD;
    }
    public static float partialTick() {
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        return camera.getEntity() == null ? client.getTimer().getGameTimeDeltaPartialTick(true) : camera.getPartialTickTime();
    }
    public static Query collect(Minecraft client, Ray ray, float partial) {
        var query = new Query();
        var owners = cells(ray.from(), ray.to());
        BlockHitResult worldHit = null;
        if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            worldHit = hit;
            var owner = hit.getBlockPos();
            owners.add(owner);
            var context = new BlockControlContext(client.level, owner, null, partial);
            query.obstruct(-1, owner, frame(context).toWorld(hit.getLocation()));
        }
        for (var cell : Set.copyOf(owners)) owners.addAll(WorldInteractionTargets.lookup(client.level, cell));
        for (var owner : owners) if (client.level.hasChunkAt(owner))
            query.add(new BlockControlContext(client.level, owner, null, partial), worldHit);
        for (var source : SOURCES) source.collect(client, ray, partial, query);
        return query;
    }
    public static Set<BlockPos> cells(Vec3 from, Vec3 to) {
        Set<BlockPos> positions = new HashSet<>();
        BlockGetter.traverseBlocks(from, to, positions,
                (result, position) -> { result.add(position.immutable()); return null; }, result -> result);
        return positions;
    }
}
