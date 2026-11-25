package willow.train.hexie.world.blockentity.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class ElectricFenceDoorEntity extends BlockEntity implements BlockEntityViews.ElectricFenceDoorEntityView {

    public float x_offset = 0f;
    public static float MAX_OFFSET = 0.80f;
    public static float MIN_OFFSET = 0f;

    public ElectricFenceDoorEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.ELECTRIC_FENCE_DOOR.get(), pPos, pBlockState);
    }

    @Override public float x_offset() { return x_offset; }
    @Override public void x_offset(float value) { x_offset = value; }
}
