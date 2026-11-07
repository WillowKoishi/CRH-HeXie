package willow.train.hexie.api.interaction;

import net.minecraft.world.phys.BlockHitResult;

/** The selected registered control and its hit in the containing storage coordinate system. */
public record BlockControlHit(BlockControl control, BlockHitResult blockHit) {}
