package willow.train.hexie.api.vehicle;

import com.simibubi.create.content.trains.entity.Carriage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class TractionPowerSources {
    private static final List<Predicate<Carriage>> SOURCES = new ArrayList<>();

    private TractionPowerSources() {}

    // register in common setup. for contact-only wires, touching the wire is enough
    public static void register(Predicate<Carriage> source) {
        SOURCES.add(Objects.requireNonNull(source));
    }

    public static boolean isPowered(Carriage carriage) {
        for (var source : SOURCES) {
            if (source.test(carriage)) return true;
        }
        return false;
    }
}
