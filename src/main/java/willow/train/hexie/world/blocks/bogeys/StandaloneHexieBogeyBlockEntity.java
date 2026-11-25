package willow.train.hexie.world.blocks.bogeys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageViewBlockEntityView;

public final class StandaloneHexieBogeyBlockEntity extends BlockEntity implements CarriageViewBlockEntityView {
    private CompoundTag bogeyData = new CompoundTag();
    private boolean carriageView;

    public StandaloneHexieBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public CompoundTag getBogeyData() { return bogeyData; }
    @Override public void markCarriageView() { carriageView = true; }
    @Override public boolean isCarriageView() { return carriageView; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("BogeyData", bogeyData.copy());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bogeyData = tag.getCompound("BogeyData").copy();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
