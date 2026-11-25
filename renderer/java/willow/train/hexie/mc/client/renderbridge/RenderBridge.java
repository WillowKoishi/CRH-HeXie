package willow.train.hexie.mc.client.renderbridge;

import dev.hexie.renderer.StructureRevision;
import dev.hexie.renderer.PresentationFrame;

/** The two outbound messages of master design §5.6. Defaults preserve the host renderer. */
public interface RenderBridge {
    RenderBridge ABSENT = new RenderBridge() {};
    default void structure(StructureRevision revision) {}
    default void present(PresentationFrame frame) {}
}
