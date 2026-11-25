package willow.train.hexie.world.blocks.bogeys;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;

/** Floor height in tenths of a block, persisted in the bogey's existing synchronized data. */
public final class BogeyFloorData {
    public static final String REVERSED = "HexieBogeyReversed";

    public static boolean reversedFromEntity(CompoundTag entityData) {
        return entityData != null && entityData.getCompound(BOGEY_DATA).getBoolean(REVERSED);
    }

    public static void writeReversed(CompoundTag entityData, boolean reversed) {
        var data = entityData.getCompound(BOGEY_DATA);
        data.putBoolean(REVERSED, reversed);
        entityData.put(BOGEY_DATA, data);
    }

    public static final String KEY = "HexieFloorHeight";
    public static final String LONGITUDINAL_KEY = "HexieBogeyLongitudinal";
    /** Integer slider units; physical displacement and UI positions use the same descriptor. */
    public record Setting(int minimum, int maximum, int unitsPerBlock, int majorTick) implements WorldBlockBridge.BogeyFloorDataSettingView {
        public Setting {
            if (minimum > maximum || unitsPerBlock <= 0 || majorTick <= 0)
                throw new IllegalArgumentException("Invalid bogey setting range or units");
        }
        public boolean valid(int value) { return value >= minimum && value <= maximum; }
        public int clamp(int value) { return Math.clamp(value, minimum, maximum); }
        public int range() { return maximum - minimum; }
        public int coordinate(int value) { return clamp(value) - minimum; }
        public int value(int coordinate) { return clamp(coordinate + minimum); }
        public double blocks(int value) { return value / (double) unitsPerBlock; }
    }
    public static final BogeyFloorData.Setting HEIGHT_SETTING = new BogeyFloorData.Setting(10, 30, 10, 5);
    public static final BogeyFloorData.Setting LONGITUDINAL_SETTING = new BogeyFloorData.Setting(-8, 8, 16, 4);
    public static boolean validLongitudinal(int value) { return LONGITUDINAL_SETTING.valid(value); }
    public static int longitudinal(CompoundTag data) {
        int value = data.getInt(LONGITUDINAL_KEY);
        return validLongitudinal(value) ? value : 0;
    }
    public static int longitudinalFromEntity(CompoundTag data) {
        return data == null ? 0 : longitudinal(data.getCompound(BOGEY_DATA));
    }
    public static Vec3 displacement(BlockState state, int value) {
        if (!validLongitudinal(value)) throw new IllegalArgumentException("Bogey offset must be -8..8");
        return state.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.Z) == Direction.Axis.X
                ? new Vec3(LONGITUDINAL_SETTING.blocks(value), 0, 0) : new Vec3(0, 0, LONGITUDINAL_SETTING.blocks(value));
    }
    public static void writeLongitudinal(CompoundTag entityData, int value) {
        if (!validLongitudinal(value)) throw new IllegalArgumentException("Bogey offset must be -8..8");
        var data = entityData.getCompound(BOGEY_DATA);
        data.putInt(LONGITUDINAL_KEY, value); entityData.put(BOGEY_DATA, data);
    }
    public static final int MIN_HEIGHT = HEIGHT_SETTING.minimum(), DEFAULT_HEIGHT = 20, MAX_HEIGHT = HEIGHT_SETTING.maximum();
    private static final String LEGACY_KEY = "HexieFloorMode";
    private static final String BOGEY_DATA = "BogeyData";

    private BogeyFloorData() {}

    public static int value(CompoundTag blockEntityData) {
        return blockEntityData == null ? DEFAULT_HEIGHT : heightFromBogeyData(blockEntityData.getCompound(BOGEY_DATA));
    }

    public static int heightFromBogeyData(CompoundTag bogeyData) {
        if (bogeyData.contains(KEY, Tag.TAG_ANY_NUMERIC)) {
            int height = bogeyData.getInt(KEY);
            return valid(height) ? height : DEFAULT_HEIGHT;
        }
        return switch (bogeyData.getInt(LEGACY_KEY)) {
            case 1 -> 24;
            case 2 -> 15;
            default -> DEFAULT_HEIGHT;
        };
    }

    public static boolean valid(int height) { return HEIGHT_SETTING.valid(height); }

    public static double offset(int height) {
        requireHeight(height);
        return height == 24 ? TrackRegistryBridge.RAIL_HEIGHT_INCREASE : HEIGHT_SETTING.blocks(height - DEFAULT_HEIGHT);
    }

    public static int heightFromOffset(double offset) {
        if (!Double.isFinite(offset)) throw new IllegalArgumentException("Non-finite floor offset");
        if (Math.abs(offset - TrackRegistryBridge.RAIL_HEIGHT_INCREASE) < 1e-6) return 24;
        return HEIGHT_SETTING.clamp((int) Math.round(DEFAULT_HEIGHT + offset * HEIGHT_SETTING.unitsPerBlock()));
    }

    public static void write(CompoundTag blockEntityData, int height) {
        requireHeight(height);
        CompoundTag bogeyData = blockEntityData.contains(BOGEY_DATA, Tag.TAG_COMPOUND)
                ? blockEntityData.getCompound(BOGEY_DATA) : new CompoundTag();
        writeHeight(bogeyData, height);
        blockEntityData.put(BOGEY_DATA, bogeyData);
    }

    private static void writeHeight(CompoundTag data, int height) {
        data.putInt(KEY, height);
        data.remove(LEGACY_KEY);
    }

    private static void requireHeight(int height) {
        if (!valid(height)) throw new IllegalArgumentException("Floor height must be between 10 and 30: " + height);
    }
}
