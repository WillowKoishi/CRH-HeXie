package willow.train.hexie.blocks.bogeys;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.init.track.TrackMaterialInit;

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
    public record Setting(int minimum, int maximum, int unitsPerBlock, int majorTick) {
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
    public static final Setting HEIGHT_SETTING = new Setting(10, 30, 10, 5);
    public static final Setting LONGITUDINAL_SETTING = new Setting(-8, 8, 16, 4);
    public static boolean validLongitudinal(int value) { return LONGITUDINAL_SETTING.valid(value); }
    public static int longitudinal(CompoundTag data) {
        int value = data.getInt(LONGITUDINAL_KEY);
        return validLongitudinal(value) ? value : 0;
    }
    public static int longitudinalFromEntity(CompoundTag data) {
        return data == null ? 0 : longitudinal(data.getCompound(BOGEY_DATA));
    }
    public static net.minecraft.world.phys.Vec3 displacement(net.minecraft.world.level.block.state.BlockState state, int value) {
        if (!validLongitudinal(value)) throw new IllegalArgumentException("Bogey offset must be -8..8");
        return state.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(net.minecraft.core.Direction.Axis.Z) == net.minecraft.core.Direction.Axis.X
                ? new net.minecraft.world.phys.Vec3(LONGITUDINAL_SETTING.blocks(value), 0, 0) : new net.minecraft.world.phys.Vec3(0, 0, LONGITUDINAL_SETTING.blocks(value));
    }
    public static void writeLongitudinal(CompoundTag entityData, int value) {
        if (!validLongitudinal(value)) throw new IllegalArgumentException("Bogey offset must be -8..8");
        var data = entityData.getCompound(BOGEY_DATA);
        data.putInt(LONGITUDINAL_KEY, value); entityData.put(BOGEY_DATA, data);
    }
    public static void setLongitudinal(HexieBogeyBlockEntity bogey, int value) {
        if (!validLongitudinal(value)) throw new IllegalArgumentException("Bogey offset must be -8..8");
        bogey.getBogeyData().putInt(LONGITUDINAL_KEY, value);
        bogey.setChanged();
        if (bogey.getLevel() != null && !bogey.getLevel().isClientSide) bogey.sendData();
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
        return height == 24 ? TrackMaterialInit.RAIL_HEIGHT_INCREASE : HEIGHT_SETTING.blocks(height - DEFAULT_HEIGHT);
    }

    public static int heightFromOffset(double offset) {
        if (!Double.isFinite(offset)) throw new IllegalArgumentException("Non-finite floor offset");
        if (Math.abs(offset - TrackMaterialInit.RAIL_HEIGHT_INCREASE) < 1e-6) return 24;
        return HEIGHT_SETTING.clamp((int) Math.round(DEFAULT_HEIGHT + offset * HEIGHT_SETTING.unitsPerBlock()));
    }

    public static void write(CompoundTag blockEntityData, int height) {
        requireHeight(height);
        CompoundTag bogeyData = blockEntityData.contains(BOGEY_DATA, Tag.TAG_COMPOUND)
                ? blockEntityData.getCompound(BOGEY_DATA) : new CompoundTag();
        writeHeight(bogeyData, height);
        blockEntityData.put(BOGEY_DATA, bogeyData);
    }

    public static int get(HexieBogeyBlockEntity bogey) {
        return heightFromBogeyData(bogey.getBogeyData());
    }

    public static void set(HexieBogeyBlockEntity bogey, int height) {
        requireHeight(height);
        writeHeight(bogey.getBogeyData(), height);
        bogey.setChanged();
        if (bogey.getLevel() != null && !bogey.getLevel().isClientSide) bogey.sendData();
    }

    private static void writeHeight(CompoundTag data, int height) {
        data.putInt(KEY, height);
        data.remove(LEGACY_KEY);
    }

    private static void requireHeight(int height) {
        if (!valid(height)) throw new IllegalArgumentException("Floor height must be between 10 and 30: " + height);
    }
}
