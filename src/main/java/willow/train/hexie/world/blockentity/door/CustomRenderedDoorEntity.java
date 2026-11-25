package willow.train.hexie.world.blockentity.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blockentity.base.HexieBlockEntity;
import willow.train.hexie.world.blocks.base.BlockEntityAnimation;
import willow.train.hexie.world.blocks.base.IContraptionMovementBlockEntity;
import willow.train.hexie.world.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.world.blocks.emu.CRH2DoorBlock;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class CustomRenderedDoorEntity extends HexieBlockEntity implements IContraptionMovementBlockEntity, BlockEntityAnimation, BlockEntityViews.CustomRenderedDoorEntityView {

    public static final String REVERSE_SLIDE_TAG = "ReverseSlide";
    boolean isSlideDoor = false, open = false;
    public float animation_controller;
    private boolean passengerFrame;
    private boolean reverseSlide;
    public boolean hasReverseSlide() { return reverseSlide; }
    public void setReverseSlide(boolean reverse) {
        if (!supportsPassengerFrame() || !isLowerSide(getBlockState()) || reverseSlide == reverse) return;
        reverseSlide = reverse;
        notifyUpdate();
    }
    public boolean hasPassengerFrame() { return passengerFrame; }
    public void togglePassengerFrame() {
        passengerFrame = !passengerFrame;
        setChanged();
        sendData();
    }
    private final DoorAnimation crh2Animation = new DoorAnimation();
    public float crh2Progress() { return crh2Animation.value(1); }
    public float crh2Progress(float partial) { return crh2Animation.value(partial); }
    public float crh2InteractionProgress() {
        // Every transition uses the closed trigger. Only the fully open state uses the open one.
        return open && getBlockState().getValue(DoorBlock.OPEN) && crh2Progress() >= 1 ? 1 : 0;
    }
    public void copyAnimationFrom(CustomRenderedDoorEntity source) {
        animation_controller = source.animation_controller;
        crh2Animation.reset(source.crh2Progress());
    }
    public void setCarriageAnimation(boolean open, float previous, float current) {
        this.open = open;
        crh2Animation.set(previous, current);
        animation_controller = current;
    }

    /** Advance on a discrete tick grid so fractional accumulation cannot add an extra endpoint tick. */
    public static float tickProgress(float previous, boolean open, int durationTicks) {
        if (durationTicks <= 0) throw new IllegalArgumentException("Animation duration must be positive");
        int tick = Math.clamp(Math.round(previous * durationTicks) + (open ? 1 : -1), 0, durationTicks);
        return tick / (float) durationTicks;
    }
    @Override public void restoreAnimation(BlockEntity previous) {
        if(previous instanceof CustomRenderedDoorEntity source)copyAnimationFrom(source);
    }

    public CustomRenderedDoorEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get(), pPos, pBlockState);
        passengerFrame = supportsPassengerFrame();
        open = pBlockState.getValue(DoorBlock.OPEN);
        if(pBlockState.getBlock() instanceof CustomRenderedDoorBlock block)
            this.isSlideDoor = block.isSlideDoor();
        crh2Animation.reset(open ? 1 : 0);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeAppearance(tag);
    }
    @Override public void writeSafe(CompoundTag tag,HolderLookup.Provider registries) {
        super.writeSafe(tag,registries);writeAppearance(tag);
    }
    private boolean supportsPassengerFrame() {
        return getBlockState().getBlock() instanceof CRH2DoorBlock door && door.supportsPassengerFrame();
    }
    private void writeAppearance(CompoundTag tag) {
        if (supportsPassengerFrame()) {
            tag.putBoolean("PassengerFrame", passengerFrame);
            if (isLowerSide(getBlockState())) tag.putBoolean(REVERSE_SLIDE_TAG, reverseSlide);
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        passengerFrame = supportsPassengerFrame() && (!tag.contains("PassengerFrame") || tag.getBoolean("PassengerFrame"));
        reverseSlide = supportsPassengerFrame() && isLowerSide(getBlockState()) && tag.getBoolean(REVERSE_SLIDE_TAG);
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
        advanceAnimation();
    }

    private void advanceAnimation() {
        if (getBlockState().getBlock() instanceof CustomRenderedDoorBlock door && door.animationDurationTicks() > 0) {
            float previous = crh2Animation.value(1);
            setCarriageAnimation(open, previous, tickProgress(previous, open, door.animationDurationTicks()));
            return;
        }
        float speed = getBlockState().getBlock() instanceof CRH2DoorBlock
                ? CRH2DoorBlock.ANIMATION_SPEED : isSlideDoor ? .075f : .15f;
        crh2Animation.advance(open ? 1 : 0, speed);
        animation_controller=crh2Animation.value(1);
    }

    public boolean isOpen(){
        return open;
    }

    public DoorModelLocations getDoorModelLocations(BlockState state) {
        var block = (CustomRenderedDoorBlock) state.getBlock();
        return isLeftSide(state) ? block.getLeftDoorModelLocations() : block.getRightDoorModelLocations();
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public void doMovement(BlockPos blockPos, BlockState blockState, BlockEntity blockEntity) {
        var door = (CustomRenderedDoorEntity) blockEntity;
        door.setOpen(blockState.getValue(DoorBlock.OPEN));
        door.advanceAnimation();
    }
}
