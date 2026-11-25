package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.trains.track.TrackPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.StandardTrackPavingPresetOperations;

/** Reads and validates the selected roadbed material on the standard track stack. */
public final class StandardTrackPavingPreset implements StandardTrackPavingPresetOperations {
    public static final StandardTrackPavingPreset INSTANCE = new StandardTrackPavingPreset();

    private StandardTrackPavingPreset() {}

    @Override public boolean isStandardTrack(ItemStack stack) {
        return stack.is(CreateTrackBlockInit.standardTrack().asItem());
    }

    public static Block getBlock(ItemStack trackStack) {
        ResourceLocation id = trackStack.get(StandardTrackPavingDataInit.STANDARD_TRACK_PAVING_MATERIAL);
        Block block = id == null ? Blocks.COBBLESTONE : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return isEligibleBlock(block) ? block : Blocks.COBBLESTONE;
    }

    @Override public ItemStack getBlockStack(ItemStack trackStack) {
        return new ItemStack(getBlock(trackStack));
    }

    @Override public boolean isEligibleItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && isEligibleBlock(blockItem.getBlock());
    }

    @Override public void set(ItemStack trackStack, ItemStack selectedBlock) {
        if (!isStandardTrack(trackStack))
            return;

        if (selectedBlock.isEmpty()) {
            trackStack.remove(StandardTrackPavingDataInit.STANDARD_TRACK_PAVING_MATERIAL);
        } else if (isEligibleItem(selectedBlock)) {
            Block block = ((BlockItem) selectedBlock.getItem()).getBlock();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (Blocks.COBBLESTONE.equals(block)) {
                trackStack.remove(StandardTrackPavingDataInit.STANDARD_TRACK_PAVING_MATERIAL);
            } else {
                trackStack.set(StandardTrackPavingDataInit.STANDARD_TRACK_PAVING_MATERIAL, id);
            }
        }

        TrackPlacement.cached = null;
    }

    private static boolean isEligibleBlock(Block block) {
        if (block == null || block.asItem() == Items.AIR)
            return false;

        var shape = block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return Block.isShapeFullBlock(shape);
    }
}
