package dev.hexie.renderer;

import java.util.List;
import java.util.function.Predicate;

public final class ExternalEntityLighting {
    private ExternalEntityLighting() {}
    /** sceneEmpty must cover all opaque LOD0 casters in the current snapshot, including off-screen geometry. */
    public static int packedLight(int original,RenderTier tier,Bounds drawBounds,List<GroupLightTree.Light> lights,
            Predicate<Bounds> sceneEmpty) {
        if (tier!=RenderTier.FULL) return original;
        double energy=0;
        for (var light:lights) {
            LightState state=light.state();
            if (!state.enabled() || state.support(64)!=LightState.Support.SUPPORTED) continue;
            double distance=drawBounds.nearest(light.position());
            if (distance>=state.radius() || !sceneEmpty.test(drawBounds.union(Bounds.point(light.position())))) continue;
            Vec color=state.linearRgb();
            energy+=state.energy(distance,Visibility.VISIBLE,64)*(0.2126*color.x()+0.7152*color.y()+0.0722*color.z());
        }
        int level=Math.max((original>>>4)&15,(int)Math.round(15*Math.min(1,energy)));
        return original & ~0xf0 | level<<4;
    }
}
