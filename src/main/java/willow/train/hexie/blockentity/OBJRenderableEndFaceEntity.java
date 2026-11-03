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
import willow.train.hexie.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.blocks.base.IContraptionMovementBlockEntity;

import java.util.List;

public class OBJRenderableEndFaceEntity extends SmartBlockEntity implements IContraptionMovementBlockEntity, willow.train.hexie.blocks.base.BlockEntityAnimation {
    private final net.createmod.catnip.animation.LerpedFloat animation=net.createmod.catnip.animation.LerpedFloat.linear();
    public float progress(float partial) { return animation.getValue(partial); }
    @Override public void restoreAnimation(BlockEntity previous) {
        if(previous instanceof OBJRenderableEndFaceEntity source) {
            counter=source.counter;animation.startWithValue(source.animation.getValue());
        }
    }
    private void advanceAnimation() {
        animation.chase(isDoorOpen?1:0,.075,net.createmod.catnip.animation.LerpedFloat.Chaser.LINEAR);
        animation.tickChaser();counter=animation.getValue();
    }

    public Couple<PartialModel> doorModels;
    public float counter = 0f;
    public boolean isDoorOpen;
    public boolean isRotateDoor;
    public boolean inContraption = false;

    public OBJRenderableEndFaceEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.OBJ_RENDERABLE_END_FACE_BLOCK_ENTITY.get(), pPos, pBlockState);
        OBJRenderableEndFaceBlock block = (OBJRenderableEndFaceBlock) pBlockState.getBlock();
        if (this.level != null && this.level.isClientSide)
            doorModels = block.getDoorModels();
        else
            doorModels = null;
        this.isRotateDoor = block.isRotateDoor();
        this.isDoorOpen = block.isOpen(pBlockState);
        animation.startWithValue(isDoorOpen?1:0);
        counter=isDoorOpen?1:0;
    }

    @Override
    public void tick() {
        if(level == null) return;
        this.isDoorOpen = getBlockState().getValue(DoorBlock.OPEN);
        advanceAnimation();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Couple<PartialModel> getDoorModels() {
        if(doorModels != null) return doorModels;
        else if(hasLevel() && level.isClientSide)
            if(level.getBlockState(this.getBlockPos()).getBlock() instanceof OBJRenderableEndFaceBlock block)
                return block.getDoorModels();
            return null;
    }

    public void setOpen(boolean open) {
        this.isDoorOpen = open;
    }

    @Override
    public void doMovement(Contraption contraption, BlockPos blockPos, BlockState blockState, BlockEntity blockEntity) {
        var entity=(OBJRenderableEndFaceEntity)blockEntity;
        entity.setOpen(contraption.getBlocks().get(blockPos).state().getValue(DoorBlock.OPEN));
        entity.advanceAnimation();
    }
}
