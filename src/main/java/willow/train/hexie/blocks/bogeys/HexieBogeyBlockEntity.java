package willow.train.hexie.blocks.bogeys;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public abstract class HexieBogeyBlockEntity extends AbstractBogeyBlockEntity
        implements com.simibubi.create.api.contraption.transformable.TransformableBlockEntity {
    private boolean carriageView;
    public void markCarriageView() { carriageView = true; }
    public boolean isCarriageView() { return carriageView; }
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }
    protected HexieBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void transform(net.minecraft.world.level.block.entity.BlockEntity entity,
            com.simibubi.create.content.contraptions.StructureTransform transform) {
        int value = BogeyFloorData.longitudinal(getBogeyData());
        if (value == 0) return;
        var axis = getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS);
        var positive = axis == net.minecraft.core.Direction.Axis.X
                ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 0, 1);
        // The block state has already been transformed. Recover its unsigned source axis,
        // then apply the same linear transform to the saved signed displacement.
        var source = transform.unapplyWithoutOffset(positive.add(.5, .5, .5)).subtract(.5, .5, .5);
        var sourcePositive = Math.abs(source.x) > Math.abs(source.z)
                ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 0, 1);
        double sign = transform.applyWithoutOffsetUncentered(sourcePositive).dot(positive);
        if (Math.abs(sign) > .5) {
            getBogeyData().putInt(BogeyFloorData.LONGITUDINAL_KEY, sign < 0 ? -value : value);
            setChanged();
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(2);
    }
}
