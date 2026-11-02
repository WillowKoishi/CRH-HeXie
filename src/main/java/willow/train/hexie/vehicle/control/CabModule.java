package willow.train.hexie.vehicle.control;

import net.minecraft.nbt.CompoundTag;

/** Vehicle-owned cab state. Input surfaces supply values; occupancy never owns these values. */
public final class CabModule {
    public double power, serviceBrake;
    public int reverser;
    public boolean automatic, parking, emergencyHandle, releaseButton;
    /** Momentary operation, not a saved handle position. */
    public boolean approachHeld;
    public final CabController controller = new CabController();

    public CabController.Mode mode() { return automatic ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL; }
    public CabController.Input input(int direction) {
        return new CabController.Input(power, serviceBrake, reverser * direction, emergencyHandle, parking, releaseButton);
    }
    /** Consume a release request once; holding or reloading it must not release a later emergency. */
    public willow.train.hexie.vehicle.runtime.VehicleRuntime.Control update(CabController.Mode mode, int direction,
            double speed, AutomaticTrainControl.Target target, double serviceDeceleration) {
        try {
            return controller.update(mode, input(direction), speed, target, serviceDeceleration);
        } finally {
            releaseButton = false;
        }
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putInt("CabVersion", 1);
        tag.putDouble("Power", power); tag.putDouble("ServiceBrake", serviceBrake); tag.putInt("Reverser", reverser);
        tag.putBoolean("Automatic", automatic); tag.putBoolean("Parking", parking);
        tag.putBoolean("EmergencyHandle", emergencyHandle);
        tag.putBoolean("Emergency", controller.emergencyLatched());
        return tag;
    }
    public static CabModule read(CompoundTag tag) {
        boolean legacy = !tag.contains("CabVersion");
        var state = new CabModule();
        state.power = legacy ? tag.getInt("Power") / 10d : tag.getDouble("Power");
        state.serviceBrake = legacy ? Math.min(10,tag.getInt("Brake")) / 10d : tag.getDouble("ServiceBrake");
        state.emergencyHandle = legacy ? tag.getInt("Brake") == 11 : tag.getBoolean("EmergencyHandle");
        state.reverser = tag.getInt("Reverser"); state.automatic = tag.getBoolean("Automatic");
        state.parking = tag.getBoolean("Parking");
        state.input(1); // Validate normalized inputs before installing saved module state.
        if (tag.getBoolean("Emergency")) state.controller.latchEmergency();
        return state;
    }
}
