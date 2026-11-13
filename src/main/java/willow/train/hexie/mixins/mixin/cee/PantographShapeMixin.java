package willow.train.hexie.mixins.mixin.cee;

import willow.train.hexie.compat.cee.PantographMounting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlock", remap = false)
public abstract class PantographShapeMixin {
    @Inject(method = "getNodePosition", at = @At("RETURN"), cancellable = true)
    private void hexie$moveNode(Level level, BlockPos pos, BlockState state, int id, CallbackInfoReturnable<Vec3> callback) {
        if (callback.getReturnValue() != null)
            callback.setReturnValue(callback.getReturnValue().add(0, PantographMounting.offset(level, pos), 0));
    }

    @Inject(method = "getNodePositions", at = @At("RETURN"), cancellable = true)
    private void hexie$moveNodes(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Map<Integer, Vec3>> callback) {
        Map<Integer, Vec3> nodes = new HashMap<>();
        double offset = PantographMounting.offset(level, pos);
        callback.getReturnValue().forEach((id, position) -> nodes.put(id, position.add(0, offset, 0)));
        callback.setReturnValue(nodes);
    }
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void hexie$moveShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                 CallbackInfoReturnable<VoxelShape> callback) {
        callback.setReturnValue(callback.getReturnValue().move(0, PantographMounting.offset(level, pos), 0));
    }
}
