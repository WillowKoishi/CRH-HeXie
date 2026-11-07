package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.StructureTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;
import willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

/** Mirrors a portable copy; neither the source structure nor live world entities are mutated. */
final class CarriageStructureTransform {
    record Result(CarriageSnapshot snapshot, CompoundTag working) {}

    private CarriageStructureTransform() {}

    /** A copied vehicle retains its resting animation, never the source's motion or coupling identity. */
    static CompoundTag copyAnimation(UUID carriageId, CompoundTag original, Mirror mirror) {
        CompoundTag data = original.copy();
        boolean couplers = data.contains("Hooks", Tag.TAG_LIST);
        var transform = new StructureTransform(BlockPos.ZERO, Direction.Axis.Y, Rotation.NONE, mirror);
        for (Tag value : data.getList("Supports", Tag.TAG_COMPOUND)) {
            var frame = (CompoundTag) value;
            frame.putLong("Pos", transform.apply(BlockPos.of(frame.getLong("Pos"))).asLong());
            int[] packed = frame.getIntArray("Pose");
            if (packed.length != 8) continue;
            float[] pose = new float[8];
            for (int i = 0; i < pose.length; i++) pose[i] = Float.intBitsToFloat(packed[i]);
            if (mirror != Mirror.NONE) {
                int axis = mirror == Mirror.FRONT_BACK ? 0 : 2;
                pose[axis] = 1 - pose[axis];
                // F * R * F: a quaternion's axial vector transforms by det(F) * F.
                var rotation = new Quaternionf(pose[3], pose[4], pose[5], pose[6]);
                rotation.y = -rotation.y;
                if (mirror == Mirror.FRONT_BACK) rotation.z = -rotation.z;
                else rotation.x = -rotation.x;
                // Coupler arms have a canonical north-facing model, reflected across its local X axis.
                if (couplers && mirror == Mirror.LEFT_RIGHT) rotation.rotateY((float) Math.PI);
                pose[3] = rotation.x; pose[4] = rotation.y; pose[5] = rotation.z; pose[6] = rotation.w;
            }
            pose[7] = 0;
            for (int i = 0; i < pose.length; i++) packed[i] = Float.floatToIntBits(pose[i]);
            frame.putIntArray("Pose", packed);
        }
        for (Tag value : data.getList("Hooks", Tag.TAG_COMPOUND)) {
            var hook = (CompoundTag) value;
            BlockPos target = transform.apply(BlockPos.of(hook.getLong("Pos")));
            hook.putLong("Pos", target.asLong());
            hook.putUUID("Id", VehicleCouplingCatalog.endpointId(carriageId, target));
            hook.remove("PartnerPose");
            hook.putBoolean("Coupled", false);
            hook.putDouble("Extension", 0);
            if (mirror == Mirror.NONE) continue;
            hook.putDouble("Yaw", -hook.getDouble("Yaw"));
            if (hook.contains("Visual", Tag.TAG_COMPOUND)) {
                var visual = VehicleRuntimeNbt.readCouplerVisual(hook.getCompound("Visual"));
                Direction facing = mirror.mirror(Direction.valueOf(visual.facing().name()));
                hook.put("Visual", VehicleRuntimeNbt.writeCouplerVisual(new CouplingEndpoint.Visual(
                        CouplingEndpoint.Facing.valueOf(facing.name()), mirrorState(visual.fixed(), mirror),
                        mirrorState(visual.shiftedFixed(), mirror),
                        visual.moving() == null ? null : mirrorState(visual.moving(), Mirror.FRONT_BACK),
                        mirrorModelPoint(visual.origin()), mirrorModelPoint(visual.movingOffset()))));
            }
            if (hook.contains("Adapter", Tag.TAG_COMPOUND)) {
                var adapter = hook.getCompound("Adapter");
                for (String key : new String[] {"Pivot", "Complete"}) {
                    var point = adapter.getCompound(key);
                    point.putDouble("X", -point.getDouble("X"));
                }
            }
        }
        return data;
    }

    private static CouplingEndpoint.Point mirrorModelPoint(CouplingEndpoint.Point point) {
        return new CouplingEndpoint.Point(-point.x(), point.y(), point.z());
    }

    private static CouplingEndpoint.RenderState mirrorState(CouplingEndpoint.RenderState original, Mirror mirror) {
        var state = CouplerDataRegistry.renderState(original).mirror(mirror);
        var properties = NbtUtils.writeBlockState(state).getCompound("Properties");
        var values = new TreeMap<String, String>();
        for (String key : properties.getAllKeys()) values.put(key, properties.getString(key));
        return new CouplingEndpoint.RenderState(original.block(), values);
    }

    static Result mirror(Level level, CarriageSnapshot snapshot, CompoundTag working, Mirror mirror) {
        if (mirror == Mirror.NONE) return new Result(snapshot, working.copy());
        var local = new StructureTransform(BlockPos.ZERO, Direction.Axis.Y, Rotation.NONE, mirror);
        var absolute = new StructureTransform(snapshot.origin().subtract(local.apply(snapshot.origin())),
                Direction.Axis.Y, Rotation.NONE, mirror);
        Level copies = new SchematicLevel(level);
        var blocks = new ArrayList<CarriageSnapshot.Block>(snapshot.blocks().size());
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (var block : snapshot.blocks()) {
            states.put(block.position(), block.state());
            BlockPos target = local.apply(block.position());
            BlockState state = local.apply(block.state());
            var data = block.entityData();
            if (data != null) data = blockEntity(copies, absolute, snapshot.origin().offset(target), state, data);
            blocks.add(new CarriageSnapshot.Block(target, state, data));
        }
        var result = new CarriageSnapshot(snapshot.id(), snapshot.dimension(), snapshot.origin(), blocks,
                snapshot.supports().stream().map(local::apply).toList(), snapshot.glue().stream()
                .map(box -> new AABB(local.apply(new Vec3(box.minX, box.minY, box.minZ)),
                        local.apply(new Vec3(box.maxX, box.maxY, box.maxZ)))).toList());
        CompoundTag data = working.copy();
        for (Tag value : data.getList("ChangedBlocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) value;
            BlockPos source = NBTHelper.readBlockPos(block, "Pos");
            BlockPos target = local.apply(source);
            var state = CarriageSnapshot.readState(block.getCompound("State"), level.registryAccess());
            states.put(source, state);
            var transformed = local.apply(state);
            block.put("Pos", NbtUtils.writeBlockPos(target));
            block.put("State", NbtUtils.writeBlockState(transformed));
            if (block.contains("BlockEntity", Tag.TAG_COMPOUND))
                block.put("BlockEntity", blockEntity(copies, local, snapshot.origin().offset(target), transformed,
                        block.getCompound("BlockEntity")));
        }
        for (Tag value : data.getList("Actors", Tag.TAG_COMPOUND)) {
            var actor = (CompoundTag) value;
            BlockPos source = NBTHelper.readBlockPos(actor, "Pos");
            BlockPos target = local.apply(source);
            actor.put("Pos", NbtUtils.writeBlockPos(target));
            if (actor.contains("BlockEntity", Tag.TAG_COMPOUND)) {
                var state = local.apply(Objects.requireNonNull(states.get(source), "Unknown portable actor"));
                actor.put("BlockEntity", blockEntity(copies, local, snapshot.origin().offset(target), state,
                        actor.getCompound("BlockEntity")));
            }
        }
        for (String key : new String[] {"items", "fluids"})
            for (Tag value : data.getList(key, Tag.TAG_COMPOUND)) {
                var storage = (CompoundTag) value;
                storage.put("pos", NbtUtils.writeBlockPos(local.apply(NBTHelper.readBlockPos(storage, "pos"))));
            }
        // Passengers remain with the original vehicle; their identities are not part of a copy.
        data.put("Passengers", new ListTag());
        return new Result(result, data);
    }

    private static CompoundTag blockEntity(Level copies, StructureTransform transform, BlockPos target,
                                            BlockState state, CompoundTag original) {
        var data = original.copy();
        data.putInt("x", target.getX()); data.putInt("y", target.getY()); data.putInt("z", target.getZ());
        var entity = BlockEntity.loadStatic(target, state, data, copies.registryAccess());
        if (entity == null) throw new IllegalArgumentException("Could not mirror portable block entity at " + target);
        entity.setLevel(copies);
        var before = entity.saveWithFullMetadata(copies.registryAccess());
        transform.apply(entity);
        // A BE need not understand the mounted actor/cab fields stored alongside its own data.
        // Apply only the transform's changes so a load/save cannot discard those fields.
        applyChanges(data, before, entity.saveWithFullMetadata(copies.registryAccess()));
        // Create's captured multi-block links are explicit positions, not block-state properties.
        for (String key : new String[] {"Controller", "LastKnownPos"})
            if (original.contains(key, Tag.TAG_INT_ARRAY))
                data.put(key, NbtUtils.writeBlockPos(transform.apply(NBTHelper.readBlockPos(original, key))));
        return data;
    }

    private static void applyChanges(CompoundTag target, CompoundTag before, CompoundTag after) {
        for (String key : before.getAllKeys()) if (!after.contains(key)) target.remove(key);
        for (String key : after.getAllKeys()) {
            Tag value = after.get(key);
            if (Objects.equals(value, before.get(key))) continue;
            if (before.get(key) instanceof CompoundTag oldValue && value instanceof CompoundTag newValue
                    && target.get(key) instanceof CompoundTag targetValue)
                applyChanges(targetValue, oldValue, newValue);
            else target.put(key, value.copy());
        }
    }
}
