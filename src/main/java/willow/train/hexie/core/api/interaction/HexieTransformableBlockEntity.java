package willow.train.hexie.core.api.interaction;

import java.util.function.UnaryOperator;
import net.minecraft.world.phys.Vec3;

public interface HexieTransformableBlockEntity {
    void transformLongitudinal(UnaryOperator<Vec3> unapplyWithoutOffset, UnaryOperator<Vec3> applyWithoutOffsetUncentered);
}
