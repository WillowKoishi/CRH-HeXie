package willow.train.hexie.world.blocks.locos;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockinteract.LocoBlockInteract;
import willow.train.hexie.world.blocks.parts.ModelRenderBounds;
import willow.train.hexie.world.collision.LocoCollision;
import willow.train.hexie.world.placement.LocoPlacement;

/** Standalone one-block SS7D locomotive floor. */
public final class SS7DFloorBlock extends HorizontalDirectionalBlock implements HexieWrenchable, ModelRenderBounds, CarriageBlockBridge.SS7DFloorBlockView {
    @Override public BlockState cycleEquipment(BlockState state) { return state.cycle(EQUIPMENT); }

    public static final MapCodec<SS7DFloorBlock> CODEC = simpleCodec(SS7DFloorBlock::new);
    /** 0 is the original plain floor; 1–4 are the four equipment orientations. */
    public static final IntegerProperty EQUIPMENT = IntegerProperty.create("equipment", 0, 4);

    // Match the walkable half-height collision used by the CRH1A/CRH2A floor blocks.

    public SS7DFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(EQUIPMENT, 0));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EQUIPMENT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return LocoPlacement.ForSS7DFloorBlock.getStateForPlacement(this, context);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                      Player player, InteractionHand hand, BlockHitResult hit) {
        return LocoBlockInteract.ForSS7DFloorBlock.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }

    /** Snapshots written before the equipment option represent an empty underbody. */
    public static CompoundTag upgradeSnapshotProperties(CompoundTag properties) {
        if (properties.contains(EQUIPMENT.getName())) return properties;
        var upgraded = properties.copy();
        upgraded.putString(EQUIPMENT.getName(), "0");
        return upgraded;
    }

    @Override public AABB modelRenderBounds(BlockState state) {
        // The equipment projects beyond the floor cell in every possible orientation.
        return new AABB(-1, -0.125, -1, 2, 1, 2);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) {
        return LocoCollision.ForSS7DFloorBlock.getShape(state, level, pos, context);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                  CollisionContext context) {
        return LocoCollision.ForSS7DFloorBlock.getCollisionShape(state, level, pos, context);
    }

    @Override public Block asBlock() { return this; }
}
