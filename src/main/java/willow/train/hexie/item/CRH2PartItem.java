package willow.train.hexie.item;

import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import willow.train.hexie.blocks.locos.crh2a.CRH2PartBlock;

/** Ordinary placement; only wide side pieces check for obstacles along the side. */
public final class CRH2PartItem extends BlockItem {
    public CRH2PartItem(Block block, Properties properties) { super(block,properties); }
    /** Preserve the existing extension helper's chosen position and facing. */
    public InteractionResult placeAtRoot(BlockPlaceContext c, BlockPos root, Direction facing) {
        return placeAtRoot(c,root,getBlock().defaultBlockState().setValue(CRH2PartBlock.FACING,facing));
    }
    public InteractionResult placeAtRoot(BlockPlaceContext c,BlockPos root,BlockState state) {
        return super.place(new RootContext(c,root,state));
    }
    public boolean canFit(BlockPlaceContext c, BlockPos root, BlockState state) {
        return ((CRH2PartBlock)getBlock()).canFitSide(c.getLevel(),root,state);
    }
    @Override protected boolean canPlace(BlockPlaceContext c, BlockState state) {
        return super.canPlace(c,state) && canFit(c,c.getClickedPos(),state);
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext c) {
        if (c instanceof RootContext root) {
            var state=root.state;
            return canPlace(c,state) ? state : null;
        }
        return super.getPlacementState(c);
    }
    @Override protected boolean placeBlock(BlockPlaceContext c,BlockState state) {
        if(!super.placeBlock(c,state))return false;
        ((CRH2PartBlock)getBlock()).fillSide(c.getLevel(),c.getClickedPos(),state);
        return true;
    }
    private static final class RootContext extends BlockPlaceContext {
        private final BlockPos root;
        private final BlockState state;
        RootContext(BlockPlaceContext c, BlockPos root, BlockState state) {
            super(c.getLevel(),c.getPlayer(),c.getHand(),c.getItemInHand(),new BlockHitResult(Vec3.atCenterOf(root),c.getClickedFace(),root,false));
            this.root=root; this.state=state;
        }
        @Override public BlockPos getClickedPos() { return root; }
        @Override public Direction getHorizontalDirection() { return state.getValue(CRH2PartBlock.FACING); }
        @Override public boolean canPlace() { return getLevel().getBlockState(root).canBeReplaced(this); }
    }
}
