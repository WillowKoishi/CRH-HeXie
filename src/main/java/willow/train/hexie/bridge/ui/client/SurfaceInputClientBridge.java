package willow.train.hexie.bridge.ui.client;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge;
import willow.train.hexie.bridge.sync.SyncBridge;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

public interface SurfaceInputClientBridge {
    SurfaceControlCaptionOperations surfaceControlCaption();

    interface SurfaceControlCaptionOperations {
        void register(RegisterClientTooltipComponentFactoriesEvent event);
    }

    SurfaceInputClientOperations surfaceInputClient();

    interface SurfaceInputClientOperations {
        DrivingConsoleContext cab();
        DrivingConsoleContext cameraCab();
        boolean capturesInput();
        boolean dragging();
        Set<String> heldControls();
        void captureResult(SyncBridge.ControlInputPacketTarget target, int vehicleId, BlockPos position,
                long requestId, boolean accepted);
        boolean handleKey(int key, int scanCode, int action);
    }

    SurfaceInputSourcesOperations surfaceInputSources();

    interface SurfaceInputSourcesOperations {
        void registerSource(SurfaceInputSourcesSourceView source);
    }

    interface SurfaceInputSourcesSourceView {
        void collect(Minecraft client, SurfaceInputBridge.SurfaceInputSourcesRayView ray, float partialTick,
                SurfaceInputBridge.SurfaceInputSourcesQueryView query);
    }
}
