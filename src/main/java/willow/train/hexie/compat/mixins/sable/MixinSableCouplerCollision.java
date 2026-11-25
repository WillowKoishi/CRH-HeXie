package willow.train.hexie.compat.mixins.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.bridge.HexieBridges;

/** Let mating heads meet without changing their ordinary selection or player collision shapes. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery", remap = false)
public abstract class MixinSableCouplerCollision {
    @WrapOperation(method = "buildPhysicsDataForBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            remap = true), remap = false, require = 1)
    private VoxelShape hexie$allowCouplerContact(BlockState state, BlockGetter level, BlockPos position,
            CollisionContext context, Operation<VoxelShape> original) {
        // The bakery caches by block state and supplies a synthetic world with no block entities.
        // Restrict this to the two blocks whose Simurail backend supplies the physical connection.
        if (ModList.get().isLoaded("simurail") && (state.is(HexieBridges.worldRegistry().blockInit().generalJanneyCoupler())
                || state.is(HexieBridges.worldRegistry().blockInit().crh2aAutoCoupler()))) return Shapes.empty();
        return original.call(state, level, position, context);
    }
}
