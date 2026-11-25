package willow.train.hexie.interaction.spatial.model;

import java.util.Objects;

public record Id(String namespace, String path) {
    public Id {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
