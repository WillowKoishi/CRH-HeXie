package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TrainMigration;
import java.util.List;
import net.minecraft.world.level.Level;

/** Existing Create decisions, invoked without entering its carriage movement loop. */
public interface CreateTrainAccess {
    void hexie$collideWithOtherTrains(Level level, Carriage carriage);
    void hexie$updateNavigationTarget(Level level, double distance);
    void hexie$tickDerailedSlowdown();
    List<TrainMigration> hexie$getMigratingPoints();
}
