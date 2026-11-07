package willow.train.hexie.api.display;

import net.minecraft.nbt.CompoundTag;

/** A manually entered display value; leading zeroes are meaningful. */
public final class PassengerCarriageNumber {
    public static final String TAG = "PassengerCarriageNumber";
    public static final int MAX_DIGITS = 2;
    private PassengerCarriageNumber() {}
    public static boolean valid(String value) {
        return value != null && value.length() <= MAX_DIGITS
                && value.chars().allMatch(c -> c >= '0' && c <= '9');
    }
    public static String clean(String value) { return valid(value) ? value : ""; }
    public static String read(CompoundTag tag) { return tag == null ? "" : clean(tag.getString(TAG)); }
    public static void write(CompoundTag tag, String value) {
        String number = clean(value);
        if (number.isEmpty()) tag.remove(TAG);
        else tag.putString(TAG, number);
    }
}
