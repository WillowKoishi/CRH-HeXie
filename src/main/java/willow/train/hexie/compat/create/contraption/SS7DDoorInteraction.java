package willow.train.hexie.compat.create.contraption;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;

/** Door operation and mirrored hinge changes on an assembled carriage. */
public final class SS7DDoorInteraction extends MovingInteractionBehaviour {
    private final DoorMovingInteraction doors = new DoorMovingInteraction();

    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                                                     AbstractContraptionEntity entity) {
        if (player.isSpectator() || player.level() != entity.level() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, pos, 4)) return false;
        var contraption = entity.getContraption();
        var clicked = contraption.getBlocks().get(pos);
        if (clicked == null || !clicked.state().is(HexieBridges.worldRegistry().blockInit().ss7dDoor())) return false;
        BlockPos lower = clicked.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        var bottom = contraption.getBlocks().get(lower);
        var top = contraption.getBlocks().get(lower.above());
        if (bottom == null || top == null || !bottom.state().is(clicked.state().getBlock())
                || !top.state().is(clicked.state().getBlock())
                || bottom.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || !bottom.state().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).equals(top.state())) return false;

        InteractionHand wrenchHand = wrenchHand(player, hand);
        if (wrenchHand == null) return doors.handlePlayerInteraction(player, hand, pos, entity);
        if (!player.mayBuild()) return false;
        if (player.isShiftKeyDown()) {
            HexieBridges.toolOps().floorSwitcherItem().switchMode(player, wrenchHand);
            return true;
        }

        var frame = entity instanceof SnapshotCarriageView snapshot ? HexieBridges.createPlayer().frame(snapshot, 1) : null;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        Vec3 from = frame == null ? entity.toLocalVector(eye, 1) : frame.local(eye);
        Vec3 to = frame == null ? entity.toLocalVector(end, 1) : frame.local(end);
        var hit = clicked.state().getShape(contraption.getContraptionWorld(), pos).clip(from, to, pos);
        if (hit == null) return false;
        var stack = player.getItemInHand(wrenchHand);
        for (BlockPos cell : new BlockPos[] {lower, lower.above()}) {
            Vec3 center = Vec3.atCenterOf(cell);
            BlockPos worldPos = BlockPos.containing(frame == null ? entity.toGlobalVector(center, 1) : frame.world(center));
            if (!entity.level().mayInteract(player, worldPos)
                    || !player.mayUseItemAt(worldPos, hit.getDirection(), stack)) return false;
        }
        if (!entity.level().isClientSide) {
            entity.setBlock(lower, new StructureTemplate.StructureBlockInfo(lower, bottom.state().cycle(DoorBlock.HINGE), bottom.nbt()));
            entity.setBlock(lower.above(), new StructureTemplate.StructureBlockInfo(lower.above(), top.state().cycle(DoorBlock.HINGE), top.nbt()));
            contraption.invalidateColliders();
        }
        return true;
    }

    private static InteractionHand wrenchHand(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) return hand;
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) return InteractionHand.OFF_HAND;
        return null;
    }
}
