package willow.train.hexie.compat.cee;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.IPantographBlock;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographType;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.PantographBlockView;
import willow.train.hexie.bridge.world.base.PantographBlockBase;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.vehicle.TractionPowerSources;

public final class CeePantographCompat {
    private static final DeferredRegister<PantographType> TYPES =
            DeferredRegister.create(CEERegistries.PANTOGRAPH_TYPE.key(), HexieConstants.MOD_ID);
    // fixed head sits two blocks up and slightly forward
    private static final Supplier<PantographType> FIXED_HEAD = TYPES.register(
            "fixed_head", () -> new PantographType(0.25f, -0.10f, 1.875f, 0.5f));

    private CeePantographCompat() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(CeePantographCompat::setup);
    }

    public static PantographBlockView createBlock(BlockBehaviour.Properties properties) {
        return new CompatiblePantographBlock(properties);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TractionPowerSources.register(CeeSnapshotTrain::hasLinePower);
            var pantograph = HexieBridges.worldRegistry().blockInit().pantograph();
            MovementBehaviour.REGISTRY.register(pantograph, new FixedHeadMovement());
            MovingInteractionBehaviour.REGISTRY.register(pantograph, new TogglePantograph());
        });
    }

    private static final class CompatiblePantographBlock extends PantographBlockBase implements IPantographBlock {
        private CompatiblePantographBlock(Properties properties) {
            super(properties);
        }

        @Override
        public PantographType getPantographType(BlockState state) {
            return FIXED_HEAD.get();
        }
    }

    private static final class FixedHeadMovement implements MovementBehaviour {
        @Override
        public void startMoving(MovementContext context) {
            if (!context.world.isClientSide && context.contraption instanceof IPantographList pantographs) {
                pantographs.changePantographState(context.localPos, context.state.getValue(HexieBridges.carriageBlock().pantographBlock().open()));
            }
        }

        @Override
        public boolean mustTickWhileDisabled() {
            return true;
        }

        @Override
        public void tick(MovementContext context) {
            if (context.world.isClientSide
                    || !(context.contraption.entity instanceof CarriageContraptionEntity entity)
                    || !(entity.getCarriage() instanceof IPantographList pantographs)) return;
            // the actor keeps its original state after a click
            var info = context.contraption.getBlocks().get(context.localPos);
            boolean active = !context.disabled && info != null
                    && info.state().getBlock() instanceof CompatiblePantographBlock
                    && info.state().getValue(HexieBridges.carriageBlock().pantographBlock().open());
            pantographs.changePantographState(context.localPos, active);
        }
    }

    private static final class TogglePantograph extends MovingInteractionBehaviour {
        @Override
        public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                AbstractContraptionEntity entity) {
            if (!(entity instanceof CarriageContraptionEntity) || player.isSpectator()
                    || !player.mayBuild() || !player.getItemInHand(hand).isEmpty()
                    || entity.getContraption() == null || !entity.canInteractWithBlock(player, pos, 4)) return false;
            var info = entity.getContraption().getBlocks().get(pos);
            if (info == null || !(info.state().getBlock() instanceof CompatiblePantographBlock)) return false;
            if (!entity.level().isClientSide) {
                BlockState state = info.state().cycle(HexieBridges.carriageBlock().pantographBlock().open());
                entity.setBlock(pos, new StructureBlockInfo(pos, state, info.nbt()));
            }
            return true;
        }
    }
}
