package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.WorldBlockBridge.BallastDataView;

/** Collects the tracks created by one item use and applies the saved ballast after placement commits. */
public final class StandardTrackBallastPlacement {
    private static final ThreadLocal<Placement> ACTIVE = new ThreadLocal<>();

    private StandardTrackBallastPlacement() {}

    public static Placement begin(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !StandardTrackPavingPreset.INSTANCE.isStandardTrack(context.getItemInHand()))
            return null;

        BlockState material = StandardTrackPavingPreset.getBlock(context.getItemInHand()).defaultBlockState();
        if (material.is(Blocks.COBBLESTONE))
            return null;

        BlockPos directPos = new BlockPlaceContext(context).getClickedPos().immutable();
        Placement placement = new Placement(level, material, ACTIVE.get(),
                level.capturedBlockSnapshots.size(), directPos, level.getBlockState(directPos));
        ACTIVE.set(placement);
        return placement;
    }

    /** Ignore unrelated Create connection updates before inspecting the native curve. */
    public static void connectionAdded(TrackBlockEntity track, BezierConnection connection) {
        Placement placement = ACTIVE.get();
        if (placement != null && placement.curve == null && track.getLevel() == placement.level
                && connection.isPrimary() && connection.getMaterial().id.equals(BuiltinRailways.STANDARD_ID)) {
            placement.curve = new Curve(track, connection.getKey().immutable());
        }
    }

    public static void end(Placement placement, InteractionResult result) {
        if (placement == null)
            return;
        if (placement.previous == null)
            ACTIVE.remove();
        else
            ACTIVE.set(placement.previous);

        if (result == null || !result.consumesAction())
            return;

        var snapshots = placement.level.capturedBlockSnapshots;
        for (int i = placement.snapshotStart; i < snapshots.size(); i++) {
            var snapshot = snapshots.get(i);
            placement.originalStates.putIfAbsent(snapshot.getPos().immutable(), snapshot.getState());
        }
        if (!placement.level.captureBlockSnapshots)
            placement.originalStates.putIfAbsent(placement.directPos, placement.originalDirectState);

        var server = placement.level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> apply(placement)));
    }

    private static void apply(Placement placement) {
        var level = placement.level;
        boolean changed = placement.originalStates.isEmpty() || placement.originalStates.entrySet().stream()
                .anyMatch(entry -> !level.getBlockState(entry.getKey()).equals(entry.getValue()));
        if (!changed)
            return;

        for (var entry : placement.originalStates.entrySet()) {
            if (entry.getValue().getBlock() instanceof ITrackBlock)
                continue;
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof TrackBlock block) || !block.getMaterial().id.equals(BuiltinRailways.STANDARD_ID)) continue;
            var withEntity = state.setValue(TrackBlock.HAS_BE, true);
            if (withEntity != state) level.setBlockAndUpdate(pos, withEntity);
            if (level.getBlockEntity(pos) instanceof TrackBlockEntity track && track instanceof BallastDataView ballast && !ballast.hexie$getBallast(null).equals(placement.material))
                TrackBallast.paint(track, null, placement.material);
        }

        Curve curve = placement.curve;
        if (curve != null && level.getBlockEntity(curve.track.getBlockPos()) == curve.track
                && curve.track.getConnections().containsKey(curve.target))
            TrackBallast.paint(curve.track, curve.target, placement.material);
    }

    private record Curve(TrackBlockEntity track, BlockPos target) {}

    public static final class Placement {
        private final ServerLevel level;
        private final BlockState material;
        private final Placement previous;
        private final int snapshotStart;
        private final BlockPos directPos;
        private final BlockState originalDirectState;
        private final Map<BlockPos, BlockState> originalStates = new LinkedHashMap<>();
        private Curve curve;

        private Placement(ServerLevel level, BlockState material, Placement previous, int snapshotStart,
                          BlockPos directPos, BlockState originalDirectState) {
            this.level = level;
            this.material = material;
            this.previous = previous;
            this.snapshotStart = snapshotStart;
            this.directPos = directPos;
            this.originalDirectState = originalDirectState;
        }
    }
}
