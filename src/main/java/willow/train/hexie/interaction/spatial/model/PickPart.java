package willow.train.hexie.interaction.spatial.model;

import java.util.Optional;
import java.util.UUID;

/** Exact cuboid in its own frame. localPose maps that frame into its host.
 * An empty control is an occluding solid. A target need not be its host. */
public record PickPart(UUID targetId, Optional<Id> controlId, AABB box, Pose localPose) {}
