package willow.train.hexie.compat.simurail;

import java.util.List;
import java.util.Map;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlock;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.blocks.parts.PartStructures;

/** The selectable kinetic controller remains a real block, but belongs to the head's single item. */
public final class PhysicalHeadBogeyBlock extends PhysicsBogeyBlock implements PartStructure, SpecialBlockItemRequirement {
    public PhysicalHeadBogeyBlock(Properties properties) { super(properties); }
    @Override public boolean isPartMember() { return true; }
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(HORIZONTAL_FACING));
    }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state) {
        var head = level.getBlockState(root);
        return head.getBlock() instanceof PhysicalHeadBlock block ? block.partCells(level, root, head) : Map.of();
    }
    @Override public ItemRequirement getRequiredItems(BlockState state, BlockEntity entity) { return ItemRequirement.NONE; }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder context) { return List.of(); }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return SimurailHeadRegistration.PHYSICAL_HEAD_ITEM.get().getDefaultInstance();
    }
    @Override public BlockEntityType<PhysicsBogeyBlockEntity> getBlockEntityType() {
        return SimurailHeadRegistration.PHYSICAL_HEAD_BOGEY_ENTITY.get();
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !moving && !state.is(next.getBlock()) && !PartInstallation.mutating()) {
            var root = PartStructures.resolve(level, pos, state);
            if (root != null) PartInstallation.removeOtherCells(level, pos,
                    root.structure().partCells(level, root.pos(), root.state()));
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var root = PartStructures.resolve(level, pos, state);
        if (!level.isClientSide && root != null) level.destroyBlock(root.pos(), !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        var level = context.getLevel();
        var root = PartStructures.resolve(level, context.getClickedPos(), state);
        if (root == null) return InteractionResult.PASS;
        if (!level.isClientSide) level.destroyBlock(root.pos(), context.getPlayer() == null || !context.getPlayer().isCreative(), context.getPlayer());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var level = context.getLevel();
        var root = PartStructures.resolve(level, context.getClickedPos(), state);
        if (root == null) return InteractionResult.PASS;
        var rotated = getRotatedBlockState(state, context.getClickedFace());
        var facing = rotated.getValue(HORIZONTAL_FACING);
        if (facing == state.getValue(HORIZONTAL_FACING)) return InteractionResult.PASS;
        if (!level.isClientSide) level.setBlock(root.pos(), root.state().setValue(HORIZONTAL_FACING, facing), UPDATE_ALL);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
