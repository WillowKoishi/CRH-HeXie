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
        var frames = carriage.couplerAnimation().frames();
        for (int index = 0; index < frames.size(); index++) {
            if (!frames.get(index).position().equals(position)) continue;
            var frame = carriage.couplerAnimation().frame(index, partial);
            var pivot = new Vec3(frame.x(), frame.y(), frame.z());
            Quaternionf rotation = frame.rotation(), inverse = new Quaternionf(rotation).conjugate();
            var arm = geometry.clipMoving(
                    rotate(inverse, from.subtract(pivot)).add(geometry.armOrigin()),
                    rotate(inverse, to.subtract(pivot)).add(geometry.armOrigin()));
            if (arm == null) return result;
            Vec3 normal = rotate(rotation, Vec3.atLowerCornerOf(arm.face().getNormal()));
            arm = new CouplerGeometry.Hit(
                    rotate(rotation, arm.point().subtract(geometry.armOrigin())).add(pivot),
                    Direction.getNearest((float) normal.x, (float) normal.y, (float) normal.z), arm.head());
            return nearer(from, result, arm);
        }
        // Before an arm pose arrives, its visible resting mesh follows the same block orientation.
        return nearer(from, result, CouplerInteraction.fromModel(state, position,
                geometry.clipMoving(modelFrom, modelTo)));
    }

    static void toggle(SnapshotCarriageEntity carriage, StructureBlockInfo info) {
        if (!info.state().hasProperty(BlockStateProperties.OPEN)) return;
        long now = carriage.level().getGameTime();
        CompoundTag data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        if (!data.contains("id")) data.putString("id", "hexie:coupler");
        data.putFloat(FROM, progress(info, now));
        data.putLong(START, now);
        carriage.setBlock(info.pos(), new StructureBlockInfo(info.pos(), info.state().cycle(BlockStateProperties.OPEN), data));
        carriage.syncCarriage();
    }

    private static float progress(StructureBlockInfo info, long now) {
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
