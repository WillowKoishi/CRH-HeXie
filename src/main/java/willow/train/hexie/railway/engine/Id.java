package willow.train.hexie.railway.engine;

import java.util.Objects;

public record Id(String namespace, String path) {
    public Id {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(path);
    }

    public static Id parse(String namespacedId) {
        int separator = namespacedId.indexOf(':');
        if (separator < 1 || separator == namespacedId.length() - 1)
            throw new IllegalArgumentException("Expected namespace:path, got " + namespacedId);
        return new Id(namespacedId.substring(0, separator), namespacedId.substring(separator + 1));
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
