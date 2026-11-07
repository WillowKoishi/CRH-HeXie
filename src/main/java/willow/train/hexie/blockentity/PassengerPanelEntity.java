package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.api.display.PassengerDisplayHost;
import willow.train.hexie.api.display.PassengerDisplaySource;
import willow.train.hexie.api.display.PassengerDisplayState;
import willow.train.hexie.api.display.PassengerCarriageNumber;
import willow.train.hexie.init.BlockEntitiesInit;

/** Display mounts persist in the host state. Timetable text is a live, read-only carriage view. */
public final class PassengerPanelEntity extends BlockEntity implements PassengerDisplayHost {
    private PassengerDisplaySource source = () -> PassengerDisplayState.EMPTY;
    private String carriageNumber = "";
    public PassengerPanelEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.PASSENGER_PANEL.get(), pos, state);
    }
    @Override public void passengerDisplaySource(PassengerDisplaySource source) { this.source = source; }
    @Override public PassengerDisplayState passengerDisplay() { return source.read(); }
    @Override public String carriageNumber() { return carriageNumber; }
    @Override public void setCarriageNumber(String number) {
        if (!PassengerCarriageNumber.valid(number) || carriageNumber.equals(number)) return;
        carriageNumber = number;
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        PassengerCarriageNumber.write(tag, carriageNumber);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        carriageNumber = PassengerCarriageNumber.read(tag);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
