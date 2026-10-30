package willow.train.hexie.mixins.mixin;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TrainMigration;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.CompatibilityTrain;
import willow.train.hexie.compat.create.CreateTrainAccess;

@Mixin(value = Train.class, remap = false)
public abstract class MixinCompatibilityTrain implements CreateTrainAccess {
    @Override @Invoker("collideWithOtherTrains")
    public abstract void hexie$collideWithOtherTrains(Level level, Carriage carriage);

    @Override @Invoker("updateNavigationTarget")
    public abstract void hexie$updateNavigationTarget(Level level, double distance);

    @Override @Invoker("tickDerailedSlowdown")
    public abstract void hexie$tickDerailedSlowdown();

    @Override @Accessor("migratingPoints")
    public abstract List<TrainMigration> hexie$getMigratingPoints();

    @Inject(method = "read", at = @At("RETURN"), cancellable = true)
    private static void hexie$restoreIndependentTrain(CompoundTag tag, HolderLookup.Provider registries,
            Map<UUID, TrackGraph> graphs, DimensionPalette dimensions, CallbackInfoReturnable<Train> cir) {
        if (tag.hasUUID(CompatibilityTrain.ASSEMBLY_KEY))
            cir.setReturnValue(CompatibilityTrain.fromSaved(cir.getReturnValue(), tag, registries, dimensions));
    }
}
