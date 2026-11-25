package willow.train.hexie.world.blockentity.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blockentity.base.HexieBlockEntity;
import willow.train.hexie.world.blocks.base.BlockEntityAnimation;
import willow.train.hexie.world.blocks.base.IContraptionMovementBlockEntity;
import willow.train.hexie.world.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class OBJRenderableEndFaceEntity extends HexieBlockEntity implements IContraptionMovementBlockEntity, BlockEntityAnimation, BlockEntityViews.OBJRenderableEndFaceEntityView {
    private final DoorAnimation animation = new DoorAnimation();
    public float progress(float partial) { return animation.value(partial); }
    @Override public void restoreAnimation(BlockEntity previous) {
        if(previous instanceof OBJRenderableEndFaceEntity source) {
            counter=source.counter;animation.reset(source.animation.value(1));
        }
    }
    private void advanceAnimation() {
        animation.advance(isDoorOpen ? 1 : 0, .075f);
        counter = animation.value(1);
    }

    public float counter = 0f;
    public boolean isDoorOpen;
    public boolean isRotateDoor;
    public boolean inContraption = false;

    public OBJRenderableEndFaceEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.OBJ_RENDERABLE_END_FACE_BLOCK_ENTITY.get(), pPos, pBlockState);
        OBJRenderableEndFaceBlock block = (OBJRenderableEndFaceBlock) pBlockState.getBlock();
        this.isRotateDoor = block.isRotateDoor();
        this.isDoorOpen = block.isOpen(pBlockState);
        animation.reset(isDoorOpen?1:0);
        counter=isDoorOpen?1:0;
    }

    @Override
    public void tick() {
        if(level == null) return;
        this.isDoorOpen = getBlockState().getValue(DoorBlock.OPEN);
        advanceAnimation();
    }

    public DoorModelLocations getDoorModelLocations() {
        return ((OBJRenderableEndFaceBlock) getBlockState().getBlock()).getDoorModelLocations();
    }

    public void setOpen(boolean open) {
        this.isDoorOpen = open;
    }

    @Override
    public void doMovement(BlockPos blockPos, BlockState blockState, BlockEntity blockEntity) {
        var entity=(OBJRenderableEndFaceEntity)blockEntity;
        entity.setOpen(blockState.getValue(DoorBlock.OPEN));
        entity.advanceAnimation();
    }

    @Override public boolean isRotateDoor() { return isRotateDoor; }
    @Override public void isRotateDoor(boolean value) { isRotateDoor = value; }
}
