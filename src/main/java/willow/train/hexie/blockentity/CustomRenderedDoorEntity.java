package willow.train.hexie.blockentity;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import willow.train.hexie.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.blocks.base.IContraptionMovementBlockEntity;

import java.util.List;

public class CustomRenderedDoorEntity extends SmartBlockEntity implements IContraptionMovementBlockEntity {

    boolean isSlideDoor = false, open = false;
    public float animation_controller;

    public CustomRenderedDoorEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get(), pPos, pBlockState);
        if(pBlockState.getBlock() instanceof CustomRenderedDoorBlock block)
            this.isSlideDoor = block.isSlideDoor();
    }

    public boolean isLeftSide(BlockState state) {
        return state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
    }

    public boolean isLowerSide(BlockState state) {
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    public boolean isSlideDoor() {
        return isSlideDoor;
    }

    @Override
    public void tick() {
        if(level == null) return;
        this.open = getBlockState().getValue(DoorBlock.OPEN);
    }

    public boolean isOpen(){
        return open;
    }

    @OnlyIn(Dist.CLIENT)
    public Couple<PartialModel> getLeftModels(BlockState state) {
        return ((CustomRenderedDoorBlock) state.getBlock()).getLeftDoorModels();
    }

    @OnlyIn(Dist.CLIENT)
    public Couple<PartialModel> getRightModels(BlockState state) {
        return ((CustomRenderedDoorBlock) state.getBlock()).getRightDoorModels();
    }

    @OnlyIn(Dist.CLIENT)
    public Couple<PartialModel> getModels(BlockState state) {
        return isLeftSide(state) ? getLeftModels(state) : getRightModels(state);
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void doMovement(Contraption contraption, BlockPos blockPos, BlockState blockState, BlockEntity blockEntity) {
        ((CustomRenderedDoorEntity) blockEntity).setOpen(contraption.getBlocks().get(blockPos).state().getValue(DoorBlock.OPEN));
    }
}
