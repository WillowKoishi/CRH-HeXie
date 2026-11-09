package willow.train.hexie.blocks.bogeys.df11g;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.bogey.BogeyStyleMigration;

public class DF11GBogeyBlockEntity extends HexieBogeyBlockEntity {
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
        BogeyStyleMigration.normalizeData(data, "BogeyStyle", block.getSize().id());
        return data;
    }
}
