package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.world.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.world.blocks.bogeys.StandaloneHexieBogeyBlock;
import willow.train.hexie.world.placement.EmuPlacement;
import willow.train.hexie.world.registry.blocks.BlockInit;

/** The head owns its DT206 support; the occupied world cell is always a head floor. */
public final class HeadBogey {
    public static final String ENABLED = "HeadBogeyEnabled";
    public static final String DATA = "BogeyData";

    private HeadBogey() {}

    /** Logical bogey state for assembly and rendering, never a world placement. */
    public static BlockState state(Direction facing) {
        return HexieBridges.trackRegistry().trackBlockInit().dt206Bogey().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, facing.getAxis())
                .setValue(headMountedProperty(), true);
    }

    public static BooleanProperty headMountedProperty() {
        return ModList.get().isLoaded("create") ? HexieBridges.createTrain().headBogey().headMountedProperty()
                : StandaloneHexieBogeyBlock.HEAD_MOUNTED;
    }

    public static boolean isHeadMountedBogey(BlockState state) {
        if (state.getBlock() instanceof StandaloneHexieBogeyBlock)
            return state.getValue(StandaloneHexieBogeyBlock.HEAD_MOUNTED);
        return ModList.get().isLoaded("create") && HexieBridges.createTrain().headBogey().isHeadMountedBogey(state);
    }

    public static BlockPos mount(BlockPos root, Direction facing) {
        return root.offset(HeadGeometry.offset(HeadGeometry.BOGEY_PART, facing));
    }

    public static CompoundTag defaultData(Direction facing) {
        var data = new CompoundTag();
        data.putString("BogeyStyle", ModList.get().isLoaded("create") ? HexieBridges.createTrain().headBogey().defaultBogeyStyleId() : "hexie:crhbogey");
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
        return ModList.get().isLoaded("create") && HexieBridges.createTrain().headBogey().trackEnables(level, root, facing);
    }

    /** Only placed server roots inspect rails. Captured/moving block entities retain their saved state. */
    public static void refresh(Level level, BlockPos root) {
        refresh(level, root, true);
    }

    /** Item placement and printing initialize at their destination, including an existing moving plot. */
    public static void placed(Level level, BlockPos root) {
        EmuPlacement.ForHeadBogey.placed(level, root);
    }

    public static void refresh(Level level, BlockPos root, boolean preserveMovingState) {
        if (!(level instanceof ServerLevel) || !level.hasChunkAt(root)
                || !(level.getBlockState(root).getBlock() instanceof CRH2HeadBlock block)
                || !(level.getBlockEntity(root) instanceof CRH2HeadEntityView head) || head.isCarriageView()) return;
        if (preserveMovingState && block.preserveBogeyState(level, root)) {
            block.bogeyChanged(head);
            return;
        }
        var facing = level.getBlockState(root).getValue(CRH2HeadBlock.FACING);
        var mount = mount(root, facing);
        if (!level.hasChunkAt(mount)) return;
        if (ModList.get().isLoaded("create")) HexieBridges.createTrain().headBogey().migrateLegacy(level, root, head, facing);
        var floor = level.getBlockState(mount);
        boolean installed = floor.is(BlockInit.CRH2A_HEAD_FLOOR.get())
                && floor.getValue(HeadFloorBlock.PART) == HeadGeometry.BOGEY_PART
                && floor.getValue(CRH2HeadBlock.FACING) == facing;
        head.updateBogey(installed && (!block.requiresTrackForBogey() || trackEnables(level, root, facing)), head.bogeyData());
        block.bogeyChanged(head);
    }
}
