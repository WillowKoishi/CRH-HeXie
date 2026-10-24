package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import willow.train.hexie.init.BlockEntitiesInit;

public class ElectricFenceDoorEntity extends BlockEntity {

    public float x_offset = 0f;
    public static float MAX_OFFSET = 0.80f;
    public static float MIN_OFFSET = 0f;

    public ElectricFenceDoorEntity(
            BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

    public ElectricFenceDoorEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.ELECTRIC_FENCE_DOOR.get(), pPos, pBlockState);
    }
}
