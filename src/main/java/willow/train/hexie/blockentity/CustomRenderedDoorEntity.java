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
    private final net.createmod.catnip.animation.LerpedFloat crh2Animation = net.createmod.catnip.animation.LerpedFloat.linear();
    public float crh2Progress() { return crh2Animation.getValue(); }
    public float crh2Progress(float partial) { return crh2Animation.getValue(partial); }
    public float crh2InteractionProgress() {
        // Every transition uses the closed trigger. Only the fully open state uses the open one.
        return open && getBlockState().getValue(DoorBlock.OPEN) && crh2Progress() >= 1 ? 1 : 0;
    }
    public void copyAnimationFrom(CustomRenderedDoorEntity source) {
        animation_controller = source.animation_controller;
        crh2Animation.startWithValue(source.crh2Progress());
    }

    public CustomRenderedDoorEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get(), pPos, pBlockState);
        open = pBlockState.getValue(DoorBlock.OPEN);
        if(pBlockState.getBlock() instanceof CustomRenderedDoorBlock block)
            this.isSlideDoor = block.isSlideDoor();
        crh2Animation.startWithValue(open ? 1 : 0);
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
        advanceCRH2Animation();
    }

    private void advanceCRH2Animation() {
        if (getBlockState().getBlock() instanceof willow.train.hexie.blocks.locos.crh2a.CRH2DoorBlock) {
            crh2Animation.chase(open ? 1 : 0,
                    willow.train.hexie.blocks.locos.crh2a.CRH2DoorBlock.ANIMATION_SPEED,
                    net.createmod.catnip.animation.LerpedFloat.Chaser.LINEAR);
            crh2Animation.tickChaser();
        }
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
        var door = (CustomRenderedDoorEntity) blockEntity;
        door.setOpen(contraption.getBlocks().get(blockPos).state().getValue(DoorBlock.OPEN));
        door.advanceCRH2Animation();
    }
}
