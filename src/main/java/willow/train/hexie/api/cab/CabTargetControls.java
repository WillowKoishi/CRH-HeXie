package willow.train.hexie.api.cab;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.InteractionResult;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlUse;

/** Physical desk operations after input validation, independent of a driver's seat. */
public final class CabTargetControls {
    public interface Adapter {
        boolean supports(BlockControlContext target);
        InteractionResult operate(BlockControlUse use, BlockControl control, int value);
    }

    private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private CabTargetControls() {}

    /** Register during common setup. The first supporting adapter owns the operation. */
    public static void register(Adapter adapter) { ADAPTERS.add(Objects.requireNonNull(adapter)); }

    public static InteractionResult operate(BlockControlUse use, BlockControl control, int value) {
        for (var adapter : ADAPTERS)
            if (adapter.supports(use.target())) return adapter.operate(use, control, value);
        return InteractionResult.PASS;
    }
}
