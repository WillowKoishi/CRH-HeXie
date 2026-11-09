package willow.train.hexie.api.cab;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.vehicle.control.CabModule;

/** Read-only control state and animation for a desk, including one with no occupied seat. */
public record CabTargetView(CabModule.State controls, ControlSurface.AnimationSource animation) {
    private static final List<Function<BlockControlContext, CabTargetView>> PROVIDERS = new CopyOnWriteArrayList<>();

    public CabTargetView { Objects.requireNonNull(controls); }

    /** Register during common setup; return null for targets owned by another provider. */
    public static void register(Function<BlockControlContext, CabTargetView> provider) {
        PROVIDERS.add(Objects.requireNonNull(provider));
    }

    public static CabTargetView find(BlockControlContext target) {
        for (var provider : PROVIDERS) {
            var view = provider.apply(target);
            if (view != null) return view;
        }
        var entity = target.blocks().getBlockEntity(target.position());
        return new CabTargetView(entity instanceof CabControlsHost host ? host.controls() : CabModule.State.NEUTRAL,
                entity instanceof ControlSurface.AnimationSource source ? source : null);
    }
}
