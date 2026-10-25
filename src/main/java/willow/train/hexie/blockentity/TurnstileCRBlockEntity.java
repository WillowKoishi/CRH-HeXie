package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import willow.train.hexie.blocks.turnstile.TurnstileCRBlock;
import willow.train.hexie.init.BlockEntitiesInit;

public class TurnstileCRBlockEntity extends BlockEntity {
    private int timer;
    public boolean exit;

    public TurnstileCRBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.timer = 0;
        this.exit = false;
    }

    public TurnstileCRBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntitiesInit.TURNS_B.get(), pos, state);
    }

    public void setTimer(int timer) {
        if (this.timer == timer) return;
        this.timer = timer;
        setChanged();
    }

    @Override
    public void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        this.timer = nbt.getInt("timer");
        this.exit = nbt.getBoolean("exit");
        super.loadAdditional(nbt, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        nbt.putInt("timer", this.timer);
        nbt.putBoolean("exit", this.exit);
        super.saveAdditional(nbt, registries);
    }

    public static void tick(
            Level world, BlockPos pos, BlockState state, TurnstileCRBlockEntity be) {
        if (world.isClientSide()) return;
        if (be.timer <= 0 && !state.getValue(TurnstileCRBlock.OPEN)) return;
        if (be.timer > 0) {
            if (!state.getValue(TurnstileCRBlock.OPEN)) {
                world.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 1f);
                world.setBlockAndUpdate(pos, state.setValue(TurnstileCRBlock.OPEN, true));
            }
            be.timer--;
        } else {
            if (state.getValue(TurnstileCRBlock.OPEN)) {
                world.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 1f);
                world.setBlockAndUpdate(pos, state.setValue(TurnstileCRBlock.OPEN, false));
            }
        }
        be.setChanged();
    }
}
