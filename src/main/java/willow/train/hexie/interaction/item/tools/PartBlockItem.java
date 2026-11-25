package willow.train.hexie.interaction.item.tools;

import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.PartBlockItemView;
import willow.train.hexie.bridge.world.PartBlockBridge.PartStructureView;

/** Ordinary BlockItem semantics with a single placement transaction for declared members. */
public class PartBlockItem extends BlockItem implements PartBlockItemView {
    public PartBlockItem(Block block,Properties properties) { super(block,properties); }
    public InteractionResult placeAtRoot(BlockPlaceContext context,BlockPos root,BlockState state) {
        return place(new RootContext(context,root,HexieBridges.partBlock().legacyPartLayout().placed(state)));
    }
    public InteractionResult placeAtRoot(BlockPlaceContext context,BlockPos root,Direction facing) {
        return placeAtRoot(context,root,getBlock().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,facing));
    }
    public boolean canFit(BlockPlaceContext context,BlockPos root,BlockState state) {
        return !(getBlock() instanceof PartStructureView part)||HexieBridges.partBlock().partInstallation().available(context.getLevel(),part.partCells(context.getLevel(),root,state),
                pos->false,pos->context.getPlayer()==null||context.getPlayer().mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()));
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        var state=context instanceof RootContext root?root.state:super.getPlacementState(context);
        if(state==null)return null;
        state=HexieBridges.partBlock().legacyPartLayout().placed(state);
        return canPlace(context,state)?state:null;
    }
    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state) {
        return super.canPlace(context,state)&&canFit(context,context.getClickedPos(),state);
    }
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state) {
        if(!(getBlock() instanceof PartStructureView part))return super.placeBlock(context,state);
        return HexieBridges.partBlock().partInstallation().install(context.getLevel(),part.partCells(context.getLevel(),context.getClickedPos(),state),
                pos->false,pos->context.getPlayer()==null||context.getPlayer().mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()));
    }
    private static final class RootContext extends BlockPlaceContext {
        private final BlockPos root;private final BlockState state;
        RootContext(BlockPlaceContext context,BlockPos root,BlockState state) {
            super(context.getLevel(),context.getPlayer(),context.getHand(),context.getItemInHand(),new BlockHitResult(Vec3.atCenterOf(root),context.getClickedFace(),root,false));
            this.root=root;this.state=state;
        }
        @Override public BlockPos getClickedPos() { return root; }
        @Override public Direction getHorizontalDirection() { return state.getValue(BlockStateProperties.HORIZONTAL_FACING); }
        @Override public boolean canPlace() { return getLevel().getBlockState(root).canBeReplaced(this); }
    }
}
