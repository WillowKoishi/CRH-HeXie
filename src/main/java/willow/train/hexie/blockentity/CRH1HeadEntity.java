package willow.train.hexie.blockentity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.base.CarriageViewBlockEntity;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadAnimation;
import willow.train.hexie.init.BlockEntitiesInit;

/** CRH1 head data, including its independently animated covers and coupler. */
public final class CRH1HeadEntity extends SmartBlockEntity implements CarriageViewBlockEntity {
    private CompoundTag animation = new CompoundTag();
    private boolean carriageView;

    public CRH1HeadEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.CRH1A_HEAD.get(), pos, state);
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    @Override public void markCarriageView() { carriageView = true; }
    public boolean isCarriageView() { return carriageView; }
    public float animationProgress(float partialTick) {
        double time = level == null ? 0 : level.getGameTime() + (double) partialTick;
        return CRH1HeadAnimation.progress(getBlockState(), animation, time);
    }
    public void beginAnimation(long time) {
        float current = CRH1HeadAnimation.progress(getBlockState(), animation, time);
        animation.putFloat(CRH1HeadAnimation.FROM, current);
        animation.putLong(CRH1HeadAnimation.START, time);
        animation.putBoolean(CRH1HeadAnimation.TARGET,
                !getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN));
        setChanged();
    }
    public void syncAnimation() { setChanged(); sendData(); }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel && !carriageView
                && level.getBlockEntity(worldPosition) == this)
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeData(tag);
    }
    @Override public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeData(tag);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        animation = new CompoundTag();
        for (String key : List.of(CRH1HeadAnimation.FROM, CRH1HeadAnimation.START, CRH1HeadAnimation.TARGET))
            if (tag.contains(key)) animation.put(key, tag.get(key).copy());
    }
    private void writeData(CompoundTag tag) {
        animation.getAllKeys().forEach(key -> tag.put(key, animation.get(key).copy()));
        tag.remove("HeadBogeyEnabled");
        tag.remove("BogeyData");
    }
}
