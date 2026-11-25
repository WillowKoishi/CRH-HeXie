package willow.train.hexie.bridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.sync.SyncBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.core.api.display.PassengerDisplayState;

public interface DisplayRenderBridge {
    CarriageMarkingRendererOperations carriageMarkingRenderer();

    interface CarriageMarkingRendererOperations {
        void drawLabel(PartBlockBridge.PanelTextView label, PartBlockBridge.VehicleMarkingSurfaceView block,
                AABB localBounds, int slot, PoseStack pose, MultiBufferSource buffers, int light);
    }

    CarriageNumberUpdatesOperations carriageNumberUpdates();

    interface CarriageNumberUpdatesOperations {
        void apply(Level level, SyncBridge.CarriageNumberPacketView packet);
    }

    PanelEditUpdatesOperations panelEditUpdates();

    interface PanelEditUpdatesOperations {
        void apply(Level level, SyncBridge.PanelEditPacketView packet);
    }

    PassengerDisplayCacheOperations passengerDisplayCache();

    interface PassengerDisplayCacheOperations {
        void accept(Level level, UUID carriage, PassengerDisplayState state);
        PassengerDisplayState read(Level level, UUID carriage);
    }

    SignTextRendererOperations signTextRenderer();

    interface SignTextRendererOperations {
        void retain(ResourceLocation texture);
        void release(ResourceLocation texture);
    }

    interface SignTextRendererTextureOwnerView extends MultiBufferSource {
        void retainSignTexture(ResourceLocation texture);
    }
}
