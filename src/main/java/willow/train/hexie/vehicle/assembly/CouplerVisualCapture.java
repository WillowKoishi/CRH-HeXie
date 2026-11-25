package willow.train.hexie.vehicle.assembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry.Definition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

// split from willow/train/hexie/vehicle/assembly/CouplerDataRegistry.java
final class CouplerVisualCapture {
    static CouplingEndpoint.Visual capture(CouplerBlockBridge.CouplerBlockView hook, BlockState state) {
        var moving = hook instanceof Definition definition ? renderState(definition.movingState()) : null;
        var offset = hook instanceof Definition definition ? definition.movingOffset() : new Point3(0, 0, 0);
        return new CouplingEndpoint.Visual(CouplingBridge.CouplingEndpointFacing.valueOf(hook.facing(state).name()),
                renderState(hook.carriageRenderState(state, false)), renderState(hook.carriageRenderState(state, true)),
                moving, hook.axis().atHeight(hook.complete().y()), offset);
    }

    private static CouplingEndpoint.RenderState renderState(BlockState state) {
        var values = new java.util.TreeMap<String, String>();
        for (var property : state.getProperties()) values.put(property.getName(), CouplerDataRegistry.propertyValue(state, property));
        return new CouplingEndpoint.RenderState(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), values);
    }

    /** Resolves only the captured block identity/properties; never consults the active coupler definitions. */
    static BlockState renderState(CouplingEndpoint.RenderState saved) {
        var id = ResourceLocation.parse(saved.block());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown coupler render block " + id);
        return CouplerDataRegistry.applyProperties(BuiltInRegistries.BLOCK.get(id).defaultBlockState(), saved.properties());
    }
}
