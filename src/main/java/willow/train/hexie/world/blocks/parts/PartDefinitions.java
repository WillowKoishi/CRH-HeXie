package willow.train.hexie.world.blocks.parts;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Packaged part capabilities. IDs remain stable; model geometry is kept in its existing resources. */
public final class PartDefinitions {
    public enum Extension { NONE, SIDE, FLOOR, ROOF, SKIRT }
    public enum RunAxis { PERPENDICULAR, FACING }
    public record Definition(PartDefinitions.Extension extension, boolean reverseFacing, boolean thinOnly, PartDefinitions.RunAxis runAxis) {
        public Definition(PartDefinitions.Extension extension, boolean reverseFacing, boolean thinOnly) {
            this(extension, reverseFacing, thinOnly, PartDefinitions.RunAxis.PERPENDICULAR);
        }
    }
    private static final PartDefinitions.Definition NONE=new PartDefinitions.Definition(PartDefinitions.Extension.NONE,false,false,PartDefinitions.RunAxis.PERPENDICULAR);
    private static volatile Map<ResourceLocation,PartDefinitions.Definition> DEFINITIONS=load();
    private PartDefinitions() {}
    private static Map<ResourceLocation,PartDefinitions.Definition> load() {
        var result=new HashMap<ResourceLocation,PartDefinitions.Definition>();
        try(var stream=PartDefinitions.class.getResourceAsStream("/data/hexie/vehicle_parts.json")) {
            if(stream==null)throw new IllegalStateException("Missing vehicle part definitions");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:json.entrySet()) {
                var value=entry.getValue().getAsJsonObject();
                result.put(ResourceLocation.parse(entry.getKey()),new PartDefinitions.Definition(PartDefinitions.Extension.valueOf(value.get("extension").getAsString()),
                        value.has("reverse_facing")&&value.get("reverse_facing").getAsBoolean(),
                        value.has("thin_only")&&value.get("thin_only").getAsBoolean(),
                        value.has("run_axis")?PartDefinitions.RunAxis.valueOf(value.get("run_axis").getAsString()):PartDefinitions.RunAxis.PERPENDICULAR));
            }
        } catch(IOException failure) { throw new ExceptionInInitializerError(failure); }
        return Map.copyOf(result);
    }
    /** Register from common setup (enqueueWork), before worlds are opened. Built-in and duplicate IDs cannot be replaced. */
    public static synchronized void register(ResourceLocation id,PartDefinitions.Definition definition) {
        Objects.requireNonNull(id);Objects.requireNonNull(definition);Objects.requireNonNull(definition.extension());
        if(DEFINITIONS.containsKey(id))throw new IllegalArgumentException("Vehicle part definition already registered: "+id);
        var next=new HashMap<>(DEFINITIONS);next.put(id,definition);DEFINITIONS=Map.copyOf(next);
    }
    public static PartDefinitions.Definition get(BlockState state) {
        var definition=DEFINITIONS.getOrDefault(BuiltInRegistries.BLOCK.getKey(state.getBlock()),NONE);
        if(definition.thinOnly()) {
            var shape=state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
            if(shape.isEmpty()||!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))return NONE;
            var axis=state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
            if(shape.max(axis)-shape.min(axis)>.5)return NONE;
        }
        return definition;
    }
}
