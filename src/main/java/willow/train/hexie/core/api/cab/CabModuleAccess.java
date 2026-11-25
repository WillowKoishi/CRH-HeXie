package willow.train.hexie.core.api.cab;

import willow.train.hexie.core.ServiceSlot;
import willow.train.hexie.core.api.traincontrol.CabTrainInput;

public interface CabModuleAccess extends CabTrainInput {
    CabState state();
    void submit(CabState state);

    @FunctionalInterface
    interface Provider {
        CabModuleAccess createCabModule();
    }

    final class Factory {
        private static final ServiceSlot<Provider> MODULES = new ServiceSlot<>("cab modules");

        private Factory() {}
        public static void register(Provider factory) { MODULES.register(factory); }
        public static CabModuleAccess create() { return MODULES.get().createCabModule(); }
    }
}
