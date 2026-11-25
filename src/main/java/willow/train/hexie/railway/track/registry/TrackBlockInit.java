package willow.train.hexie.railway.track.registry;

import net.neoforged.bus.api.IEventBus;

public final class TrackBlockInit {
    private TrackBlockInit() {}

    public static void register(IEventBus bus) {
        BogeyBlockRegistration.register(bus);
        LocomotiveFrontBlockRegistration.register(bus);
    }
}
