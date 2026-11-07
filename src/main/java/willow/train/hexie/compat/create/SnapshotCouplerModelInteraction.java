package willow.train.hexie.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.blocks.coupler.CouplerInteraction;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerGeometry;
import willow.train.hexie.blocks.coupler.CouplerAdapterGeometry;
import willow.train.hexie.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.blocks.coupler.RescueCouplerState;
import willow.train.hexie.blockentity.CouplerBlockEntity;

/** Adapts the independent arm pose and body-mounted cover pose to one model hit. */
public final class SnapshotCouplerModelInteraction {
    private static final String FROM = "HexieCoverFrom", START = "HexieCoverChangedAt";

    private SnapshotCouplerModelInteraction() {}

    public static CouplerGeometry.Hit classify(SnapshotCarriageEntity carriage, Player player,
            BlockPos position) {
        var info = carriage.getContraption().getBlocks().get(position);
        var body = CarriagePlayerSupport.frame(carriage, 1);
        Vec3 eye = player.getEyePosition();
        return clip(carriage, position, info.state(), progress(info, carriage.level().getGameTime()), 1,
                body.local(eye), body.local(eye.add(player.getLookAngle().scale(player.blockInteractionRange()))));
    }

    /** from/to and the returned hit are all in the displayed carriage's local coordinates. */
    public static CouplerGeometry.Hit clip(SnapshotCarriageEntity carriage, BlockPos position,
            BlockState state, float coverProgress, float partial, Vec3 from, Vec3 to) {
        var hook = CouplerBlock.of(state);
        if (hook == null || hook.geometry() == null) return null;
        var geometry = hook.geometry();
        var info = carriage.getContraption().getBlocks().get(position);
        SnapshotCouplerAnimation.AdapterAttachment attachment = null;
        var parameters = carriage.couplerAnimation().parameters();
        for (int i = 0; i < parameters.size(); i++) if (parameters.get(i).position().equals(position)) {
            attachment = carriage.couplerAnimation().parameters(i, partial).attachment();
            break;
        }
        var adapter = attachment != null ? attachment.adapter() : CouplerAdapterData.read(info == null ? null : info.nbt());
        String baseModel = attachment == null ? hook.model()
                : attachment.reversed() ? adapter.secondModel() : adapter.firstModel();
        float rescueProgress = RescueCouplerState.progress(info == null ? null : info.nbt(), carriage.level().getGameTime(), partial);
        if (carriage.level().isClientSide
                && carriage.getContraption().getBlockEntityClientSide(position) instanceof CouplerBlockEntity display)
            rescueProgress = display.rescueKnuckleProgress(partial);
        var complete = hook.complete();
        Vec3 contact = new Vec3(.5 + complete.x(), .5 + complete.y(), .5 + complete.z());
        if (attachment != null) contact = geometry.armOrigin().add(
                attachment.complete().x() - attachment.pivot().x(),
                attachment.complete().y() - attachment.pivot().y(),
                attachment.complete().z() - attachment.pivot().z());
        Vec3 modelFrom = CouplerInteraction.toModel(state, position, from);
        Vec3 modelTo = CouplerInteraction.toModel(state, position, to);
        var result = CouplerInteraction.fromModel(state, position,
                geometry.clipCovers(coverProgress, modelFrom, modelTo));
        // The fixed base shares the registered bogie/coupler height; the covers stay on the body.
        double floor = carriage.couplerAnimation().floorOffset();
        var base = CouplerInteraction.fromModel(state, position,
                geometry.clipBase(modelFrom.add(0, floor, 0), modelTo.add(0, floor, 0)));
        if (base != null) base = new CouplerGeometry.Hit(base.point().add(0, -floor, 0), base.face(), false);
        result = nearer(from, result, base);
        var frame = carriage.couplerAnimation().frameAt(position, partial);
        if (frame != null) {
            var pivot = new Vec3(frame.x(), frame.y(), frame.z());
            Quaternionf rotation = frame.rotation(), inverse = new Quaternionf(rotation).conjugate();
            Vec3 a = rotate(inverse, from.subtract(pivot)).add(geometry.armOrigin());
            Vec3 b = rotate(inverse, to.subtract(pivot)).add(geometry.armOrigin());
            var arm = geometry.clipMoving(coverProgress, a, b);
            arm = nearer(a, arm, adapterHit(adapter, baseModel, rescueProgress, contact, a, b));
            if (arm == null) return result;
            Vec3 normal = rotate(rotation, Vec3.atLowerCornerOf(arm.face().getNormal()));
            arm = new CouplerGeometry.Hit(
                    rotate(rotation, arm.point().subtract(geometry.armOrigin())).add(pivot),
                    Direction.getNearest((float) normal.x, (float) normal.y, (float) normal.z), arm.head(), arm.adapterIndex());
            return nearer(from, result, arm);
        }
        // Before an arm pose arrives, its visible resting mesh follows the same block orientation.
        return nearer(from, result, CouplerInteraction.fromModel(state, position,
                nearer(modelFrom, geometry.clipMoving(coverProgress, modelFrom, modelTo),
                        adapterHit(adapter, baseModel, rescueProgress, contact, modelFrom, modelTo))));
    }

    private static CouplerGeometry.Hit adapterHit(willow.train.hexie.vehicle.runtime.CouplerAdapter adapter,
            String baseModel, float progress, Vec3 contact, Vec3 from, Vec3 to) {
        var hit = CouplerAdapterGeometry.clip(adapter, baseModel, progress, from.subtract(contact), to.subtract(contact));
        return hit == null ? null : new CouplerGeometry.Hit(hit.point().add(contact), hit.face(), true, hit.adapterIndex());
    }

    static void toggle(SnapshotCarriageEntity carriage, StructureBlockInfo info) {
        if (!info.state().hasProperty(BlockStateProperties.OPEN)) return;
        carriage.setBlock(info.pos(), withOpen(info, !info.state().getValue(BlockStateProperties.OPEN),
                carriage.level().getGameTime()));
        carriage.syncCarriage();
    }

    /** Preserve the current displayed pose when a manual or coupling-driven target changes. */
    static StructureBlockInfo withOpen(StructureBlockInfo info, boolean open, long now) {
        if (!info.state().hasProperty(BlockStateProperties.OPEN)
                || info.state().getValue(BlockStateProperties.OPEN) == open) return info;
        CompoundTag data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        if (!data.contains("id")) data.putString("id", "hexie:coupler");
        data.putFloat(FROM, progress(info, now));
        data.putLong(START, now);
        return new StructureBlockInfo(info.pos(), info.state().setValue(BlockStateProperties.OPEN, open), data);
    }

    static float progress(StructureBlockInfo info, long now) {
        float target = CouplerInteraction.coverProgress(info.state());
        var data = info.nbt();
        if (data == null || !data.contains(START) || !data.contains(FROM)) return target;
        float from = Mth.clamp(data.getFloat(FROM), 0, 1);
        return Mth.approach(from, target, Math.max(0, now - data.getLong(START)) * .1f);
    }

    private static Vec3 rotate(Quaternionf rotation, Vec3 point) {
        var result = rotation.transform(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(result.x, result.y, result.z);
    }

    private static CouplerGeometry.Hit nearer(Vec3 origin, CouplerGeometry.Hit first,
            CouplerGeometry.Hit second) {
        if (second == null) return first;
        return first == null || second.point().distanceToSqr(origin) < first.point().distanceToSqr(origin) ? second : first;
    }
}
