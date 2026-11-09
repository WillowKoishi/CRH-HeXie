package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.interaction.ControlInputTargets;

/** Create's loaded contraption target access; the common input transport knows no carrier type. */
public final class CreateControlInputTargets implements ControlInputTargets.Adapter {
    private CreateControlInputTargets() {}

    public static void register() { ControlInputTargets.registerAdapter(new CreateControlInputTargets()); }

    @Override public boolean supports(Entity carrier) { return carrier instanceof AbstractContraptionEntity; }

    @Override public BlockControlContext world(Entity carrier, BlockPos position, float partialTick) {
        var create = (AbstractContraptionEntity) carrier;
        var contraption = create.getContraption();
        return create.isRemoved() || contraption == null || !contraption.getBlocks().containsKey(position) ? null
                : new BlockControlContext(contraption.getContraptionWorld(), position, create, partialTick);
    }

    @Override public Object container(BlockControlContext target) {
        var carrier = (AbstractContraptionEntity) target.carrier();
        var contraption = carrier.getContraption();
        return !carrier.isRemoved() && contraption != null && contraption.getContraptionWorld() == target.blocks()
                && contraption.getBlocks().containsKey(target.position()) ? contraption : null;
    }

    @Override public BlockEntity blockEntity(BlockControlContext target) {
        if (container(target) == null) return null;
        var carrier = (AbstractContraptionEntity) target.carrier();
        // ContraptionWorld delegates BE access to the underlying world at the local position.
        // Only the client has a live contraption BE; the server owns the saved block data instead.
        return carrier.level().isClientSide
                ? carrier.getContraption().getBlockEntityClientSide(target.position()) : null;
    }

    @Override public Vec3 toStorage(BlockControlContext target, Vec3 local) {
        var carrier = (AbstractContraptionEntity) target.carrier();
        return carrier instanceof SnapshotCarriageEntity snapshot
                ? CarriagePlayerSupport.frame(snapshot, target.partialTick()).world(local)
                : carrier.toGlobalVector(local, target.partialTick());
    }

    @Override public Vec3 toLocal(BlockControlContext target, Vec3 storage) {
        var carrier = (AbstractContraptionEntity) target.carrier();
        return carrier instanceof SnapshotCarriageEntity snapshot
                ? CarriagePlayerSupport.frame(snapshot, target.partialTick()).local(storage)
                : carrier.toLocalVector(storage, target.partialTick());
    }

    @Override public boolean canInteract(BlockControlContext target, Player player, Vec3 localPoint) {
        // Shared transport checks loaded ownership and world permissions. Create's
        // inherited canInteractWithBlock only repeats a distance test in an older pose;
        // surface input uses the client's rendered range for every carrier.
        return true;
    }

    @Override public boolean seatDiscoveryLoaded(Player player, Entity vehicle, BlockPos console) {
        return world(vehicle, console, 1) != null;
    }
}
