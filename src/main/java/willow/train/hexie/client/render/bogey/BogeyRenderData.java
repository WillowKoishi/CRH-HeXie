package willow.train.hexie.client.render.bogey;

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
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.blocks.emu.head.HeadBogey;
import willow.train.hexie.vehicle.assembly.AssemblyBogeySupport;

/** Renderer input for a real bogey block or a bogey owned by a larger part. */
public record BogeyRenderData(BlockPos position, BlockState state, BogeyStyle style,
                              CompoundTag data, BlockEntity source) {
    public static BogeyRenderData resolve(BlockGetter world, BlockPos position) {
        if (world.getBlockEntity(position) instanceof AbstractBogeyBlockEntity bogey
                && bogey.getBlockState().getBlock() instanceof AbstractBogeyBlock<?>)
            return new BogeyRenderData(position, bogey.getBlockState(), bogey.getStyle(),
                    bogey.getBogeyData().copy(), bogey);
        var support = AssemblyBogeySupport.resolve(world, position);
        if (support == null || !(support.state().getBlock() instanceof AbstractBogeyBlock<?>)) return null;
        var entityData = support.entityData();
        return create(support.position(), support.state(),
                entityData == null ? new CompoundTag() : entityData.getCompound(HeadBogey.DATA),
                world.getBlockEntity(support.owner()));
    }

    public static BogeyRenderData head(CRH2HeadEntity head) {
        if (!head.bogeyEnabled()) return null;
        var facing = head.getBlockState().getValue(CRH2HeadBlock.FACING);
        return create(HeadBogey.mount(head.getBlockPos(), facing), HeadBogey.state(facing), head.bogeyData(), head);
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
