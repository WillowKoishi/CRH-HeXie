package willow.train.hexie.vehicle.control;

import net.minecraft.nbt.CompoundTag;

/** Temporary stepped controls. Brake notch 11 is emergency; returning to 10 is the release operation. */
public final class TestCabControls {
    public int power, brake, reverser;
    public boolean automatic, parking;
    public final CabController controller = new CabController();

    /** Only the test surface uses ten notches and these numeric button IDs. */
    public static void apply(CabModule module, int action, double speed) {
        boolean wasEmergency = module.emergencyHandle;
        var view = read(module.write());
        view.action(action);
        module.power = view.power / 10d; module.serviceBrake = Math.min(10, view.brake) / 10d;
        module.emergencyHandle = view.brake == 11; module.reverser = view.reverser;
        module.automatic = view.automatic; module.parking = view.parking;
        // This fixture releases only on a fresh return from emergency, at standstill,
        // with power and reverser reset. Other cab adapters define their own policy.
        if (wasEmergency && !module.emergencyHandle && Math.abs(speed) < 1e-4
                && module.power == 0 && module.reverser == 0)
            module.controller.releaseEmergency();
    }
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
        if (tag.contains("CabVersion")) {
            var module = CabModule.read(tag);
            state.power = (int)Math.round(module.power * 10);
            state.brake = module.emergencyHandle ? 11 : (int)Math.round(module.serviceBrake * 10);
            state.reverser = module.reverser; state.automatic = module.automatic; state.parking = module.parking;
            if (module.controller.emergencyLatched()) state.controller.latchEmergency();
            return state;
        }
        state.power = Math.clamp(tag.getInt("Power"), 0, 10);
        state.brake = Math.clamp(tag.getInt("Brake"), 0, 11);
        state.reverser = Math.clamp(tag.getInt("Reverser"), -1, 1);
        state.automatic = tag.getBoolean("Automatic"); state.parking = tag.getBoolean("Parking");
        if (tag.getBoolean("Emergency")) state.controller.latchEmergency();
        return state;
    }
}
