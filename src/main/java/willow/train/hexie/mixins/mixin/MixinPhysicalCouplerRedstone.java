package willow.train.hexie.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.compat.simurail.SimurailCouplerFactory;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.init.block.BlockInit;

/** Forward immediate neighbor notifications to the optional physical coupler backend. */
@Mixin(AbstractCouplerBlock.class)
public abstract class MixinPhysicalCouplerRedstone extends HorizontalDirectionalBlock {
    protected MixinPhysicalCouplerRedstone(Properties properties) {
        super(properties);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && ModList.get().isLoaded("simurail") && ModList.get().isLoaded("sable"))
            SimurailCouplerFactory.neighborChanged(level, pos);
    }

    @Inject(method = "getTicker(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Lnet/minecraft/world/level/block/entity/BlockEntityTicker;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private <T extends BlockEntity> void hexie$tickSemiautomaticCoupler(Level level, BlockState state,
            BlockEntityType<T> type, CallbackInfoReturnable<BlockEntityTicker<T>> callback) {
        if (callback.getReturnValue() == null && ModList.get().isLoaded("simurail")
                && ModList.get().isLoaded("sable") && state.is(BlockInit.CRH2A_END_FACE.get())
                && type == BlockEntitiesInit.COUPLER.get())
            callback.setReturnValue((world, pos, couplerState, entity) -> ((CouplerBlockEntity) entity).tickCoverAnimation());
    }
}
