package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import willow.train.hexie.blocks.MegaphoneBlock;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.init.SoundsInit;

public class MegaphoneBlockEntity extends BlockEntity {
    private int timer;

    public MegaphoneBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.MEGAPHONE_BLOCK_ENTITIES.get(), pPos, pBlockState);
        this.timer = 0;
    }

    public static void tick(
            Level level, BlockPos blockPos, BlockState blockState, MegaphoneBlockEntity entity) {
        if (!level.isClientSide()) {
            entity.timer--;
            if (entity.timer <= 0) {
                entity.timer = 24;
                if (blockState.getValue(MegaphoneBlock.POWERED)) {
                    level.playSound(
                            null,
                            blockPos,
                            SoundsInit.CROSSING_RINGING.get(),
                            SoundSource.BLOCKS,
                            10f,
                            1f);
                }
            }
        }
    }
}
