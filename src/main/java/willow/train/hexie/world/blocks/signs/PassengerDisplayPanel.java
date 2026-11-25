package willow.train.hexie.world.blocks.signs;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.world.blockentity.panel.PassengerPanelEntity;
import willow.train.hexie.world.blockinteract.PassengerDisplayInstallation;
import willow.train.hexie.world.blockinteract.SignBlockInteract;
import willow.train.hexie.world.registry.blocks.BlockInit;

/** A carriage panel can retain both electronic display components in its own block state. */
public interface PassengerDisplayPanel extends EntityBlock, BlockControlHost,
        HexieItemRequirementProvider, SignBlockBridge.PassengerDisplayPanelView {
    @Override default boolean nativeInteraction() {
        return SignBlockInteract.ForPassengerDisplayPanel.nativeInteraction();
    }
    @Override default ControlSurface surfaceGeometry(BlockState state) {
        return SignBlockInteract.ForPassengerDisplayPanel.surfaceGeometry(state);
    }
    @Override default AABB controlBounds(BlockState state) {
        return SignBlockInteract.ForPassengerDisplayPanel.controlBounds(state);
    }
    @Override default BlockControlHit pickControl(
            BlockControlContext context,
            Vec3 from, Vec3 to) {
        return SignBlockInteract.ForPassengerDisplayPanel.pickControl(context, from, to);
    }
    default boolean supportsPassengerDisplay() { return true; }
    static boolean supports(Block block) {
        return block instanceof PassengerDisplayPanel panel && panel.supportsPassengerDisplay();
    }

    /** Shared loot also reaches Create's sneak-wrench inventory recovery, which skips BlockDropsEvent. */
    static List<ItemStack> withDisplayDrops(BlockState state,
            List<ItemStack> base) {
        if (!supports(state.getBlock()) || !state.hasProperty(PanelDisplayMount.PROPERTY)
                || !PassengerDisplayInstallation.isRoot(state)) return base;
        var mount = PanelDisplayMount.get(state);
        if (!mount.hasNumber() && !mount.hasSign()) return base;
        var drops = new ArrayList<>(base);
        if (mount.hasNumber()) drops.add(BlockInit.CRH2A_CARRIAGE_NUMBER_DISPLAY.get().asItem().getDefaultInstance());
        if (mount.hasSign()) drops.add(BlockInit.CRH2A_ELECTRONIC_SIGNAGE.get().asItem().getDefaultInstance());
        return List.copyOf(drops);
    }

    @Override default Requirement getRequiredItems(
            BlockState state, BlockEntity entity) {
        if (!PassengerDisplayInstallation.isRoot(state))
            return Requirement.NONE;
        return Requirement.of(
                Kind.CONSUME,
                withDisplayDrops(state, List.of(state.getBlock().asItem().getDefaultInstance())));
    }
    @Override default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return supports(state.getBlock()) ? new PassengerPanelEntity(pos, state) : null;
    }
}
