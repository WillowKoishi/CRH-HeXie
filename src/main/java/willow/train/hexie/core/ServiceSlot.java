package willow.train.hexie.core;

import java.util.Objects;

/** A service supplied by the owning domain during mod construction. */
public final class ServiceSlot<T> {
    private final String name;
    private volatile T service;

    public ServiceSlot(String name) { this.name = name; }

    public synchronized void register(T implementation) {
        Objects.requireNonNull(implementation, name);
        if (service != null && service != implementation)
            throw new IllegalStateException(name + " is already registered");
        service = implementation;
    }

    public boolean isPresent() {
        return service != null;
    }

    public T get() {
        T value = service;
        if (value == null) throw new IllegalStateException(name + " has not been registered");
        return value;
    }
}
