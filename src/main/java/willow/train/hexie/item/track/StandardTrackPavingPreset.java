package willow.train.hexie.item.track;

import com.simibubi.create.content.trains.track.TrackPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import willow.train.hexie.init.StandardTrackPavingDataInit;
import willow.train.hexie.init.track.TrackBlockInit;

/** Reads and validates the selected roadbed material on the standard track stack. */
public final class StandardTrackPavingPreset {
    private StandardTrackPavingPreset() {}

    public static boolean isStandardTrack(ItemStack stack) {
        return stack.is(TrackBlockInit.STANDARD_TRACK.asItem());
    }

    public static Block getBlock(ItemStack trackStack) {
        ResourceLocation id = trackStack.get(StandardTrackPavingDataInit.STANDARD_TRACK_PAVING_MATERIAL);
        Block block = id == null ? Blocks.COBBLESTONE : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return isEligibleBlock(block) ? block : Blocks.COBBLESTONE;
    }

    public static ItemStack getBlockStack(ItemStack trackStack) {
        return new ItemStack(getBlock(trackStack));
    }

    public static boolean isEligibleItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && isEligibleBlock(blockItem.getBlock());
    }

    public static void set(ItemStack trackStack, ItemStack selectedBlock) {
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
