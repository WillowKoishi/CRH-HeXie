package willow.train.hexie.render.bogey;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge;

/** Renderer input for a real bogey block or a bogey owned by a larger part. */
public record BogeyRenderData(BlockPos position, BlockState state, BogeyStyle style,
                              CompoundTag data, BlockEntity source) {
    public static BogeyRenderData resolve(BlockGetter world, BlockPos position) {
        if (world.getBlockEntity(position) instanceof AbstractBogeyBlockEntity bogey
                && bogey.getBlockState().getBlock() instanceof AbstractBogeyBlock<?>)
            return new BogeyRenderData(position, bogey.getBlockState(), bogey.getStyle(),
                    bogey.getBogeyData().copy(), bogey);
        var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(world, position);
        if (support == null || !(support.state().getBlock() instanceof AbstractBogeyBlock<?>)) return null;
        var entityData = support.entityData();
        return create(support.position(), support.state(),
                entityData == null ? new CompoundTag() : entityData.getCompound(CarriageBlockBridge.HeadBogeyOperations.DATA),
                world.getBlockEntity(support.owner()));
    }

    public static BogeyRenderData head(CRH2HeadEntityView head) {
        if (!head.bogeyEnabled()) return null;
        var facing = head.asBlockEntity().getBlockState().getValue(HexieBridges.carriageBlock().crh2HeadBlock().facing());
        return create(HexieBridges.carriageBlock().headBogey().mount(head.asBlockEntity().getBlockPos(), facing), HexieBridges.carriageBlock().headBogey().state(facing), head.bogeyData(), head.asBlockEntity());
    }

    private static BogeyRenderData create(BlockPos position, BlockState state, CompoundTag saved, BlockEntity source) {
        var block = (AbstractBogeyBlock<?>) state.getBlock();
        var id = ResourceLocation.tryParse(saved.getString("BogeyStyle"));
        var style = id == null ? null : AllBogeyStyles.BOGEY_STYLES.get(id);
        if (style == null) style = block.getDefaultStyle();
        var data = saved.copy();
        data.putString("BogeyStyle", style.id.toString());
        return new BogeyRenderData(position.immutable(), state, style, data, source);
    }

    public AbstractBogeyBlock<?> block() { return (AbstractBogeyBlock<?>) state.getBlock(); }
}
