package willow.train.hexie.compat.create.interaction;

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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.CouplerAdapterItemView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.railway.TrackGeometryView.Vector;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2EndFaceBlockView;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.core.api.vehicle.Point3;

/** Explicit two-click shunting of the verification coupler, through the existing interaction packet. */
public final class SnapshotCouplerInteraction {
    private static final String SELECTION = "HexieSelectedCoupler";
    private static final double MAX_DISTANCE = 8;

    private SnapshotCouplerInteraction() {}

    public static boolean handle(SnapshotCarriageEntity entity, Player player, BlockPos position, InteractionHand hand) {
        var info = entity.getContraption() == null ? null : entity.getContraption().getBlocks().get(position);
        UUID endpoint = HexieBridges.coupling().vehicleCouplingCatalog().endpointId(entity.getUUID(), position);
        if (info == null || !entity.hasCoupler(position)
                || player.isSpectator() || !player.mayBuild() || !entity.canInteractWithBlock(player, position, 4))
            return false;
        // Keep the existing wrench relocation available on this block too.
        if (player.getItemInHand(hand).is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)) return false;
        // This wrench changes only the end-face appearance through its moving interaction behaviour.
        if (info.state().getBlock() instanceof CRH2EndFaceBlockView
                && player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView) return false;
        if (entity.level().isClientSide) return true;
        if (HexieBridges.partBlock().couplerInteraction().hasGeometry(info.state())) {
            var hit = SnapshotCouplerModelInteraction.classify(entity, player, position);
            // The owner cell is not an interaction target: only a visible model surface is.
            if (hit == null) return true;
            if (!hit.head()) {
                if (HexieBridges.couplerBlock().couplerBlock().of(info.state()).geometry().hasCovers())
                    SnapshotCouplerModelInteraction.toggle(entity, info);
                return true;
            }
        }
        var motion = entity.motion();
        if (motion == null) return true;
        var runtime = motion.runtime();
        if (!runtime.placement().endpoints().containsKey(endpoint)) return true;
        var hook = HexieBridges.couplerBlock().couplerBlock().of(info.state());
        if (hook != null && hook.requiresReadyState()
                && !hook.readyForCoupling(info.state(), info.nbt(), entity.level().getGameTime())) {
            message(player, "车钩尚未完全伸出");
            return true;
        }
        var data = player.getPersistentData();
        var level = (ServerLevel) entity.level();
        try {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof CouplerAdapterItemView converter) {
                if (!HexieBridges.couplerBlock().couplerBlock().of(info.state()).allowsAdapters()) {
                    message(player, "此车钩不支持转换器");
                    return true;
                }
                var adapter = converter.adapter(held);
                if (adapter == null) throw new IllegalArgumentException("转换器参数无效");
                CompoundTag blockData = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
                if (!blockData.contains("id")) blockData.putString("id", "hexie:coupler");
                runtime.installCouplerAdapter(endpoint, adapter);
                HexieBridges.couplerBlock().couplerAdapterData().write(blockData, runtime.definition(entity.getUUID()).couplings().stream()
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
                HexieBridges.couplerBlock().couplerAdapterData().write(blockData, runtime.definition(entity.getUUID()).couplings().stream()
                        .filter(e -> e.id().equals(endpoint)).findFirst().orElseThrow().adapter());
                entity.setBlock(position, new StructureBlockInfo(position, info.state(), blockData));
                motion.applyCurrent(entity);
                entity.syncCarriage();
                data.remove(SELECTION);
                var item = HexieBridges.toolOps().couplerAdapterItem().stack(removed);
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
                    .rotate(definition.rotated(new Point3(0, 0, -1)));
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
