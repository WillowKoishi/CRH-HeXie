package willow.train.hexie.bogey;

import java.util.ServiceLoader;

/** The main mod supplies the active implementation when its railway backend is selected. */
public final class BogeyBackendLoader {
    private BogeyBackendLoader() {}
    private static final class Holder {
        private static final BogeyBackend INSTANCE = load();
    }
    public static BogeyBackend get() { return Holder.INSTANCE; }
    private static BogeyBackend load() {
        var providers = ServiceLoader.load(BogeyBackend.class, BogeyBackend.class.getClassLoader()).iterator();
        if (!providers.hasNext()) throw new IllegalStateException("No HeXie bogey backend installed");
        var backend = providers.next();
        if (providers.hasNext()) throw new IllegalStateException("Multiple HeXie bogey backends installed");
        return backend;
    }
}
