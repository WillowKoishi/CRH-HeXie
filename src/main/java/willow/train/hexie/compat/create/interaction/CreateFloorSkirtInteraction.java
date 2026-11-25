package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1FloorBlockView;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.player.CarriagePlayerSupport;

/** Keep the same side-only skirt toggle on captured Create and snapshot carriage floors. */
public final class CreateFloorSkirtInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
            AbstractContraptionEntity entity) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView)
                || player.isSpectator() || !player.mayBuild() || player.level() != entity.level()
                || entity.getContraption() == null || !entity.canInteractWithBlock(player, position, 4))
            return false;
        var contraption = entity.getContraption();
        var info = contraption.getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof CRH1FloorBlockView)
                || info.state().getValue(HexieBridges.carriageBlock().crh2PartBlock().part()) != 0) return false;
        if (player.isShiftKeyDown()) {
            HexieBridges.toolOps().floorSwitcherItem().switchMode(player, hand);
            return true;
        }
        var frame = entity instanceof SnapshotCarriageEntity snapshot ? CarriagePlayerSupport.frame(snapshot, 1) : null;
        var origin = player.getEyePosition();
        var target = origin.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var from = frame == null ? entity.toLocalVector(origin, 1) : frame.local(origin);
        var to = frame == null ? entity.toLocalVector(target, 1) : frame.local(target);
        var hit = info.state().getShape(contraption.getContraptionWorld(), position).clip(from, to, position);
        if (hit == null) return false;
        var skirt = HexieBridges.carriageBlock().crh1FloorBlock().skirtProperty(info.state(), hit.getDirection());
        if (skirt == null) return false;
        var center = Vec3.atCenterOf(position);
        var worldPos = BlockPos.containing(frame == null ? entity.toGlobalVector(center, 1) : frame.world(center));
        if (!entity.level().mayInteract(player, worldPos)
                || !player.mayUseItemAt(worldPos, hit.getDirection(), player.getItemInHand(hand))) return false;
        if (!entity.level().isClientSide)
            entity.setBlock(position, new StructureBlockInfo(position, info.state().cycle(skirt), info.nbt()));
        return true;
    }
}
