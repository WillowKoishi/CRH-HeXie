package willow.train.hexie.interaction.surface;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesRayView;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.interaction.spatial.VoxelShell;
import willow.train.hexie.interaction.spatial.model.*;

/**
 * Supplies original local meshes for world cells and captured cells. Addons can declare the
 * visible mesh and a cut proxy independently of the block's selection/collision shape.
 */
public final class BlockSurfaceSource implements SurfaceSource {
    private static final SurfaceMesh EMPTY = new SurfaceMesh(List.of());

    @FunctionalInterface
    public interface Provider {
        SurfaceGeometry geometry(SurfaceHostId host, BlockState state, BlockGetter view, BlockPos cell);
    }
    private static final Map<Block, Provider> providers = new IdentityHashMap<>();
    public static void register(Block block, Provider provider) {
        providers.put(block, provider);
    }

    public static void register(Block block, SurfaceSource source) {
        register(block, (host, state, view, pos) -> source.geometry(host).orElseGet(() -> new SurfaceGeometry(EMPTY)));
    }

    private final Function<SurfaceHostId, BlockGetter> views;

    /** Resolve both level and captured coordinates through the caller's storage view. */
    public BlockSurfaceSource(Function<SurfaceHostId, BlockGetter> views) {
        this.views = views;
    }

    @Override
    public Optional<FaceGeometry> face(SurfaceHostId host, Face side) {
        return geometry(host).flatMap(g -> g.face(side));
    }

    @Override
    public Optional<SurfaceGeometry> geometry(SurfaceHostId host) {
        return geometry(host, views.apply(host));
    }

    private static Optional<SurfaceGeometry> geometry(SurfaceHostId host, BlockGetter view) {
        if (view == null) return Optional.empty();
        var c = host.cell();
        var pos = new BlockPos(c.x(), c.y(), c.z());
        var state = view.getBlockState(pos);
        if (state.isAir()) return Optional.empty();
        var custom = providers.get(state.getBlock());
        if (custom != null) return Optional.of(custom.geometry(host, state, view, pos));
        var outline = state.getShape(view, pos);
        var visible = VoxelShell.of(outline.toAabbs()
                        .stream()
                        .map(SurfaceRay::box)
                        .toList());
        var collision = state.getCollisionShape(view, pos);
        var occlusion = state.getOcclusionShape(view, pos);
        // Only use a platform-declared volume. A picking box alone also surrounds zero-thickness models.
        boolean cube = Block.isShapeFullBlock(outline) && Block.isShapeFullBlock(collision);
        boolean closedVolume = state.canOcclude() && !collision.isEmpty() && !occlusion.isEmpty()
                && !net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                        outline, occlusion, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME);
        return Optional.of(cube || closedVolume
                        ? new SurfaceGeometry(visible)
                        : new SurfaceGeometry(visible, Optional.of(EMPTY)));
    }

    public static BlockHitResult pick(BlockControlContext target, SurfaceInputSourcesRayView localRay) {
        var pos = target.position();
        var cell = new Cell(pos.getX(), pos.getY(), pos.getZ());
        SurfaceHostId host;
        if (target.carrier() != null) host = new SurfaceHostId.Carriage(target.carrier().getUUID(), cell);
        else if (target.blocks() instanceof Level level) {
            var dimension = level.dimension().location();
            host = new SurfaceHostId.World(new Id(dimension.getNamespace(), dimension.getPath()), cell);
        } else return null;

        var geometry = geometry(host, target.blocks());
        if (geometry.isEmpty()) return null;
        Vec3 delta = localRay.to().subtract(localRay.from());
        double reach = delta.length();
        if (!(reach > 0) || !Double.isFinite(reach)) return null;
        Vec3 offset = Vec3.atLowerCornerOf(pos);
        var ray = new SurfaceRay(SurfaceRay.vec(localRay.from().subtract(offset)), SurfaceRay.vec(delta.scale(1 / reach)));
        BlockHitResult best = null;
        for (var triangle : geometry.get().visible().triangles()) {
            var hit = ray.hit(triangle.a(), triangle.b(), triangle.c(), reach);
            if (hit == null || best != null && hit.distance() >= reach) continue;
            reach = hit.distance();
            var normal = triangle.normal();
            var point = offset.add(SurfaceRay.mc(triangle.at(hit.b(), hit.c())));
            best = new BlockHitResult(point, Direction.getNearest(normal.x(), normal.y(), normal.z()), pos, false);
        }
        return best;
    }
}
