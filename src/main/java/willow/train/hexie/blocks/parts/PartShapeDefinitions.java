package willow.train.hexie.blocks.parts;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.blocks.base.DirectionalShape;

/** Stable authored shape variants shared by old registered block classes. Values are model pixels. */
public final class PartShapeDefinitions {
    private static final Map<String,Map<Direction,VoxelShape>> SHAPES=load();
    private PartShapeDefinitions() {}
    public static VoxelShape get(String name,Direction facing) {
        var shape=SHAPES.get(name);
        if(shape==null)throw new IllegalArgumentException("Unknown vehicle part shape: "+name);
        return shape.get(facing);
    }
    private static VoxelShape boxes(JsonArray data) {
        VoxelShape shape=Shapes.empty();
        for(var element:data) {
            var b=element.getAsJsonArray();
            shape=Shapes.or(shape,Block.box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));
        }
        return shape.optimize();
    }
    private static Map<String,Map<Direction,VoxelShape>> load() {
        var result=new HashMap<String,Map<Direction,VoxelShape>>();
        try(var stream=PartShapeDefinitions.class.getResourceAsStream("/data/hexie/vehicle_part_shapes.json")) {
            if(stream==null)throw new IllegalStateException("Missing vehicle part shape definitions");
            for(var entry:JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
                var data=entry.getValue().getAsJsonObject();var rotation=new DirectionalShape(boxes(data.getAsJsonArray("north")));
                var directions=new EnumMap<Direction,VoxelShape>(Direction.class);
                for(var facing:Direction.Plane.HORIZONTAL)directions.put(facing,data.has(facing.getName())?boxes(data.getAsJsonArray(facing.getName())):rotation.get(facing));
                result.put(entry.getKey(),Map.copyOf(directions));
            }
        } catch(java.io.IOException failure) { throw new ExceptionInInitializerError(failure); }
        return Map.copyOf(result);
    }
}
