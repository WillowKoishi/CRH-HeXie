package willow.train.hexie.compat.create.carriage25;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.carriage.Carriage25FloorView;

@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class Carriage25CreateCompat {
    private Carriage25CreateCompat() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("create")) event.enqueueWork(Loaded::register);
    }

    /** Keep Create-linked class initialization behind the optional-mod check. */
    private static final class Loaded {
        static void register() {
            var interaction = new FloorInteraction();
            MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                    block instanceof Carriage25FloorView ? interaction : null);
        }
    }

    private static final class FloorInteraction extends MovingInteractionBehaviour {
        @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                                                          AbstractContraptionEntity entity) {
            var contraption = entity.getContraption();
            if (player.isSpectator() || !player.mayBuild() || player.level() != entity.level()
                    || contraption == null || !entity.canInteractWithBlock(player, pos, 4)) return false;
            var info = contraption.getBlocks().get(pos);
            if (info == null || !(info.state().getBlock() instanceof Carriage25FloorView floor)) return false;
            var stack = player.getItemInHand(hand);
            InteractionHand wrenchHand = hand;
            if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND
                    && player.getOffhandItem().getItem() instanceof FloorSwitcherItemView) {
                stack = player.getOffhandItem();
                wrenchHand = InteractionHand.OFF_HAND;
            }
            boolean wrench = stack.getItem() instanceof FloorSwitcherItemView;
            if (!wrench && (!stack.isEmpty() || hand != InteractionHand.MAIN_HAND)) return false;
            if (wrench && player.isShiftKeyDown()) {
                HexieBridges.toolOps().floorSwitcherItem().switchMode(player, wrenchHand);
                return true;
            }

            var frame = entity instanceof SnapshotCarriageView snapshot
                    ? HexieBridges.createPlayer().frame(snapshot, 1) : null;
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
            Vec3 from = frame == null ? entity.toLocalVector(eye, 1) : frame.local(eye);
            Vec3 to = frame == null ? entity.toLocalVector(end, 1) : frame.local(end);
            var hit = info.state().getShape(contraption.getContraptionWorld(), pos).clip(from, to, pos);
            if (hit == null) return false;
            Vec3 center = Vec3.atCenterOf(pos);
            BlockPos worldPos = BlockPos.containing(frame == null ? entity.toGlobalVector(center, 1) : frame.world(center));
            if (!entity.level().mayInteract(player, worldPos)
                    || !player.mayUseItemAt(worldPos, hit.getDirection(), stack)) return false;
            BlockState next = wrench ? floor.wrenchState(info.state(), hit.getDirection()) : floor.rotateEquipment(info.state());
            if (next == null || next == info.state()) return false;
            if (!entity.level().isClientSide) entity.setBlock(pos, new StructureBlockInfo(pos, next, info.nbt()));
            return true;
        }
    }
}
