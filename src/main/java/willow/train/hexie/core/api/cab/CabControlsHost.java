package willow.train.hexie.core.api.cab;

import java.util.function.Supplier;

/** A model's read-only handle/status binding; the host cannot mutate the runtime's module. */
public interface CabControlsHost {
    void controlsSource(Supplier<CabState> source);
    CabState controls();
}
