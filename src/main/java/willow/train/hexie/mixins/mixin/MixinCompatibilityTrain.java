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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import willow.train.hexie.compat.create.CompatibilityTrain;
import willow.train.hexie.compat.create.CreateConsoleTimetable;
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

    @Inject(method = "disassemble", at = @At("HEAD"))
    private void hexie$saveConsoleTimetable(CallbackInfoReturnable<Boolean> callback) {
        CreateConsoleTimetable.saveProgress((Train) (Object) this);
    }

    @WrapMethod(method = "read")
    private static Train hexie$restoreIndependentTrain(CompoundTag tag, HolderLookup.Provider registries,
            Map<UUID, TrackGraph> graphs, DimensionPalette dimensions, Operation<Train> original) {
        // All native RETURN injections finish on the source before its ownership is transferred.
        Train source = original.call(tag, registries, graphs, dimensions);
        if (tag.hasUUID(CompatibilityTrain.ASSEMBLY_KEY))
            return CompatibilityTrain.fromSaved(source, tag, registries, dimensions);
        CreateConsoleTimetable.restoreRuntime(source, tag.getCompound("Runtime"), registries);
        return source;
    }
}
