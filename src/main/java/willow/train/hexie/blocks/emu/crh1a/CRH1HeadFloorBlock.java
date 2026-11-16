package willow.train.hexie.blocks.emu.crh1a;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.vehicle.assembly.OwnedBogeyMount;

/** Invisible load-bearing floor cells belonging to one CRH1 head. */
public final class CRH1HeadFloorBlock extends Block implements PartStructure, OwnedBogeyMount,
        com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    public CRH1HeadFloorBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(CRH1HeadBlock.FACING, Direction.NORTH).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CRH1HeadBlock.FACING, PART);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(CRH1HeadBlock.FACING, rotation.rotate(state.getValue(CRH1HeadBlock.FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        int part = state.getValue(PART);
        return rotate(state, mirror.getRotation(state.getValue(CRH1HeadBlock.FACING)))
                .setValue(PART, part / 3 * 3 + 2 - part % 3);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
    @Override public boolean isPartMember() { return true; }
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) {
        return pos.subtract(CRH1HeadBlock.offset(state.getValue(PART), state.getValue(CRH1HeadBlock.FACING)));
    }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState ignored) {
        var state = level.getBlockState(root);
        return state.getBlock() instanceof CRH1HeadBlock head ? head.partCells(level, root, state) : Map.of();
    }
    @Override public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        var root = partRoot(world, pos, state);
        return world.getBlockState(root).getBlock() instanceof CRH1HeadBlock head ? head.asItem().getDefaultInstance() : ItemStack.EMPTY;
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var origin = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if (origin == null) return List.of();
        var root = partRoot(params.getLevel(), BlockPos.containing(origin), state);
        var owner = params.getLevel().getBlockState(root);
        return owner.getBlock() instanceof CRH1HeadBlock ? List.of(owner.getBlock().asItem().getDefaultInstance()) : List.of();
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var root = partRoot(level, pos, state);
        if (!level.isClientSide && level.hasChunkAt(root) && level.getBlockState(root).getBlock() instanceof CRH1HeadBlock)
            level.destroyBlock(root, !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!moving && !state.is(next.getBlock()) && !PartInstallation.mutating()) {
            var root = partRoot(level, pos, state);
            if (level.hasChunkAt(root)) {
                var owner = level.getBlockState(root);
                if (owner.getBlock() instanceof CRH1HeadBlock head)
                    PartInstallation.removeOtherCells(level, pos, head.partCells(level, root, owner));
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        var root = partRoot(level, pos, state);
        if (!level.isClientSide && level.hasChunkAt(root) && level.getBlockState(root).getBlock() instanceof CRH1HeadBlock)
            level.scheduleTick(root, level.getBlockState(root).getBlock(), 1);
    }
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(
            BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;
    }
    // Only old assembled snapshots can contain this virtual support. New heads never write its enable flag.
    @Override public boolean isBogeyMount(BlockState state) { return state.getValue(PART) == CRH1HeadBlock.LEGACY_BOGEY_PART; }
    @Override public BlockPos bogeyOwner(BlockPos position, BlockState state) { return partRoot(null, position, state); }
    @Override public BlockState bogeyState(BlockState ownerState) { return CRH1HeadBogey.state(ownerState.getValue(CRH1HeadBlock.FACING)); }
    @Override public boolean bogeyEnabled(CompoundTag ownerData) { return ownerData.getBoolean(CRH1HeadBogey.ENABLED); }
}
