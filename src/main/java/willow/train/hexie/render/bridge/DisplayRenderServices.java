package willow.train.hexie.render.bridge;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.render.DisplayRenderBridge;
import willow.train.hexie.core.api.display.PassengerDisplayState;
import willow.train.hexie.render.display.CarriageMarkingRenderer;
import willow.train.hexie.render.sign.CarriageNumberUpdates;
import willow.train.hexie.render.sign.PanelEditUpdates;
import willow.train.hexie.render.sign.PassengerDisplayCache;
import willow.train.hexie.render.sign.SignTextTextures;

final class DisplayRenderServices implements DisplayRenderBridge {
    private final CarriageMarkingRendererOperations carriageMarkingRenderer = CarriageMarkingRenderer::drawLabel;

    private final CarriageNumberUpdatesOperations carriageNumberUpdates = CarriageNumberUpdates::apply;

    private final PanelEditUpdatesOperations panelEditUpdates = PanelEditUpdates::apply;

    private final PassengerDisplayCacheOperations passengerDisplayCache = new PassengerDisplayCacheOperations() {
        @Override public void accept(Level level, UUID carriage, PassengerDisplayState state) {
            PassengerDisplayCache.accept(level, carriage, state);
        }
        @Override public PassengerDisplayState read(Level level, UUID carriage) {
            return PassengerDisplayCache.read(level, carriage);
        }
    };

    private final SignTextRendererOperations signTextRenderer = new SignTextRendererOperations() {
        @Override public void retain(ResourceLocation texture) {
            SignTextTextures.retain(texture);
        }
        @Override public void release(ResourceLocation texture) {
            SignTextTextures.release(texture);
        }
    };

    @Override public CarriageMarkingRendererOperations carriageMarkingRenderer() {
        return carriageMarkingRenderer;
    }

    @Override public CarriageNumberUpdatesOperations carriageNumberUpdates() {
        return carriageNumberUpdates;
    }

    @Override public PanelEditUpdatesOperations panelEditUpdates() {
        return panelEditUpdates;
    }

    @Override public PassengerDisplayCacheOperations passengerDisplayCache() {
        return passengerDisplayCache;
    }

    @Override public SignTextRendererOperations signTextRenderer() {
        return signTextRenderer;
    }
}
