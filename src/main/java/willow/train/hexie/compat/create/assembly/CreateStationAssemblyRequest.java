package willow.train.hexie.compat.create.assembly;

import java.util.Optional;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblyRegionView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblyRequestView;
import willow.train.hexie.bridge.world.WorldBlockBridge.StandardTrackBlockView;

/** Converts station range/trigger context to our request, without invoking Create train assembly. */
public final class CreateStationAssemblyRequest {
    private CreateStationAssemblyRequest() {}

    public static AssemblyRequestView create(StationBlockEntity station, AssemblyRegionView structureRegion) {
        requireServerThread(station);
        station.refreshAssemblyInfo();
        return fromRefreshed(station, structureRegion).orElseThrow(() ->
                new IllegalArgumentException("Station is not assembling exclusively HeXie bogeys on a HeXie track"));
    }

    /** Called immediately after the station's existing refresh; never scans the rail strip again. */
    public static Optional<AssemblyRequestView> fromRefreshed(StationBlockEntity station, AssemblyRegionView structureRegion) {
        requireServerThread(station);
        if (!station.isAssembling() || !station.edgePoint.hasValidTrack()
                || !station.edgePoint.isOrthogonal() || station.edgePoint.isOnCurve()
                || !(station.edgePoint.getTrack() instanceof StandardTrackBlockView))
            return Optional.empty();
        if (station.getStation() == null || station.getStation().getPresentTrain() != null)
            return Optional.empty();
        if (!(station instanceof CreateAssemblyStationAccess access))
            throw new IllegalStateException("HeXie station integration is unavailable");
        return access.hexie$getAssemblySupports().map(supports ->
                HexieBridges.vehicle().createAssemblyRequest(structureRegion, supports, station.getAssemblyDirection()));
    }

    private static void requireServerThread(StationBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Station assembly requires the server thread");
    }
}
