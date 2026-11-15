package willow.train.hexie.blocks.emu.crh1a;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartMemberBlock;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.blocks.parts.PartStructures;

/** The second occupied column uses the same door owner for collision, clicking and removal. */
public final class CRH1DoorMemberBlock extends Block implements PartStructure,
        com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement {
    public CRH1DoorMemberBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(PartMemberBlock.withOwnerOffset(stateDefinition.any(), BlockPos.ZERO));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PartMemberBlock.X, PartMemberBlock.Y, PartMemberBlock.Z);
    }
    @Override public boolean isPartMember() { return true; }
    @Override public BlockPos partRoot(BlockGetter world, BlockPos pos, BlockState state) { return PartMemberBlock.owner(pos, state); }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter world, BlockPos root, BlockState state) {
        var owner = world.getBlockState(root);
        return owner.getBlock() instanceof CRH1DoorBlock door ? door.partCells(world, root, owner) : Map.of();
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    private VoxelShape shape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, boolean collision) {
        var owner = PartStructures.resolve(world, pos, state);
        if (owner == null) return Shapes.empty();
        BlockPos source = pos.getY() > owner.pos().getY() ? owner.pos().above() : owner.pos();
        var sourceState = world.getBlockState(source);
        if (!(sourceState.getBlock() instanceof CRH1DoorBlock door)) return Shapes.empty();
        if (collision) return door.collisionCellShape(sourceState, world, source, 1)
                .move(source.getX() - pos.getX(), 0, source.getZ() - pos.getZ());
        var shape = sourceState.getShape(world, source, context);
        return Shapes.joinUnoptimized(shape.move(source.getX() - pos.getX(), 0, source.getZ() - pos.getZ()),
                Shapes.block(), BooleanOp.AND);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shape(state, world, pos, context, false);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shape(state, world, pos, context, true);
    }
    private BlockHitResult redirect(BlockPos root, BlockHitResult hit) {
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), root, hit.isInside());
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        var owner = PartStructures.resolve(world, pos, state);
        return owner == null ? InteractionResult.PASS : owner.state().useWithoutItem(world, player, redirect(owner.pos(), hit));
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        var owner = PartStructures.resolve(world, pos, state);
        return owner == null ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : owner.state().useItemOn(stack, world, player, hand, redirect(owner.pos(), hit));
    }
    @Override public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        var owner = PartStructures.resolve(world, pos, state);
        return owner == null ? ItemStack.EMPTY : owner.state().getBlock().asItem().getDefaultInstance();
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var origin = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        var owner = origin == null ? null : PartStructures.resolve(builder.getLevel(), BlockPos.containing(origin), state);
        return owner == null ? List.of() : owner.state().getDrops(builder);
    }
    @Override public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        var owner = PartStructures.resolve(world, pos, state);
        if (!world.isClientSide && owner != null) world.destroyBlock(owner.pos(), !player.isCreative(), player);
        return super.playerWillDestroy(world, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState next, boolean moving) {
        if (!moving && !state.is(next.getBlock()) && !PartInstallation.mutating()) {
            var owner = PartStructures.resolve(world, pos, state);
            if (owner != null) PartInstallation.removeOtherCells(world, pos, owner.structure().partCells(world, owner.pos(), owner.state()));
        }
        super.onRemove(state, world, pos, next, moving);
    }
    @Override protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (world.isClientSide || moving || PartInstallation.mutating()) return;
        var owner = PartStructures.resolve(world, pos, state);
        if (owner != null && owner.state().getBlock() instanceof CRH1DoorBlock door)
            door.neighborChanged(owner.state(), world, owner.pos(), block, from, false);
        else world.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.hasChunkAt(partRoot(world, pos, state)) && PartStructures.resolve(world, pos, state) == null)
            world.removeBlock(pos, false);
    }
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity entity) {
        return com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;
    }
}
