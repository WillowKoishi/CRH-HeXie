package willow.train.hexie.vehicle.runtime;

/** One reversible adapter mounted on the original hook head. Thickness is measured in blocks. */
public record CouplerAdapter(String itemId, String firstModel, String secondModel, double thickness,
                             java.util.List<CouplerAdapter> layers, CouplingEndpoint.Point offset) {
    public CouplerAdapter(String itemId, String firstModel, String secondModel, double thickness) {
        this(itemId, firstModel, secondModel, thickness, java.util.List.of(), new CouplingEndpoint.Point(0, 0, -thickness));
    }

    public CouplerAdapter(String itemId, String firstModel, String secondModel, double thickness,
                          java.util.List<CouplerAdapter> layers) {
        this(itemId, firstModel, secondModel, thickness, layers,
                layers.isEmpty() ? new CouplingEndpoint.Point(0, 0, -thickness) : offsetOf(firstModel, layers));
    }

    public CouplerAdapter(String itemId, String firstModel, String secondModel, double thickness,
                          CouplingEndpoint.Point offset) {
        this(itemId, firstModel, secondModel, thickness, java.util.List.of(), offset);
    }

    public CouplerAdapter {
        requireIdentifier(itemId, "adapter item");
        requireIdentifier(firstModel, "first coupling model");
        requireIdentifier(secondModel, "second coupling model");
        if (!Double.isFinite(thickness) || thickness <= 0)
            throw new IllegalArgumentException("Coupler adapter thickness must be finite and positive");
        layers = java.util.List.copyOf(layers);
        java.util.Objects.requireNonNull(offset, "adapter interface offset");
        if (Math.abs(offset.z() + thickness) > 1e-9)
            throw new IllegalArgumentException("Adapter depth differs from interface offset");
        if (!layers.isEmpty()) {
            String exposed = firstModel;
            double total = 0;
            for (var layer : layers) {
                if (!layer.layers.isEmpty() || (exposed = layer.otherModel(exposed)) == null)
                    throw new IllegalArgumentException("Incompatible adapter stack");
                total += layer.thickness;
            }
            if (!exposed.equals(secondModel) || Math.abs(total - thickness) > 1e-9)
                throw new IllegalArgumentException("Invalid adapter stack summary");
            var expected = offsetOf(firstModel, layers);
            if (Math.abs(expected.x() - offset.x()) > 1e-9 || Math.abs(expected.y() - offset.y()) > 1e-9
                    || Math.abs(expected.z() - offset.z()) > 1e-9)
                throw new IllegalArgumentException("Invalid adapter stack offset");
        }
    }

    /** Individual items, ordered from the original hook outwards. */
    public java.util.List<CouplerAdapter> items() { return layers.isEmpty() ? java.util.List.of(this) : layers; }

    public static CouplerAdapter stack(String baseModel, java.util.List<CouplerAdapter> items) {
        if (items.isEmpty()) return null;
        String exposed = baseModel;
        double thickness = 0;
        for (var item : items) {
            if (!item.layers.isEmpty() || (exposed = item.otherModel(exposed)) == null)
                throw new IllegalArgumentException("Adapter does not match the exposed coupling model");
            thickness += item.thickness;
        }
        if (items.size() == 1) return items.getFirst();
        return new CouplerAdapter(items.getFirst().itemId, baseModel, exposed, thickness, items);
    }

    public static CouplerAdapter append(String baseModel, CouplerAdapter installed, CouplerAdapter next) {
        var items = new java.util.ArrayList<CouplerAdapter>();
        if (installed != null) items.addAll(installed.items());
        items.addAll(next.items());
        return stack(baseModel, items);
    }

    public CouplerAdapter withoutOuter(String baseModel) { return stack(baseModel, items().subList(0, items().size() - 1)); }
    public CouplerAdapter outer() { return items().getLast(); }

    /** Local north remains outward. Reversing the rigid adapter changes its interface height sign. */
    public CouplingEndpoint.Point offsetFrom(String mountedModel) {
        if (firstModel.equals(mountedModel)) return offset;
        if (secondModel.equals(mountedModel)) return new CouplingEndpoint.Point(offset.x(), -offset.y(), offset.z());
        throw new IllegalArgumentException("Adapter does not match its mounting interface");
    }

    private static CouplingEndpoint.Point offsetOf(String baseModel, java.util.List<CouplerAdapter> layers) {
        var result = new CouplingEndpoint.Point(0, 0, 0);
        String model = baseModel;
        for (var layer : layers) {
            result = result.plus(layer.offsetFrom(model));
            model = layer.otherModel(model);
        }
        return result;
    }

    /** Returns the exposed model when mounted on either compatible side, otherwise null. */
    public String otherModel(String model) {
        if (firstModel.equals(model)) return secondModel;
        if (secondModel.equals(model)) return firstModel;
        return null;
    }

    static void requireIdentifier(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + label);
    }
}
