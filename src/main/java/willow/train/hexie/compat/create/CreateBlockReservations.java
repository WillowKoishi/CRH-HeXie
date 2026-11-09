package willow.train.hexie.compat.create;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Atomic internal reservation shared by backend-owned railway and bogey block builders. */
public final class CreateBlockReservations {
    private static final Map<ResourceLocation, String> OWNERS = new HashMap<>();
    private CreateBlockReservations() {}

    /** Check every generated ID before reserving any; no public catalog locks are acquired. */
    public static synchronized void reserve(String owner, List<ResourceLocation> blockIds) {
        Objects.requireNonNull(owner, "owner");
        var unique = new LinkedHashSet<ResourceLocation>();
        for (var id : blockIds) {
            Objects.requireNonNull(id, "block ID");
            if (!unique.add(id))
                throw new IllegalArgumentException(owner + " generates the same block ID twice: " + id);
            var previous = OWNERS.get(id);
            if (previous != null)
                throw new IllegalArgumentException("Block ID " + id + " for " + owner + " is already owned by " + previous);
        }
        unique.forEach(id -> OWNERS.put(id, owner));
    }
}
