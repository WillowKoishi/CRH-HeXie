package willow.train.hexie.vehicle.assembly;

import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

// split from willow/train/hexie/vehicle/assembly/VehicleRuntimeNbt.java
final class CouplerVisualNbt {
    static CompoundTag writeCouplerVisual(CouplingEndpoint.Visual visual) {
        var tag = new CompoundTag();
        tag.putString("Facing", visual.facing().name());
        tag.put("Fixed", renderState(visual.fixed()));
        tag.put("ShiftedFixed", renderState(visual.shiftedFixed()));
        if (visual.moving() != null) tag.put("Moving", renderState(visual.moving()));
        tag.put("Origin", VehicleRuntimeNbt.point(visual.origin()));
        tag.put("Offset", VehicleRuntimeNbt.point(visual.movingOffset()));
        return tag;
    }

    static CouplingEndpoint.Visual readCouplerVisual(CompoundTag tag) {
        return new CouplingEndpoint.Visual(CouplingBridge.CouplingEndpointFacing.valueOf(VehicleRuntimeNbt.string(tag, "Facing")),
                renderState(tag, "Fixed"), renderState(tag, "ShiftedFixed"),
                tag.contains("Moving") ? renderState(tag, "Moving") : null,
                VehicleRuntimeNbt.point(tag, tag.contains("Origin") ? "Origin" : "Pivot"), VehicleRuntimeNbt.point(tag, "Offset"));
    }

    static CompoundTag renderState(CouplingEndpoint.RenderState state) {
        var tag = new CompoundTag();
        tag.putString("Block", state.block());
        var properties = new CompoundTag();
        state.properties().forEach(properties::putString);
        tag.put("Properties", properties);
        return tag;
    }

    static CouplingEndpoint.RenderState renderState(CompoundTag parent, String key) {
        VehicleRuntimeNbt.require(parent, key, Tag.TAG_COMPOUND);
        var tag = parent.getCompound(key);
        VehicleRuntimeNbt.require(tag, "Properties", Tag.TAG_COMPOUND);
        var values = new TreeMap<String, String>();
        var properties = tag.getCompound("Properties");
        for (String name : properties.getAllKeys()) values.put(name, VehicleRuntimeNbt.string(properties, name));
        return new CouplingEndpoint.RenderState(VehicleRuntimeNbt.string(tag, "Block"), values);
    }
}
