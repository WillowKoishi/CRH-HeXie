package willow.train.hexie.compat.cee;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;

public final class PantographMounting {
    static final ResourceLocation CEE_PANTOGRAPH = ResourceLocation.parse("electroenergetics:pantograph");
    private PantographMounting() {}

    public static double offset(BlockGetter level, BlockPos pos) {
        var support = pos.below();
        var state = level.getBlockState(support);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("hexie")) return 0;
        var shape = state.getShape(level, support);
        if (shape.isEmpty()) return 0;
        double top = shape.max(Direction.Axis.Y);
        return top > 0 && top < 1 ? top - 1 : 0;
    }

    public static double movingOffset(MovementContext context) {
        return offset(context.contraption.getContraptionWorld(), context.localPos);
    }

    public static double movingOffset(Carriage carriage, BlockPos local) {
        var entity = carriage.anyAvailableEntity();
        if (entity == null || !(entity.getContraption() instanceof CarriageContraption view)) return 0;
        var block = view.getBlocks().get(local);
        if (block == null || !BuiltInRegistries.BLOCK.getKey(block.state().getBlock()).equals(CEE_PANTOGRAPH)) return 0;
        return offset(view.getContraptionWorld(), local);
    }
}
