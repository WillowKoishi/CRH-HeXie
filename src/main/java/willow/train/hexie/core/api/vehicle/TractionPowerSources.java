package willow.train.hexie.core.api.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class TractionPowerSources {
    private static final List<Predicate<?>> SOURCES = new ArrayList<>();

    private TractionPowerSources() {}

    // register in common setup. for contact-only wires, touching the wire is enough
    public static <T> void register(Predicate<T> source) {
        SOURCES.add(Objects.requireNonNull(source));
    }

    // The active train backend supplies the same carriage type to registration and dispatch.
    @SuppressWarnings("unchecked")
    public static <T> boolean isPowered(T carriage) {
        for (var source : SOURCES) {
            if (((Predicate<T>) source).test(carriage)) return true;
        }
        return false;
    }
}
