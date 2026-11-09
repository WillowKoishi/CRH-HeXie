package willow.train.hexie.blocks.emu.head;

import java.util.Map;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.track.TrackBlockInit;

/** The head owns its DT206 support; the occupied world cell is always a head floor. */
public final class HeadBogey {
    public static final String ENABLED = "HeadBogeyEnabled";
    public static final String DATA = "BogeyData";

    private HeadBogey() {}

    /** Logical bogey state for assembly and rendering, never a world placement. */
    public static BlockState state(Direction facing) {
        return TrackBlockInit.DT206_BOGEY.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, facing.getAxis())
                .setValue(HexieBogeyBlock.HEAD_MOUNTED, true);
    }

    public static BlockPos mount(BlockPos root, Direction facing) {
        return root.offset(HeadGeometry.offset(HeadGeometry.BOGEY_PART, facing));
    }

    public static CompoundTag defaultData(Direction facing) {
        var data = new CompoundTag();
        data.putString("BogeyStyle", TrackBlockInit.DT206_BOGEY.get().getDefaultStyle().id.toString());
        data.putBoolean("UpsideDown", false);
        data.putInt(BogeyFloorData.LONGITUDINAL_KEY, 3 * facing.getAxisDirection().getStep());
        return data;
    }

    /** Preserve all existing Create/addon data, supplying only absent or invalid essentials. */
    public static CompoundTag normalizedData(CompoundTag saved, Direction facing) {
        var data = saved.copy();
        var defaults = defaultData(facing);
        if (!data.contains("BogeyStyle", Tag.TAG_STRING)) data.put("BogeyStyle", defaults.get("BogeyStyle"));
        if (!data.contains("UpsideDown", Tag.TAG_ANY_NUMERIC)) data.putBoolean("UpsideDown", false);
        if (!data.contains(BogeyFloorData.LONGITUDINAL_KEY, Tag.TAG_ANY_NUMERIC)
                || !BogeyFloorData.validLongitudinal(data.getInt(BogeyFloorData.LONGITUDINAL_KEY)))
            data.putInt(BogeyFloorData.LONGITUDINAL_KEY, defaults.getInt(BogeyFloorData.LONGITUDINAL_KEY));
        return data;
    }

    public static boolean trackEnables(Level level, BlockPos root, Direction facing) {
        var trackPos = mount(root, facing).below();
        if (!level.hasChunkAt(trackPos)) return false;
        var rail = level.getBlockState(trackPos);
        if (!(rail.getBlock() instanceof ITrackBlock track)) return false;
        var normal = track.getUpNormal(level, trackPos, rail);
        var axes = track.getTrackAxes(level, trackPos, rail);
        if (Math.abs(normal.y - 1) > 1e-6 || axes.size() != 1) return false;
        var axis = axes.getFirst();
        return Math.abs(axis.y) <= 1e-6
                && (facing.getAxis() == Direction.Axis.X
                    ? Math.abs(axis.z) <= 1e-6 && Math.abs(axis.x) > 1e-6
                    : Math.abs(axis.x) <= 1e-6 && Math.abs(axis.z) > 1e-6);
    }

    /** Only placed server roots inspect rails. Captured/moving block entities retain their saved state. */
    public static void refresh(Level level, BlockPos root) {
        refresh(level, root, true);
    }

    /** Item placement and printing initialize at their destination, including an existing moving plot. */
    public static void placed(Level level, BlockPos root) {
        refresh(level, root, false);
    }

    private static void refresh(Level level, BlockPos root, boolean preserveMovingState) {
        if (!(level instanceof ServerLevel) || !level.hasChunkAt(root)
                || !(level.getBlockState(root).getBlock() instanceof CRH2HeadBlock block)
                || !(level.getBlockEntity(root) instanceof CRH2HeadEntity head) || head.isCarriageView()) return;
        if (preserveMovingState && block.preserveBogeyState(level, root)) {
            block.bogeyChanged(head);
            return;
        }
        var facing = level.getBlockState(root).getValue(CRH2HeadBlock.FACING);
        var mount = mount(root, facing);
        if (!level.hasChunkAt(mount)) return;
        migrateLegacy(level, root, head, facing);
        var floor = level.getBlockState(mount);
        boolean installed = floor.is(BlockInit.CRH2A_HEAD_FLOOR.get())
                && floor.getValue(HeadFloorBlock.PART) == HeadGeometry.BOGEY_PART
                && floor.getValue(CRH2HeadBlock.FACING) == facing;
        head.updateBogey(installed && (!block.requiresTrackForBogey() || trackEnables(level, root, facing)), head.bogeyData());
        block.bogeyChanged(head);
    }

    /** Convert only a positively owned, old head support; unrelated bogeys remain untouched. */
    private static void migrateLegacy(Level level, BlockPos root, CRH2HeadEntity head, Direction facing) {
        var mount = mount(root, facing);
        var old = level.getBlockState(mount);
        if (!old.is(TrackBlockInit.DT206_BOGEY.get()) || !old.getValue(HexieBogeyBlock.HEAD_MOUNTED)
                || old.getValue(BlockStateProperties.HORIZONTAL_AXIS) != facing.getAxis()
                || !root.equals(HeadGeometry.owner(level, mount))
                || !(level.getBlockEntity(mount) instanceof HexieBogeyBlockEntity bogey)) return;
        var data = bogey.getBogeyData().copy();
        var rootState = level.getBlockState(root);
        var floor = BlockInit.CRH2A_HEAD_FLOOR.get().defaultBlockState()
                .setValue(CRH2HeadBlock.FACING, facing).setValue(HeadFloorBlock.PART, HeadGeometry.BOGEY_PART);
        // Include the root in the transaction so rollback also restores its previous block entity data.
        PartInstallation.replace(level, Map.of(root, rootState, mount, old), Map.of(root, rootState, mount, floor),
                at -> false, at -> true, () -> head.updateBogey(head.bogeyEnabled(), data));
    }
}
