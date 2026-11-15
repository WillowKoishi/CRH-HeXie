package willow.train.hexie.vehicle.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Coupling types can opt into direct mating without adapter-based substitution. */
public final class CouplingRules {
    private static final Set<String> DIRECT_ONLY = ConcurrentHashMap.newKeySet();
    private CouplingRules() {}
    public static void directOnly(String model) {
        CouplerAdapter.requireIdentifier(model, "coupling model");
        DIRECT_ONLY.add(model);
    }
    public static boolean compatible(CouplingEndpoint first, CouplingEndpoint second) {
        if (DIRECT_ONLY.contains(first.model()) || DIRECT_ONLY.contains(second.model())
                || DIRECT_ONLY.contains(first.effectiveModel()) || DIRECT_ONLY.contains(second.effectiveModel()))
            return first.adapter() == null && second.adapter() == null && first.model().equals(second.model());
        return first.effectiveModel().equals(second.effectiveModel());
    }
}
