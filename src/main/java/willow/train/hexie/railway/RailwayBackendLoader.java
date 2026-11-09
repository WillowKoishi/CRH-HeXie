package willow.train.hexie.railway;

import java.util.ServiceLoader;

/** The chosen backend is an implementation detail packaged with HeXie. */
public final class RailwayBackendLoader {
    private RailwayBackendLoader() {}
    private static final class Holder {
        static final RailwayBackend INSTANCE = load();
        private static RailwayBackend load() {
            var providers = ServiceLoader.load(RailwayBackend.class, RailwayBackend.class.getClassLoader()).stream().toList();
            if (providers.size() != 1) throw new IllegalStateException("HeXie requires exactly one railway backend, found " + providers.size());
            return providers.getFirst().get();
        }
    }
    public static RailwayBackend get() { return Holder.INSTANCE; }
}
