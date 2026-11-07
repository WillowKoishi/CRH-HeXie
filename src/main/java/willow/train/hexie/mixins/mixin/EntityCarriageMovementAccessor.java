package willow.train.hexie.mixins.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only when accepting a carriage report, without querying its floor again. */
@Mixin(Entity.class)
public interface EntityCarriageMovementAccessor {
    @Accessor("onGround")
    void hexie$setReportedOnGround(boolean onGround);

    @Accessor("mainSupportingBlockPos")
    void hexie$setReportedSupportingBlock(Optional<BlockPos> position);

    @Accessor("onGroundNoBlocks")
    void hexie$setReportedOnGroundNoBlocks(boolean onGround);
}
