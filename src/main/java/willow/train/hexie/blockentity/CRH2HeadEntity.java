package willow.train.hexie.blockentity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.BlockEntitiesInit;
public final class CRH2HeadEntity extends BlockEntity {
    public CRH2HeadEntity(BlockPos p,BlockState s) { super(BlockEntitiesInit.CRH2A_HEAD.get(),p,s); }
}
