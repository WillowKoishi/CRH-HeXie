package willow.train.hexie.blocks.bogeys.ss7d;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

public final class SS7DBogeyBlockEntity extends HexieBogeyBlockEntity {
    public SS7DBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public BogeyStyle getDefaultStyle() { return BogeyStylesInit.LOCOBOGEY; }

    @Override protected AABB createRenderBoundingBox() {
        // The body-end guides extend beyond the running frame at both ends.
        return super.createRenderBoundingBox().inflate(1);
    }
}
