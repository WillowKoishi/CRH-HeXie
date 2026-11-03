package willow.train.hexie.api.cab;

import java.util.function.Supplier;
import willow.train.hexie.vehicle.control.CabModule;

/** A model's read-only handle/status binding; the host cannot mutate the runtime's module. */
public interface CabControlsHost {
    void controlsSource(Supplier<CabModule.State> source);
    CabModule.State controls();
}
