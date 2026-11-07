package willow.train.hexie.api.interaction;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/** A server-validated operation; the hit remains in the target's storage coordinates. */
public record BlockControlUse(BlockControlContext target, Player player, InteractionHand hand, BlockHitResult hit) {}
