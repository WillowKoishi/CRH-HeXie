package willow.train.hexie.bogey.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.api.bogey.client.BogeyAnimator;

/** Owns callback identity and model declarations independently of the rendering backend. */
public final class BogeyAnimations {
    public record Registration(Set<ResourceLocation> models, BogeyAnimator animator) {
        public Registration { models = Set.copyOf(models); Objects.requireNonNull(animator); }
    }

    private static final Map<ResourceLocation, Registration> REGISTRATIONS = new LinkedHashMap<>();
    private static boolean frozen;
    private BogeyAnimations() {}

    public static synchronized void register(ResourceLocation id, Set<ResourceLocation> models, BogeyAnimator callback) {
        Objects.requireNonNull(id);
        var registration = new Registration(models, callback);
        if (frozen) throw new IllegalStateException("Register bogey animations during client construction, before model baking");
        if (REGISTRATIONS.containsKey(id)) throw new IllegalArgumentException("Bogey animation already registered: " + id);
        Backend.INSTANCE.register(id, registration.models());
        REGISTRATIONS.put(id, registration);
    }

    public static synchronized Optional<Registration> find(ResourceLocation id) {
        return Optional.ofNullable(REGISTRATIONS.get(Objects.requireNonNull(id)));
    }

    public static synchronized void freeze(Set<ResourceLocation> commonIds) {
        validateIds(commonIds, REGISTRATIONS.keySet());
        frozen = true;
    }

    /** Validate only after every add-on has completed construction, so registration order is immaterial. */
    public static void validateIds(Set<ResourceLocation> commonIds, Set<ResourceLocation> animationIds) {
        var missing = new TreeSet<>(Objects.requireNonNull(commonIds));
        missing.removeAll(Objects.requireNonNull(animationIds));
        var unknown = new TreeSet<>(animationIds);
        unknown.removeAll(commonIds);
        if (!missing.isEmpty() || !unknown.isEmpty())
            throw new IllegalStateException("Bogey animation registrations do not match common registrations; missing="
                    + missing + ", unknown=" + unknown);
    }

    private static final class Backend {
        private static final BogeyAnimationBackend INSTANCE = load();
        private static BogeyAnimationBackend load() {
            var providers = ServiceLoader.load(BogeyAnimationBackend.class,
                    BogeyAnimationBackend.class.getClassLoader()).stream().toList();
            if (providers.size() != 1)
                throw new IllegalStateException("HeXie requires exactly one bogey animation backend, found " + providers.size());
            return providers.getFirst().get();
        }
    }
}
