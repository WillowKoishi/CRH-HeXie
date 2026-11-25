package willow.train.hexie.compat.create.block;

import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.interaction.HexieTransformableBlockEntity;

public final class CreateHeadTransformCompat {
    private CreateHeadTransformCompat() {}

    public static void register() {
        MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(
                HexieBridges.worldRegistry().crh2aHeadBlockEntity(), (entity, transform) -> {
                    if (entity instanceof HexieTransformableBlockEntity head) {
                        head.transformLongitudinal(transform::unapplyWithoutOffset, transform::applyWithoutOffsetUncentered);
                    }
                });
    }
}
