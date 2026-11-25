package willow.train.hexie.interaction.item.editable_panels;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.HexieBridges;

/**
 * Unified custom-sticker tool supporting both parameterized windows (geometry cutting + frame/glass)
 * and locally uploaded image decals on arbitrary unassembled world or physical-structure blocks.
 */
public class CustomStickerItem extends Item {
    public CustomStickerItem(Properties properties) {
        super(properties);
    }

    /** Right-clicking air (normal or sneaking) opens the mode & parameter selector screen. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            HexieBridges.Client.stickerClient().openModeScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Right-clicking a block face confirms placement or enters free-camera edit mode for saved windows. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getLevel().isClientSide) {
                HexieBridges.Client.stickerClient().openModeScreen();
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().isClientSide) {
            boolean handled = HexieBridges.Client.stickerClient().handleRightClickBlock(
                    context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
            return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }
}
