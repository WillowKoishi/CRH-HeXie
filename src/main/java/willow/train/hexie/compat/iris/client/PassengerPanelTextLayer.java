package willow.train.hexie.compat.iris.client;

import java.util.IdentityHashMap;
import java.util.Map;
import net.irisshaders.batchedentityrendering.impl.BlendingStateHolder;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Keeps recessed display lettering ahead of glass in Iris's final draw order. */
public final class PassengerPanelTextLayer extends RenderType implements BlendingStateHolder {
    private static final Map<RenderType, RenderType> LAYERS = new IdentityHashMap<>();
    private TransparencyType transparencyType = TransparencyType.OPAQUE_DECAL;

    private PassengerPanelTextLayer(RenderType nativeType) {
        // Delegate the native atlas, shader, blending and depth state unchanged. Only this
        // display-specific layer belongs in the opaque-decal pass; other font users do not.
        super("hexie_panel_text", nativeType.format(), nativeType.mode(), nativeType.bufferSize(),
                nativeType.affectsCrumbling(), nativeType.sortOnUpload(),
                nativeType::setupRenderState, nativeType::clearRenderState);
    }

    public static RenderType of(RenderType nativeType) {
        return LAYERS.computeIfAbsent(nativeType, PassengerPanelTextLayer::new);
    }

    public static void register(IEventBus bus) {
        bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(
                (ResourceManagerReloadListener) resources -> LAYERS.clear()));
    }

    // Do not implement WrappableRenderType: Iris would unwrap it before classifying it,
    // returning the native font to the same unordered transparency pass as the glass.
    @Override public TransparencyType getTransparencyType() { return transparencyType; }

    @Override public void setTransparencyType(TransparencyType type) { transparencyType = type; }
}
