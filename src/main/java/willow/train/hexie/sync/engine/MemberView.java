package willow.train.hexie.sync.engine;

import java.util.UUID;
import willow.train.hexie.bridge.sync.VehicleSyncData.Membership;

public record MemberView(UUID trainId, int index, boolean reversed, long contentRevision) implements Membership {}
