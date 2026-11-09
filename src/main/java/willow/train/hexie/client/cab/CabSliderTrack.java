package willow.train.hexie.client.cab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import willow.train.hexie.api.interaction.BlockControl;

/** Maps a handle's legal detents onto its declared horizontal or vertical screen travel. */
public final class CabSliderTrack {
    private final BlockControl.Handle handle;
    private final BlockControl.Handle.DragAxis axis;
    private final int top, travel;
    private final List<Integer> notches;
    private final Map<Integer, Double> positions;

    /** Retains the existing explicit track constructor; top is the start coordinate on the chosen axis. */
    public CabSliderTrack(BlockControl.Handle handle, int top, int travel) {
        this(handle, handle.dragSpec().axis(), top, travel, readPositions(handle));
    }

    private CabSliderTrack(BlockControl.Handle handle, BlockControl.Handle.DragAxis axis, int top, int travel,
            Map<Integer, Double> positions) {
        if (positions.isEmpty()) throw new IllegalArgumentException("Handle has no draggable detents: " + handle.id());
        if (travel < 1) throw new IllegalArgumentException("Handle drag travel must be positive");
        this.handle = handle;
        this.axis = axis;
        this.top = top;
        this.travel = travel;
        this.notches = List.copyOf(positions.keySet());
        this.positions = Map.copyOf(positions);
    }

    private static Map<Integer, Double> readPositions(BlockControl.Handle handle) {
        var positions = new LinkedHashMap<Integer, Double>();
        for (int notch : handle.validNotches()) {
            double position = handle.sliderPosition(notch);
            if (Double.isFinite(position) && position >= 0 && position <= 1) positions.put(notch, position);
        }
        return positions;
    }

    /** Also used by captions so invalid or unbounded declarations cannot stall a render frame. */
    public static List<Integer> interactiveNotches(BlockControl.Handle handle) {
        return List.copyOf(readPositions(handle).keySet());
    }

    public static CabSliderTrack at(BlockControl.Handle handle, int notch, double pointer, int extent) {
        var positions = readPositions(handle);
        if (positions.isEmpty()) throw new IllegalArgumentException("Handle has no draggable detents: " + handle.id());
        double smallestGap = 1, previous = Double.NaN;
        for (double position : positions.values()) {
            double gap = Math.abs(position - previous);
            if (gap > 0 && Double.isFinite(gap)) smallestGap = Math.min(smallestGap, gap);
            previous = position;
        }
        int minimumTravel = (int) Math.min(Integer.MAX_VALUE, Math.ceil(1 / smallestGap - 1e-9));
        int available = Math.max(1, extent);
        int margin = (int) Math.clamp(((long) available - 1 - minimumTravel) / 2, 0, 8);
        var spec = handle.dragSpec();
        int travel = Math.max(1, Math.min(spec.travel(), available - 1 - 2 * margin));
        double position = positions.getOrDefault(notch, positions.values().iterator().next());
        double anchor = Double.isFinite(pointer) ? pointer : available / 2d;
        int top = (int) Math.clamp(Math.round(anchor - position * travel), margin,
                Math.max(margin, available - 1 - margin - travel));
        return new CabSliderTrack(handle, spec.axis(), top, travel, positions);
    }

    public BlockControl.Handle handle() { return handle; }
    public BlockControl.Handle.DragAxis axis() { return axis; }
    public int top() { return top; }
    public int travel() { return travel; }
    public List<Integer> notches() { return notches; }

    public double coordinate(int notch) {
        Double position = positions.get(notch);
        if (position == null) throw new IllegalArgumentException("Not a draggable detent: " + notch);
        return top + position * travel;
    }

    /** Existing vertical callers use this alias. */
    public double y(int notch) { return coordinate(notch); }

    public int nearest(double coordinate) {
        int nearest = notches.getFirst();
        double distance = Math.abs(coordinate - coordinate(nearest));
        for (int notch : notches) {
            double candidate = Math.abs(coordinate - coordinate(notch));
            if (candidate <= distance) {
                nearest = notch;
                distance = candidate;
            }
        }
        return nearest;
    }
}
