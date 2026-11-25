package willow.train.hexie.interaction.bridge;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import willow.train.hexie.bridge.sync.SyncBridge;
import willow.train.hexie.bridge.ui.client.SurfaceInputClientBridge;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.interaction.surface.SurfaceControlCaption;
import willow.train.hexie.interaction.surface.SurfaceInputClient;
import willow.train.hexie.interaction.surface.SurfaceInputSources;

final class ClientSurfaceInputServices implements SurfaceInputClientBridge,
        SurfaceInputClientBridge.SurfaceControlCaptionOperations,
        SurfaceInputClientBridge.SurfaceInputClientOperations,
        SurfaceInputClientBridge.SurfaceInputSourcesOperations {
    @Override
    public SurfaceControlCaptionOperations surfaceControlCaption() {
        return this;
    }

    @Override
    public SurfaceInputClientOperations surfaceInputClient() {
        return this;
    }

    @Override
    public SurfaceInputSourcesOperations surfaceInputSources() {
        return this;
    }

    @Override
    public void register(RegisterClientTooltipComponentFactoriesEvent event) {
        SurfaceControlCaption.register(event);
    }

    @Override
    public DrivingConsoleContext cab() {
        return SurfaceInputClient.cab();
    }

    @Override
    public DrivingConsoleContext cameraCab() {
        return SurfaceInputClient.cameraCab();
    }

    @Override
    public boolean capturesInput() {
        return SurfaceInputClient.capturesInput();
    }

    @Override
    public boolean dragging() {
        return SurfaceInputClient.dragging();
    }

    @Override
    public Set<String> heldControls() {
        return SurfaceInputClient.heldControls();
    }

    @Override
    public void captureResult(SyncBridge.ControlInputPacketTarget target, int vehicleId, BlockPos position,
                long requestId, boolean accepted) {
        SurfaceInputClient.captureResult(target, vehicleId, position, requestId, accepted);
    }

    @Override
    public boolean handleKey(int key, int scanCode, int action) {
        return SurfaceInputClient.handleKey(key, scanCode, action);
    }

    @Override
    public void registerSource(SurfaceInputSourcesSourceView source) {
        SurfaceInputSources.registerSource(source);
    }
}
