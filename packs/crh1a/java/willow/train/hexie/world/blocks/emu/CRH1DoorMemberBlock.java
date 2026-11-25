package willow.train.hexie.world.blocks.emu;

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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorMemberBlockView;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.parts.PartMemberBlock;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.PartInstallation;

/** The second occupied column uses the same door owner for collision, clicking and removal. */
public final class CRH1DoorMemberBlock extends Block implements PartStructure,
        HexieItemRequirementProvider, CRH1DoorMemberBlockView {
    public CRH1DoorMemberBlock(BlockBehaviour.Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(PartMemberBlock.withOwnerOffset(stateDefinition.any(), BlockPos.ZERO));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PartMemberBlock.X, PartMemberBlock.Y, PartMemberBlock.Z);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return PartMemberBlock.withOwnerOffset(state, PartMemberBlock.ownerOffset(state).rotate(rotation));
    }
    @Override public boolean isPartMember() { return true; }
    @Override public BlockPos partRoot(BlockGetter world, BlockPos pos, BlockState state) { return PartMemberBlock.owner(pos, state); }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter world, BlockPos root, BlockState state) {
        var owner = world.getBlockState(root);
        return owner.getBlock() instanceof CRH1DoorBlock door ? door.partCells(world, root, owner) : Map.of();
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    public VoxelShape shape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, boolean collision) {
        return EmuCollision.ForCRH1DoorMemberBlock.shape(this, state, world, pos, context, collision);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH1DoorMemberBlock.getShape(this, state, world, pos, context);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH1DoorMemberBlock.getCollisionShape(this, state, world, pos, context);
    }
    public BlockHitResult redirect(BlockPos root, BlockHitResult hit) {
        return EmuBlockInteract.ForCRH1DoorMemberBlock.redirect(root, hit);
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return EmuBlockInteract.ForCRH1DoorMemberBlock.useWithoutItem(this, state, world, pos, player, hit);
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return EmuBlockInteract.ForCRH1DoorMemberBlock.useItemOn(this, stack, state, world, pos, player, hand, hit);
    }
    @Override public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        var owner = PartStructures.resolve(world, pos, state);
        return owner == null ? ItemStack.EMPTY : owner.state().getBlock().asItem().getDefaultInstance();
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
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
    @Override public Requirement getRequiredItems(BlockState state,
            BlockEntity entity) {
        return Requirement.NONE;
    }
}
