package willow.train.hexie.vehicle.control;

import net.minecraft.nbt.CompoundTag;

/** Temporary stepped controls. Brake notch 11 is emergency; returning to 10 is the release operation. */
public final class TestCabControls {
    public int power, brake, reverser;
    public boolean automatic, parking;
    public final CabController controller = new CabController(CabController.EmergencyRelease.HANDLE_RETURN);

    public void action(int action) {
        switch (action) {
            case 0 -> power = Math.min(10, power + 1);
            case 1 -> power = Math.max(0, power - 1);
            case 2 -> brake = Math.min(11, brake + 1);
            case 3 -> brake = Math.max(0, brake - 1);
            case 4 -> reverser = reverser == 1 ? -1 : reverser + 1;
            case 5 -> automatic = !automatic;
            case 6 -> parking = !parking;
            default -> { }
        }
    }
    public CabController.Input input(int cabDirection) {
        return new CabController.Input(power / 10d, Math.min(10, brake) / 10d, reverser * cabDirection,
                brake == 11, parking, false);
    }
    public CabController.Mode mode() { return automatic ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL; }
    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putInt("Power", power); tag.putInt("Brake", brake); tag.putInt("Reverser", reverser);
        tag.putBoolean("Automatic", automatic); tag.putBoolean("Parking", parking);
        tag.putBoolean("Emergency", controller.emergencyLatched());
        return tag;
    }
    public static TestCabControls read(CompoundTag tag) {
        var state = new TestCabControls();
        state.power = Math.clamp(tag.getInt("Power"), 0, 10);
        state.brake = Math.clamp(tag.getInt("Brake"), 0, 11);
        state.reverser = Math.clamp(tag.getInt("Reverser"), -1, 1);
        state.automatic = tag.getBoolean("Automatic"); state.parking = tag.getBoolean("Parking");
        if (tag.getBoolean("Emergency")) state.controller.latchEmergency();
        return state;
    }
}
