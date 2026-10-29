package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.world.level.Level;

/** Existing Create decisions, invoked without entering its carriage movement loop. */
public interface CreateTrainAccess {
    void hexie$collideWithOtherTrains(Level level, Carriage carriage);
    void hexie$updateNavigationTarget(Level level, double distance);
}
