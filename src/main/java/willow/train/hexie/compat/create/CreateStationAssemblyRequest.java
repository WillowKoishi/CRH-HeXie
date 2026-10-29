package willow.train.hexie.compat.create;

import java.util.Optional;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.blocks.track.standard.StandardTrackBlock;
import willow.train.hexie.vehicle.assembly.AssemblyRegion;
import willow.train.hexie.vehicle.assembly.AssemblyRequest;

/** Converts station range/trigger context to our request, without invoking Create train assembly. */
public final class CreateStationAssemblyRequest {
    private CreateStationAssemblyRequest() {}

    public static AssemblyRequest create(StationBlockEntity station, AssemblyRegion structureRegion) {
        requireServerThread(station);
        station.refreshAssemblyInfo();
        return fromRefreshed(station, structureRegion).orElseThrow(() ->
                new IllegalArgumentException("Station is not assembling exclusively HeXie bogeys on a HeXie track"));
    }

    /** Called immediately after the station's existing refresh; never scans the rail strip again. */
    public static Optional<AssemblyRequest> fromRefreshed(StationBlockEntity station, AssemblyRegion structureRegion) {
        requireServerThread(station);
        if (!station.isAssembling() || !station.edgePoint.hasValidTrack()
                || !station.edgePoint.isOrthogonal() || station.edgePoint.isOnCurve()
                || !(station.edgePoint.getTrack() instanceof StandardTrackBlock))
            return Optional.empty();
        if (station.getStation() == null || station.getStation().getPresentTrain() != null)
            return Optional.empty();
        if (!(station instanceof CreateAssemblyStationAccess access))
            throw new IllegalStateException("HeXie station integration is unavailable");
        return access.hexie$getAssemblySupports().map(supports ->
                new AssemblyRequest(structureRegion, supports, station.getAssemblyDirection()));
    }

    private static void requireServerThread(StationBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Station assembly requires the server thread");
    }
}
