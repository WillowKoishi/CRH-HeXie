package willow.train.hexie.client.cab;

import willow.train.hexie.api.cab.CabControl;

/** Maps the handle's declared screen positions to the available vertical track. */
public record CabSliderTrack(CabControl.Handle handle, int top, int travel) {
    public static CabSliderTrack at(CabControl.Handle handle, int notch, double pointerY, int height) {
        double smallestGap = 1;
        for (int n = handle.minNotch(); n < handle.maxNotch(); n++)
            smallestGap = Math.min(smallestGap, Math.abs(handle.sliderPosition(n + 1) - handle.sliderPosition(n)));
        int minimumTravel = (int) Math.ceil(1 / smallestGap - 1e-9);
        int margin = Math.clamp((height - 1 - minimumTravel) / 2, 0, 8);
        int travel = Math.max(1, Math.min(90, height - 1 - 2 * margin));
        int top = Math.clamp((int) Math.round(pointerY - handle.sliderPosition(notch) * travel), margin,
                Math.max(margin, height - 1 - margin - travel));
        return new CabSliderTrack(handle, top, travel);
    }

    public double y(int notch) {
        return top + handle.sliderPosition(notch) * travel;
    }

    public int nearest(double y) {
        int nearest = handle.minNotch();
        double distance = Math.abs(y - y(nearest));
        for (int n = nearest + 1; n <= handle.maxNotch(); n++) {
            double candidate = Math.abs(y - y(n));
            if (candidate <= distance) {
                nearest = n;
                distance = candidate;
            }
        }
        return nearest;
    }
}
