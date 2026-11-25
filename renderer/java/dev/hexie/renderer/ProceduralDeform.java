package dev.hexie.renderer;

import java.util.List;

/** Final group-local LOD0 positions shared by all consumers of this frame. */
public record ProceduralDeform(long groupId, long instanceId, long poseRevision, List<Vec> frameVertices) {
    public ProceduralDeform { frameVertices = List.copyOf(frameVertices); }
}
