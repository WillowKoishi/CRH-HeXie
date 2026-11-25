package willow.train.hexie.world.blockentity.coupler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.base.CouplerBlockEntityBase;
import willow.train.hexie.world.blocks.base.BlockEntityAnimation;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class CouplerBlockEntity extends CouplerBlockEntityBase implements BlockEntityAnimation {
    /** Optional backends choose their supported blocks after mod loading. */
    public static BlockEntity create(BlockPos pos, BlockState state) {
        var mods = ModList.get();
        if (mods.isLoaded("simurail") && mods.isLoaded("sable"))
            return HexieBridges.simurail().createSimurailCouplerFactory(pos, state).asBlockEntity();
        return new CouplerBlockEntity(pos, state);
    }

    public CouplerBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntitiesInit.COUPLER.get(), pos, state);
    }

    public CouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
