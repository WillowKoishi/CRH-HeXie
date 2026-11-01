package willow.train.hexie.blockentity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.init.BlockEntitiesInit;

import java.util.List;

public class CR200jFrontBlockEntity extends SmartBlockEntity {
    public float counterFairing = 0f, counterWindShield = 0f;
    private boolean isFairingOpen = false, isWindShieldActive = true;
    public boolean negative_direction = false;

    public CR200jFrontBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CR200J_FRONT_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Override
    protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("fairing", isFairingOpen);
        tag.putBoolean("wind_shield", isWindShieldActive);
    }

    @Override
    public void writeSafe(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putBoolean("fairing", isFairingOpen);
        tag.putBoolean("wind_shield", isWindShieldActive);
    }

    @Override
    protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
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

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public void switchWindShield() {
        this.isWindShieldActive = !isWindShieldActive;
    }

    public void switchFairing() {
        this.isFairingOpen = !isFairingOpen;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return AABB.ofSize(Vec3.atLowerCornerOf(this.getBlockPos()), 10, 10, 10);
    }
}
