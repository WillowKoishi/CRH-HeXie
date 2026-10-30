package willow.train.hexie.blocks.bogeys;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.init.track.TrackMaterialInit;

/** Floor height in tenths of a block, persisted in the bogey's existing synchronized data. */
public final class BogeyFloorData {
    public static final String KEY = "HexieFloorHeight";
    public static final int MIN_HEIGHT = 10, DEFAULT_HEIGHT = 20, MAX_HEIGHT = 30;
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

    public static boolean valid(int height) { return height >= MIN_HEIGHT && height <= MAX_HEIGHT; }

    public static double offset(int height) {
        requireHeight(height);
        return height == 24 ? TrackMaterialInit.RAIL_HEIGHT_INCREASE : (height - DEFAULT_HEIGHT) / 10.0;
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
