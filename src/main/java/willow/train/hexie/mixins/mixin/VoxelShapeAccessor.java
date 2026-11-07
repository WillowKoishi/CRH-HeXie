package willow.train.hexie.mixins.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {
    @Accessor("shape")
    DiscreteVoxelShape hexie$getShape();
}
