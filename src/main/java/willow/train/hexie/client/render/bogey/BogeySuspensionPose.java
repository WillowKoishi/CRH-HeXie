package willow.train.hexie.client.render.bogey;

import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.init.track.TrackMaterialInit;

/** Mechanical top coordinates, excluding configured floor height, shared by both render paths. */
public final class BogeySuspensionPose {
    public static final float MODEL_ORIGIN_Y = (float) (-1.5 - 1 / 128d + TrackMaterialInit.RAIL_HEIGHT_INCREASE);
    private static final ThreadLocal<Matrix4fc> CURRENT = new ThreadLocal<>();

    private BogeySuspensionPose() {}

    /** Both inputs are block-centred mechanical poses, before BogeyStyle's model-origin translation. */
    public static Matrix4f relative(Vector3fc bogeyPosition, Quaternionfc bogeyRotation,
                                   Vector3fc topPosition, Quaternionfc topRotation) {
        Matrix4f bogey = new Matrix4f().translation(bogeyPosition).rotate(bogeyRotation)
                .translate(0, MODEL_ORIGIN_Y, 0);
        Matrix4f top = new Matrix4f().translation(topPosition).rotate(topRotation)
                .translate(0, MODEL_ORIGIN_Y, 0);
        return bogey.invert().mul(top);
    }

    public static Scope push(Matrix4fc bodyInBogey) {
        Matrix4fc previous = CURRENT.get();
        CURRENT.set(bodyInBogey);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final Matrix4fc previous;
        private Scope(Matrix4fc previous) { this.previous = previous; }
        @Override public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** Convert the body attachment into the exported frame's coordinate system. */
    static Matrix4f inModelSpace(CompoundTag data, double frameOffset) {
        Matrix4fc current = CURRENT.get();
        // A floor setting moves the carriage floor, not the suspension's resting attachment points.
        Matrix4f relative = current == null ? new Matrix4f() : new Matrix4f(current);
        // SimpleBogeyRenderer mirrors its whole input pose; conjugate the relative transform too.
        if (data.getBoolean(BogeyFloorData.REVERSED))
            relative = new Matrix4f().scaling(1, 1, -1).mul(relative).scale(1, 1, -1);
        return new Matrix4f().translation(0, (float) -frameOffset, 0)
                .mul(relative).translate(0, (float) frameOffset, 0);
    }
}
