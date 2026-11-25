package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort;

public class DF11GBogeyBlockEntity extends HexieBogeyBlockEntity implements CreateTrainBridgePort.DF11GBogeyBlockEntityView {
    public DF11GBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return ((DF11GBogeyBlock) getBlockState().getBlock()).getDefaultStyle();
    }

    @Override
    public CompoundTag getBogeyData() {
        CompoundTag data = super.getBogeyData();
        var block = (DF11GBogeyBlock) getBlockState().getBlock();
        HexieBridges.bogeyRegistry().bogeyStyleMigration().normalizeData(data, "BogeyStyle", block.getSize().id());
        return data;
    }
}
