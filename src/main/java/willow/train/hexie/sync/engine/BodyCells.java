package willow.train.hexie.sync.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import willow.train.hexie.bridge.sync.VehicleSyncData.Cell;
import willow.train.hexie.bridge.sync.VehicleSyncData.Id;
import willow.train.hexie.bridge.sync.VehicleSyncData.BlockData;

/** Lossless cell payload of a Body. Entity data stays in the adapter's original serialized form. */
public final class BodyCells {
    private BodyCells() {}

    public static byte[] encode(Map<Cell, BlockData> cells) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(cells.size());
            for (var entry : cells.entrySet()) {
                var cell = entry.getKey();
                out.writeInt(cell.x());
                out.writeInt(cell.y());
                out.writeInt(cell.z());
                var data = entry.getValue();
                write(out, data.blockId().namespace());
                write(out, data.blockId().path());
                out.writeInt(data.properties().size());
                for (var property : data.properties().entrySet()) {
                    write(out, property.getKey());
                    write(out, property.getValue());
                }
                out.writeBoolean(data.blockEntityData().isPresent());
                if (data.blockEntityData().isPresent()) write(out, data.blockEntityData().get());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static Map<Cell, BlockData> decode(byte[] payload) {
        var cells = new HashMap<Cell, BlockData>();
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = in.readInt();
            if (count < 0) throw new IOException("Negative cell count");
            for (int i = 0; i < count; i++) {
                var cell = new Cell(in.readInt(), in.readInt(), in.readInt());
                var id = new Id(read(in), read(in));
                var properties = new HashMap<String, String>();
                int size = in.readInt();
                if (size < 0) throw new IOException("Negative property count");
                for (int j = 0; j < size; j++) properties.put(read(in), read(in));
                var entity = in.readBoolean() ? Optional.of(read(in)) : Optional.<String>empty();
                cells.put(cell, new BlockData(id, properties, entity));
            }
            if (in.read() != -1) throw new IOException("Trailing cell data");
            return Map.copyOf(cells);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid carriage cells", e);
        }
    }

    private static void write(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String read(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > in.available()) throw new EOFException("Invalid string length");
        return new String(in.readNBytes(size), StandardCharsets.UTF_8);
    }
}
