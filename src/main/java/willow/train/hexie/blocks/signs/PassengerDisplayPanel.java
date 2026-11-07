package willow.train.hexie.blocks.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blockentity.PassengerPanelEntity;

/** A carriage panel can retain both electronic display components in its own block state. */
public interface PassengerDisplayPanel extends EntityBlock, willow.train.hexie.api.interaction.BlockControlHost,
        com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement {
    @Override default boolean nativeInteraction() { return true; }
    @Override default willow.train.hexie.api.interaction.ControlSurface surfaceGeometry(BlockState state) {
        return PanelAttachmentSurface.SURFACE;
    }
    @Override default net.minecraft.world.phys.AABB controlBounds(BlockState state) {
        return PanelAttachmentSurface.bounds(state);
    }
    @Override default willow.train.hexie.api.interaction.BlockControlHit pickControl(
            willow.train.hexie.api.interaction.BlockControlContext context,
            net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        return PanelAttachmentSurface.pick(context, from, to);
    }
    default boolean supportsPassengerDisplay() { return true; }
    static boolean supports(net.minecraft.world.level.block.Block block) {
        return block instanceof PassengerDisplayPanel panel && panel.supportsPassengerDisplay();
    }

    /** Shared loot also reaches Create's sneak-wrench inventory recovery, which skips BlockDropsEvent. */
    static java.util.List<net.minecraft.world.item.ItemStack> withDisplayDrops(BlockState state,
            java.util.List<net.minecraft.world.item.ItemStack> base) {
        if (!supports(state.getBlock()) || !state.hasProperty(PanelDisplayMount.PROPERTY)
                || !PassengerDisplayInstallation.isRoot(state)) return base;
        var mount = state.getValue(PanelDisplayMount.PROPERTY);
        if (!mount.hasNumber() && !mount.hasSign()) return base;
        var drops = new java.util.ArrayList<>(base);
        if (mount.hasNumber()) drops.add(willow.train.hexie.init.block.BlockInit.CRH2A_CARRIAGE_NUMBER_DISPLAY.get().asItem().getDefaultInstance());
        if (mount.hasSign()) drops.add(willow.train.hexie.init.block.BlockInit.CRH2A_ELECTRONIC_SIGNAGE.get().asItem().getDefaultInstance());
        return java.util.List.copyOf(drops);
    }

    @Override default com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(
            BlockState state, BlockEntity entity) {
        if (!PassengerDisplayInstallation.isRoot(state))
            return com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;
        return new com.simibubi.create.content.schematics.requirement.ItemRequirement(
                com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME,
                withDisplayDrops(state, java.util.List.of(state.getBlock().asItem().getDefaultInstance())));
    }
    @Override default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return supports(state.getBlock()) ? new PassengerPanelEntity(pos, state) : null;
    }
}
