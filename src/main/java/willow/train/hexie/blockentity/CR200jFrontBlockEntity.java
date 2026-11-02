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

public class CR200jFrontBlockEntity extends SmartBlockEntity implements willow.train.hexie.blocks.base.IContraptionMovementBlockEntity {
    public float counterFairing = 0f, counterWindShield = 0f;
    public float previousFairing, previousWindShield;
    private boolean isFairingOpen = false, isWindShieldActive = true;
    public boolean negative_direction = false;

    public CR200jFrontBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CR200J_FRONT_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Override public void tick() {
        super.tick();
        if (level != null) tickClientAnimation(level.getGameTime());
    }

    private long animationTick = Long.MIN_VALUE;
    @Override public void tickClientAnimation(long gameTime) {
        if (animationTick == gameTime) return;
        animationTick = gameTime;
        previousFairing = counterFairing;
        previousWindShield = counterWindShield;
        counterFairing = net.minecraft.util.Mth.clamp(counterFairing + (isFairingOpen ? .075f : -.075f), 0, 1);
        // Continue to the parked end of the current sweep when switched off.
        if (isWindShieldActive || counterWindShield >= 0 && counterWindShield <= 1) {
            counterWindShield += negative_direction ? -.0375f : .0375f;
            if (counterWindShield >= 1.35f) { counterWindShield = 2.7f-counterWindShield; negative_direction = true; }
            else if (counterWindShield <= -.35f) { counterWindShield = -.7f-counterWindShield; negative_direction = false; }
        }
    }

    @Override public void doMovement(com.simibubi.create.content.contraptions.Contraption contraption,
            BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        // Control flags remain owned by the block entity; the movement ticker advances animation.
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
