package willow.train.hexie.client.cab;

import willow.train.hexie.vehicle.control.Crh2CabHandle;

/** The on-screen travel between detents. The brake's rapid-to-key-removal interval is double width. */
public record Crh2CabSliderTrack(Crh2CabHandle handle, int top, int travel) {
    public static Crh2CabSliderTrack at(Crh2CabHandle handle, int notch, double pointerY, int height) {
        int margin = Math.clamp((height - 1 - units(handle, handle.maxNotch())) / 2, 0, 8);
        int travel = Math.max(1, Math.min(90, height - 1 - 2 * margin));
        int top = Math.clamp((int) Math.round(pointerY - offset(handle, notch, travel)), margin,
                Math.max(margin, height - 1 - margin - travel));
        return new Crh2CabSliderTrack(handle, top, travel);
    }

    private static int units(Crh2CabHandle handle, int notch) {
        return notch - handle.minNotch() + (handle == Crh2CabHandle.BRAKE && notch == 9 ? 1 : 0);
    }

    private static double offset(Crh2CabHandle handle, int notch, int travel) {
        int span = units(handle, handle.maxNotch());
        int position = units(handle, notch);
        return travel * (handle == Crh2CabHandle.POWER ? position : span - position) / (double) span;
    }

    public double y(int notch) {
        return top + offset(handle, notch, travel);
    }

    public int nearest(double y) {
        for (int n = handle.minNotch(); n < handle.maxNotch(); n++) {
            double midpoint = (y(n) + y(n + 1)) / 2;
            if (handle == Crh2CabHandle.POWER ? y < midpoint : y > midpoint) return n;
        }
        return handle.maxNotch();
    }
}
