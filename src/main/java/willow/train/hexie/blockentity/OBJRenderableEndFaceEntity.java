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

public class OBJRenderableEndFaceEntity extends SmartBlockEntity implements IContraptionMovementBlockEntity {

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
    }

    @Override
    public void tick() {
        if(level == null) return;
        this.isDoorOpen = level.getBlockState(this.getBlockPos()).getValue(DoorBlock.OPEN);
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
        ((OBJRenderableEndFaceEntity) blockEntity).setOpen(contraption.getBlocks().get(blockPos).state().getValue(DoorBlock.OPEN));
    }
}
