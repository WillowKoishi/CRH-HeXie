package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.BlockEntitiesInit;

/** Render-only entity; placement and side-cell ownership stay in CRH2PartBlock. */
public final class CRH2PartEntity extends BlockEntity {
    public CRH2PartEntity(BlockPos pos, BlockState state) { super(BlockEntitiesInit.CRH2A_PART.get(), pos, state); }
}
