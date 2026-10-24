package willow.train.hexie.compat;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

public final class InteractionResults {
    private InteractionResults() {}

    public static ItemInteractionResult toItemResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            // The legacy interaction has already run, so do not invoke it again without an item.
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemInteractionResult.FAIL;
        };
    }
}
