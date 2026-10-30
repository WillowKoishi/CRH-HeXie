package willow.train.hexie.client.events;

import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Supplements Create's cell walk only for hook shapes shifted vertically out of their source cell. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class ShiftedCouplerInteraction {
    private ShiftedCouplerInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isSpectator() || minecraft.level == null) return;
        // Keep native reach and ordinary world/entity obstruction truncation.
        var ray = ContraptionHandlerClient.getRayInputs(player);
        var origin = ray.getFirst();
        var target = ray.getSecond();
        var candidates = new AABB(origin, target).inflate(16);
        var loaded = ContraptionHandler.loadedContraptions.get(minecraft.level).values();
        SnapshotCarriageEntity bestEntity = null;
        BlockHitResult bestHit = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var reference : loaded) {
            if (!(reference.get() instanceof SnapshotCarriageEntity carriage) || carriage.isRemoved()
                    || carriage.couplerAnimation().floorOffset() == 0
                    || !carriage.getBoundingBox().intersects(candidates)) continue;
            var contraption = carriage.getContraption();
            if (contraption == null) continue;
            var localOrigin = carriage.toLocalVector(origin, 1);
            var localTarget = carriage.toLocalVector(target, 1);
            var world = contraption.getContraptionWorld();
            for (var hook : carriage.couplerAnimation().frames()) {
                var position = hook.position();
                var block = contraption.getBlocks().get(position);
                if (block == null || !(block.state().getBlock() instanceof CouplerBlock)
                        || contraption.isHiddenInPortal(position)) continue;
                var hit = block.state().getShape(world, position).clip(localOrigin, localTarget, position);
                if (hit == null) continue;
                double distance = carriage.toGlobalVector(hit.getLocation(), 1).distanceToSqr(origin);
                if (distance >= bestDistance) continue;
                bestEntity = carriage;
                bestHit = hit;
                bestDistance = distance;
            }
        }
        if (bestHit == null) return;
        // Only intercept an extra hook hit in front of every ordinary contraption hit. Native clicks keep their path.
        for (var reference : loaded) {
            var entity = reference.get();
            if (entity == null || entity.isRemoved() || entity.getContraption() == null
                    || !entity.getBoundingBox().intersects(candidates)) continue;
            var hit = ContraptionHandlerClient.rayTraceContraption(origin, target, entity);
            if (hit != null && entity.toGlobalVector(hit.getLocation(), 1).distanceToSqr(origin) <= bestDistance + 1e-8)
                return;
        }
        var hand = event.getHand();
        if (!bestEntity.handlePlayerInteraction(player, bestHit.getBlockPos(), bestHit.getDirection(), hand)) return;
        CatnipServices.NETWORK.sendToServer(new ContraptionInteractionPacket(bestEntity, hand,
                bestHit.getBlockPos(), bestHit.getDirection()));
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
