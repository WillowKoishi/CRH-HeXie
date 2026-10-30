package willow.train.hexie.compat.create;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.item.CouplerAdapterItem;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.track.TrackGeometry.Vector;

/** Explicit two-click shunting of the verification coupler, through the existing interaction packet. */
final class SnapshotCouplerInteraction {
    private static final String SELECTION = "HexieSelectedCoupler";
    private static final double MAX_DISTANCE = 8;

    private SnapshotCouplerInteraction() {}

    static boolean handle(SnapshotCarriageEntity entity, Player player, BlockPos position, InteractionHand hand) {
        var info = entity.getContraption() == null ? null : entity.getContraption().getBlocks().get(position);
        if (info == null || CouplerBlock.of(info.state()) == null
                || player.isSpectator() || !player.mayBuild() || !entity.canInteractWithBlock(player, position, 4))
            return false;
        // Keep the existing wrench relocation available on this block too.
        if (player.getItemInHand(hand).is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)) return false;
        if (entity.level().isClientSide) return true;
        var motion = entity.motion();
        if (motion == null) return true;
        UUID endpoint = VehicleCouplingCatalog.endpointId(entity.getUUID(), position);
        var runtime = motion.runtime();
        if (!runtime.placement().endpoints().containsKey(endpoint)) return true;
        var data = player.getPersistentData();
        var level = (ServerLevel) entity.level();
        try {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof CouplerAdapterItem converter) {
                var adapter = converter.adapter(held);
                if (adapter == null) throw new IllegalArgumentException("转换器参数无效");
                CompoundTag blockData = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
                if (!blockData.contains("id")) blockData.putString("id", "hexie:coupler");
                runtime.installCouplerAdapter(endpoint, adapter);
                CouplerAdapterData.write(blockData, runtime.definition(entity.getUUID()).couplings().stream()
                        .filter(e -> e.id().equals(endpoint)).findFirst().orElseThrow().adapter());
                entity.setBlock(position, new StructureBlockInfo(position, info.state(), blockData));
                motion.applyCurrent(entity);
                entity.syncCarriage();
                data.remove(SELECTION);
                if (!player.getAbilities().instabuild) held.shrink(1);
                return true;
            }
            if (player.isShiftKeyDown() && held.isEmpty()) {
                var removed = runtime.removeCouplerAdapter(endpoint);
                if (removed == null) return true;
                CompoundTag blockData = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
                if (!blockData.contains("id")) blockData.putString("id", "hexie:coupler");
                CouplerAdapterData.write(blockData, runtime.definition(entity.getUUID()).couplings().stream()
                        .filter(e -> e.id().equals(endpoint)).findFirst().orElseThrow().adapter());
                entity.setBlock(position, new StructureBlockInfo(position, info.state(), blockData));
                motion.applyCurrent(entity);
                entity.syncCarriage();
                data.remove(SELECTION);
                var item = CouplerAdapterItem.stack(removed);
                if (!player.getInventory().add(item)) player.drop(item, false);
                return true;
            }
            for (var state : runtime.couplingStates()) {
                if (!state.first().equals(endpoint) && !state.second().equals(endpoint)) continue;
                data.remove(SELECTION);
                if (state.connected()) {
                    runtime.uncouple(endpoint);
                    message(player, "已解挂");
                } else {
                    runtime.cancelCoupling(endpoint);
                    message(player, "已取消挂接");
                }
                return true;
            }
            CompoundTag selected = data.getCompound(SELECTION);
            long age = level.getGameTime() - selected.getLong("Time");
            if (!selected.hasUUID("Carriage") || !selected.hasUUID("Endpoint") || age < 0 || age > 600
                    || !selected.getString("Dimension").equals(level.dimension().location().toString())) {
                selected = new CompoundTag();
                selected.putUUID("Carriage", entity.getUUID());
                selected.putUUID("Endpoint", endpoint);
                selected.putLong("Time", level.getGameTime());
                selected.putString("Dimension", level.dimension().location().toString());
                data.put(SELECTION, selected);
                message(player, "已选中车钩");
                return true;
            }
            data.remove(SELECTION);
            UUID firstId = selected.getUUID("Endpoint");
            if (firstId.equals(endpoint)) { message(player, "已取消选择"); return true; }
            if (!(level.getEntity(selected.getUUID("Carriage")) instanceof SnapshotCarriageEntity first)
                    || first.isRemoved()) {
                message(player, "先前选择的车钩已不在附近");
                return true;
            }
            var firstMotion = first.motion();
            if (firstMotion == null) { message(player, "先前选择的车辆尚未恢复"); return true; }
            var firstRuntime = firstMotion.runtime();
            var a = firstRuntime.placement().endpoints().get(firstId);
            var b = runtime.placement().endpoints().get(endpoint);
            if (a == null) return true;
            Vector separation = b.complete().subtract(a.complete());
            if (separation.dot(separation) > MAX_DISTANCE * MAX_DISTANCE) {
                message(player, "两车钩相距超过8格");
                return true;
            }
            var definition = firstRuntime.definition(first.getUUID()).couplings().stream()
                    .filter(candidate -> candidate.id().equals(firstId)).findFirst().orElseThrow();
            var facing = firstRuntime.placement().bodies().get(first.getUUID())
                    .rotate(definition.rotated(new Point(0, 0, -1)));
            if (separation.dot(facing) < -1e-5) {
                message(player, "目标不在车钩前方");
                return true;
            }
            var obstruction = level.clip(new ClipContext(vector(a.complete()), vector(b.complete()),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (obstruction.getType() != HitResult.Type.MISS) {
                message(player, "两车钩之间有方块阻挡");
                return true;
            }
            firstMotion.beginCoupling(first.getUUID(), firstId, motion, entity.getUUID(), endpoint, .5);
            message(player, "开始挂接");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            message(player, "无法挂接：" + failure.getMessage());
        }
        return true;
    }

    private static Vec3 vector(Vector point) { return new Vec3(point.x(), point.y(), point.z()); }
    private static void message(Player player, String text) { player.displayClientMessage(Component.literal(text), true); }
}
