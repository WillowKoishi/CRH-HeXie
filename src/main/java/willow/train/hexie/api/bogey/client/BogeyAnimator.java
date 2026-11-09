package willow.train.hexie.api.bogey.client;

/** An add-on computes its own part transforms. HeXie has already applied the whole bogey pose. */
@FunctionalInterface
public interface BogeyAnimator {
    void render(BogeyAnimationContext context, BogeyPartRenderer parts);
}
