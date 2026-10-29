package willow.train.hexie.compat.create;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Station-local state and the supports already discovered by Create's station rail scan. */
public interface CreateAssemblyStationAccess {
    @Nullable UUID hexie$getAssemblyId();
    void hexie$setAssemblyId(@Nullable UUID id);
    boolean hexie$hasAssemblyFailure();
    void hexie$setAssemblyFailure(@Nullable Exception failure);

    /** Empty when no supports were discovered or any support belongs to another implementation. */
    Optional<List<BlockPos>> hexie$getAssemblySupports();
}
