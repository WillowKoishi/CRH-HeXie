package dev.hexie.renderer.mc;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Integer UI units are explicit for fractional controls. A zero always survives persistence. */
public record RenderSettings(Preset preset, Map<Field,Integer> values, boolean fullQuality) {
    public enum Preset { LOW, MEDIUM, HIGH }
    public enum Tier { BASIC, FULL, IRIS }
    public enum Field {
        POINT_LIGHTS(1,1,1), TILE_PIXELS(32,16,16), DEPTH_LAYERS(16,24,24), PRIORITY_LIGHTS(2,4,8),
        OWN_MIB(24,96,192), OWN_MIN_RES(64,128,128), OWN_MAX_RES(128,256,512),
        FOREIGN_CUBES(4,16,24), FOREIGN_RES(128,256,256), MAP_FACES(12,30,60),
        EXACT_RAYS(32768,81920,163840), DDA_STEPS(2097152,4194304,8388608),
        ACTIVE_LIGHTS(2048,4096,8192), CLUSTER_INDICES(262144,1048576,4194304),
        CELL_WIDTH_1024(512,512,256), CSM_LEVELS(2,3,4), CSM_RES(1024,1536,2048),
        CSM_DISTANCE(48,80,128), GPU_MICROSECONDS(3500,5000,6000), MEMORY_MIB(256,512,1024);

        private final int[] defaults;
        Field(int low,int medium,int high) { defaults = new int[]{low,medium,high}; }
        public int defaultValue(Preset preset) { return defaults[preset.ordinal()]; }
    }

    public RenderSettings {
        Objects.requireNonNull(preset);
        var copy = new EnumMap<Field,Integer>(Field.class);
        for (var field : Field.values()) {
            int value = values.getOrDefault(field,field.defaultValue(preset));
            if (value < 0 || field == Field.POINT_LIGHTS && value > 1)
                throw new IllegalArgumentException(field.name());
            copy.put(field,value);
        }
        values = Map.copyOf(copy);
    }
    public static RenderSettings of(Preset p) { return new RenderSettings(p,Map.of(),false); }
    public RenderSettings with(Field field,int value) {
        var copy = new EnumMap<>(values);
        copy.put(field,value);
        return new RenderSettings(preset,copy,fullQuality);
    }
    public int get(Field field) { return values.get(field); }
    public int effective(Field field,Tier tier) {
        if (tier != Tier.FULL || get(Field.MEMORY_MIB) == 0) return 0;
        return switch (field) {
            case MEMORY_MIB, GPU_MICROSECONDS, MAP_FACES -> get(field);
            case CSM_LEVELS, CSM_RES, CSM_DISTANCE -> get(Field.CSM_LEVELS)* (long)get(Field.CSM_RES) == 0
                    || get(Field.CSM_DISTANCE)==0 || get(Field.MAP_FACES)==0 ? 0 : get(field);
            default -> {
                if (get(Field.POINT_LIGHTS)==0 || get(Field.ACTIVE_LIGHTS)==0) yield 0;
                yield switch (field) {
                    case FOREIGN_CUBES, FOREIGN_RES -> get(Field.FOREIGN_RES)==0 || get(Field.FOREIGN_CUBES)==0
                            || get(Field.MAP_FACES)<6 ? 0 : get(field);
                    case OWN_MIB, OWN_MIN_RES, OWN_MAX_RES -> get(Field.OWN_MIB)==0 || get(Field.OWN_MIN_RES)==0
                            || get(Field.OWN_MAX_RES)==0 || get(Field.MAP_FACES)<6 ? 0 : get(field);
                    case DDA_STEPS, CELL_WIDTH_1024 -> get(Field.DDA_STEPS)==0 || get(Field.CELL_WIDTH_1024)==0 ? 0 : get(field);
                    case TILE_PIXELS, DEPTH_LAYERS, CLUSTER_INDICES -> get(Field.TILE_PIXELS)==0 || get(Field.DEPTH_LAYERS)==0
                            || get(Field.CLUSTER_INDICES)==0 ? 0 : get(field);
                    default -> get(field);
                };
            }
        };
    }
    public static RenderSettings load(Path path) throws IOException {
        if (!Files.exists(path)) return of(Preset.MEDIUM);
        try (Reader reader=Files.newBufferedReader(path)) {
            return Objects.requireNonNull(new Gson().fromJson(reader,RenderSettings.class));
        }
    }
    public void save(Path path) throws IOException {
        Path dir=path.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp=Files.createTempFile(dir,"hexie-render-",".tmp");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(this));
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
