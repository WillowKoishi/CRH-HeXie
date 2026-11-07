package willow.train.hexie.api.display;

import net.minecraft.nbt.CompoundTag;

public final class PassengerDisplayStateNbt {
    private PassengerDisplayStateNbt() {}

    public static CompoundTag write(PassengerDisplayState state) {
        var tag = new CompoundTag();
        tag.putString("TrainNumber", state.trainNumber());
        tag.putString("Origin", state.origin());
        tag.putString("Destination", state.destination());
        tag.putString("NextStop", state.nextStop());
        return tag;
    }

    public static PassengerDisplayState read(CompoundTag tag) {
        return new PassengerDisplayState(tag.getString("TrainNumber"), tag.getString("Origin"),
                tag.getString("Destination"), tag.getString("NextStop"));
    }
}
