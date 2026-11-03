package willow.train.hexie.blocks.parts;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Packaged part capabilities. IDs remain stable; model geometry is kept in its existing resources. */
public final class PartDefinitions {
    public enum Extension { NONE, SIDE, FLOOR, ROOF, SKIRT }
    public record Definition(Extension extension, boolean reverseFacing, boolean thinOnly) {}
    private static final Definition NONE=new Definition(Extension.NONE,false,false);
    private static volatile Map<ResourceLocation,Definition> DEFINITIONS=load();
    private PartDefinitions() {}
    private static Map<ResourceLocation,Definition> load() {
        var result=new HashMap<ResourceLocation,Definition>();
        try(var stream=PartDefinitions.class.getResourceAsStream("/data/hexie/vehicle_parts.json")) {
            if(stream==null)throw new IllegalStateException("Missing vehicle part definitions");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:json.entrySet()) {
                var value=entry.getValue().getAsJsonObject();
                result.put(ResourceLocation.parse(entry.getKey()),new Definition(Extension.valueOf(value.get("extension").getAsString()),
                        value.has("reverse_facing")&&value.get("reverse_facing").getAsBoolean(),
                        value.has("thin_only")&&value.get("thin_only").getAsBoolean()));
            }
        } catch(java.io.IOException failure) { throw new ExceptionInInitializerError(failure); }
        return Map.copyOf(result);
    }
    /** Register from common setup (enqueueWork), before worlds are opened. Built-in and duplicate IDs cannot be replaced. */
    public static synchronized void register(ResourceLocation id,Definition definition) {
        Objects.requireNonNull(id);Objects.requireNonNull(definition);Objects.requireNonNull(definition.extension());
        if(DEFINITIONS.containsKey(id))throw new IllegalArgumentException("Vehicle part definition already registered: "+id);
        var next=new HashMap<>(DEFINITIONS);next.put(id,definition);DEFINITIONS=Map.copyOf(next);
    }
    public static Definition get(BlockState state) {
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
