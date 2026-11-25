package willow.train.hexie.railway.engine;

import willow.train.hexie.core.api.vehicle.Point3;

public record Pose(Point3 position, Quat rotation) {}
