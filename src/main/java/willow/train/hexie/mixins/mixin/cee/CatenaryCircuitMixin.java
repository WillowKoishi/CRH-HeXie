package willow.train.hexie.mixins.mixin.cee;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import willow.train.hexie.compat.cee.PantographMounting;
import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.simulation.infrastructure.CatenaryModule", remap = false)
public abstract class CatenaryCircuitMixin {
    @ModifyExpressionValue(method = "buildCircuit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hexie$moveElectricalContact(Vec3 original, @Local Carriage carriage,
            @Local TrainPantographEntry entry) {
        return original.add(0, PantographMounting.movingOffset(carriage, entry.originalPos), 0);
    }
}
