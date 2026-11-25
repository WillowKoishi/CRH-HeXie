package willow.train.hexie.interaction.spatial.model;

import java.util.UUID;

/** A cell address in exactly one world dimension or carriage-local space. */
public sealed interface SurfaceHostId {
    Cell cell();
    SurfaceHostId at(Cell cell);

    record World(Id dimensionId, Cell cell) implements SurfaceHostId {
        @Override public World at(Cell cell) {
            return new World(dimensionId, cell);
        }
    }

    record Carriage(UUID carriageId, Cell cell) implements SurfaceHostId {
        @Override public Carriage at(Cell cell) {
            return new Carriage(carriageId, cell);
        }
    }
}
