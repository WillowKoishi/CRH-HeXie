package willow.train.hexie.client.render.bogey;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.bogeys.df11g.DF11GBogeyBlockEntity;
import willow.train.hexie.datafix.Constants;
import willow.train.hexie.init.track.TrackBlockInit;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Supplies a local rendering direction without changing blocks or sending block updates. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class QJBogeyStationPreview {
    private static final Map<StationBlockEntity, Boolean> STATIONS = new WeakHashMap<>();
    private static WeakReference<Level> trackedLevel = new WeakReference<>(null);
    private static long nextUpdate = Long.MIN_VALUE;

    private QJBogeyStationPreview() {}

    @SubscribeEvent
    public static void onBlockEntityBehaviours(BlockEntityBehaviourEvent event) {
        event.forType(AllBlockEntityTypes.TRACK_STATION.get(),
                station -> event.attach(new StationPreviewBehaviour(station)));
    }

    public static void onStationTick(StationBlockEntity station) {
        Level level = station.getLevel();
        if (level == null || !level.isClientSide)
            return;

        if (trackedLevel.get() != level) {
            STATIONS.clear();
            trackedLevel = new WeakReference<>(level);
            nextUpdate = Long.MIN_VALUE;
        }

        STATIONS.put(station, Boolean.TRUE);
        if (level.getGameTime() < nextUpdate)
            return;
        nextUpdate = level.getGameTime() + 5;

        STATIONS.keySet().removeIf(candidate -> candidate.isRemoved()
                || candidate.getLevel() != level
                || !level.hasChunkAt(candidate.getBlockPos())
                || level.getBlockEntity(candidate.getBlockPos()) != candidate);
        updatePreviews(STATIONS.keySet());
    }

    static void updatePreviews(Iterable<StationBlockEntity> stations) {
        Map<DF11GBogeyBlockEntity, Preview> previews = new IdentityHashMap<>(4);
        for (StationBlockEntity station : stations)
            collectPreviews(station, previews);

        previews.forEach((bogey, preview) -> {
            var data = bogey.getBogeyData();
            if (!preview.facing().name().equals(data.getString(Constants.QJ_PREVIEW_DIRECTION_KEY)))
                NBTHelper.writeEnum(data, Constants.QJ_PREVIEW_DIRECTION_KEY, preview.facing());
        });
    }

    private static void collectPreviews(StationBlockEntity station,
            Map<DF11GBogeyBlockEntity, Preview> previews) {
        Level level = station.getLevel();
        if (level == null || station.isRemoved()
                || !station.getBlockState().getOptionalValue(StationBlock.ASSEMBLING).orElse(false)
                || station.edgePoint.getTargetBezier() != null)
            return;

        BlockPos target = station.edgePoint.getGlobalPosition();
        if (!level.hasChunkAt(target))
            return;
        BlockState trackState = level.getBlockState(target);
        if (!(trackState.getBlock() instanceof ITrackBlock track))
            return;
        var axes = track.getTrackAxes(level, target, trackState);
        if (axes.size() != 1)
            return;
        Vec3 axis = axes.getFirst();
        if (axis.y != 0 || (axis.x != 0 && axis.z != 0))
            return;

        int step = station.edgePoint.getTargetDirection().getStep();
        Direction assemblyDirection = Direction.getNearest(axis.x * step, 0, axis.z * step);
        Direction facing = assemblyDirection.getOpposite();
        BlockPos up = BlockPos.containing(track.getUpNormal(level, target, trackState));
        int maxLength = AllConfigs.server().trains.maxAssemblyLength.get();
        BlockPos.MutableBlockPos railPos = target.mutable();
        BlockPos.MutableBlockPos bogeyPos = new BlockPos.MutableBlockPos();
        LevelChunk chunk = null;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ = Integer.MIN_VALUE;

        // Keep the same live-track checks, reusing coordinates and the current loaded chunk.
        for (int offset = 1; offset < maxLength; offset++) {
            railPos.move(assemblyDirection);
            int nextChunkX = railPos.getX() >> 4;
            int nextChunkZ = railPos.getZ() >> 4;
            if (chunk == null || nextChunkX != chunkX || nextChunkZ != chunkZ) {
                var access = level.getChunk(nextChunkX, nextChunkZ, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk loaded))
                    break;
                chunk = loaded;
                chunkX = nextChunkX;
                chunkZ = nextChunkZ;
            }
            if (!track.trackEquals(trackState, chunk.getBlockState(railPos)))
                break;

            bogeyPos.setWithOffset(railPos, up);
            boolean sameChunk = (bogeyPos.getX() >> 4) == chunkX && (bogeyPos.getZ() >> 4) == chunkZ;
            BlockState state = sameChunk ? chunk.getBlockState(bogeyPos) : level.getBlockState(bogeyPos);
            if (!(TrackBlockInit.QJ_BOGEY.has(state) || TrackBlockInit.QJ_BACKWARD_BOGEY.has(state))
                    || state.getValue(AbstractBogeyBlock.AXIS) != assemblyDirection.getAxis()
                    || !(level.getBlockEntity(bogeyPos) instanceof DF11GBogeyBlockEntity bogey)
                    || bogey.isRemoved())
                continue;

            Preview previous = previews.get(bogey);
            if (previous == null || offset < previous.distance()
                    || (offset == previous.distance()
                    && station.getBlockPos().compareTo(previous.station()) < 0))
                previews.put(bogey, new Preview(facing, offset, station.getBlockPos()));
        }
    }

    private static final class StationPreviewBehaviour extends BlockEntityBehaviour {
        private static final BehaviourType<StationPreviewBehaviour> TYPE =
                new BehaviourType<>("hexie_qj_station_preview");

        private StationPreviewBehaviour(StationBlockEntity station) {
            super(station);
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        @Override
        public void tick() {
            onStationTick((StationBlockEntity) blockEntity);
        }
    }

    private record Preview(Direction facing, int distance, BlockPos station) {}
}
