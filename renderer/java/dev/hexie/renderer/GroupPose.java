package dev.hexie.renderer;

import java.util.Optional;

public record GroupPose(long groupId, long attachParentId, Pose evaluatedOrCurr, Optional<Pose> prevForInterp) {
    public static final long ROOT = -1;
    public Pose localAt(float partialTick) {
        return prevForInterp.map(p -> p.interpolate(evaluatedOrCurr, partialTick)).orElse(evaluatedOrCurr);
    }
}
