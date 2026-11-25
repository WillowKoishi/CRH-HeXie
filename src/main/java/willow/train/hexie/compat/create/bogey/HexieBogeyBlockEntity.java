package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.WorldBlockBridge;

public abstract class HexieBogeyBlockEntity extends AbstractBogeyBlockEntity
       implements TransformableBlockEntity, BlockEntityViews.HexieBogeyBlockEntityView {
    private boolean carriageView;
    public void markCarriageView() { carriageView = true; }
    public boolean isCarriageView() { return carriageView; }
    @Override public void syncBogeyData(CompoundTag data) {
        setBogeyData(data);
        setChanged();
        sendData();
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }
    protected HexieBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void transform(BlockEntity entity,
            StructureTransform transform) {
        int value = HexieBridges.worldBlock().bogeyFloorData().longitudinal(getBogeyData());
        if (value == 0) return;
        var axis = getBlockState().getValue(BlockStateProperties.HORIZONTAL_AXIS);
        var positive = axis == Direction.Axis.X
                ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        // The block state has already been transformed. Recover its unsigned source axis,
        // then apply the same linear transform to the saved signed displacement.
        var source = transform.unapplyWithoutOffset(positive.add(.5, .5, .5)).subtract(.5, .5, .5);
        var sourcePositive = Math.abs(source.x) > Math.abs(source.z)
                ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        double sign = transform.applyWithoutOffsetUncentered(sourcePositive).dot(positive);
        if (Math.abs(sign) > .5) {
            getBogeyData().putInt(WorldBlockBridge.BogeyFloorDataOperations.LONGITUDINAL_KEY, sign < 0 ? -value : value);
            setChanged();
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(2);
    }
}
