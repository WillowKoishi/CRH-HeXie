package willow.train.hexie.world.blockentity.head;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.world.blockentity.base.HexieBlockEntity;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class CR200jFrontBlockEntity extends HexieBlockEntity implements BlockEntityViews.CR200jFrontBlockEntityView {
    public float counterFairing = 0f, counterWindShield = 0f;
    private boolean isFairingOpen = false, isWindShieldActive = true;
    public boolean negative_direction = false;

    public CR200jFrontBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CR200J_FRONT_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("fairing", isFairingOpen);
        tag.putBoolean("wind_shield", isWindShieldActive);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putBoolean("fairing", isFairingOpen);
        tag.putBoolean("wind_shield", isWindShieldActive);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("fairing")) this.isFairingOpen = tag.getBoolean("fairing");
        if (tag.contains("wind_shield")) this.isWindShieldActive = tag.getBoolean("wind_shield");
    }

    public boolean isFairingOpen() {
        return isFairingOpen;
    }

    public boolean isWindShieldActive() {
        return isWindShieldActive;
    }

    public void switchWindShield() {
        this.isWindShieldActive = !isWindShieldActive;
    }

    public void switchFairing() {
        this.isFairingOpen = !isFairingOpen;
    }

    @Override public float counterFairing() { return counterFairing; }
    @Override public void counterFairing(float value) { counterFairing = value; }
    @Override public float counterWindShield() { return counterWindShield; }
    @Override public void counterWindShield(float value) { counterWindShield = value; }
    @Override public boolean negative_direction() { return negative_direction; }
    @Override public void negative_direction(boolean value) { negative_direction = value; }
}
