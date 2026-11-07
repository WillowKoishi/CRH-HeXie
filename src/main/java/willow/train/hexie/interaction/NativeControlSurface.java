package willow.train.hexie.interaction;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.*;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerInteraction;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.compat.create.CRH2DoorMovement;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.SnapshotCouplerModelInteraction;
import willow.train.hexie.mixins.mixin.ContraptionWorldAccessor;

/** Door leaves and hook parts register through the same surface API as desk buttons and handles. */
public interface NativeControlSurface extends BlockControlHost {
    enum Control implements BlockControl.Button {
        DOOR, COUPLER_HEAD, COUPLER_BODY;
        @Override public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
        @Override public Component nameText() { return Component.translatable("interaction.hexie." + id()); }
        @Override public Component valueText(ControlState state) { return Component.empty(); }
    }
    ControlSurface DOOR = declared(Control.DOOR);
    ControlSurface COUPLER = declared(Control.COUPLER_HEAD, Control.COUPLER_BODY);
    private static ControlSurface declared(Control... controls) {
        return new ControlSurface(java.util.Arrays.stream(controls)
                .map(control -> new ControlSurface.Part(control, null, ControlSurface.NO_BODY, null)).toList(),
                ControlSurface.NO_BODY, List.of());
    }
    @Override default ControlSurface surfaceGeometry(BlockState state) {
        return state.getBlock() instanceof CRH2DoorBlock ? DOOR : COUPLER;
    }
    @Override default boolean nativeInteraction() { return true; }
    @Override default AABB controlBounds(BlockState state) {
        if (state.getBlock() instanceof CRH2DoorBlock door) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return new AABB(0, 0, 0, 1, 1, 1);
            return door.interactionShape(state, 0).bounds()
                    .minmax(door.interactionShape(state, 1).bounds())
                    .minmax(door.interactionShape(state, 1, true).bounds());
        }
        var hook = CouplerBlock.of(state);
        return hook != null && hook.geometry() != null ? hook.geometry().bounds(hook.facing(state))
                : new AABB(0, 0, 0, 1, 1, 1);
    }
    @Override default AABB controlBounds(BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof CouplerBlockEntity coupler
                ? willow.train.hexie.blocks.coupler.CouplerAdapterGeometry.bounds(state, coupler) : controlBounds(state);
    }
    @Override default BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        var state = target.state();
        var position = target.position();
        if (state.getBlock() instanceof CRH2DoorBlock door) {
            BlockPos root = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
            var lower = target.blocks().getBlockState(root);
            if (!lower.is(door)) return null;
            float progress = lower.getValue(DoorBlock.OPEN) ? 1 : 0;
            boolean reverse = false;
            if (target.blocks() instanceof ContraptionWorldAccessor view) {
                var contraption = view.hexie$getContraption();
                progress = CRH2DoorMovement.fullyOpen(contraption, root, door) ? 1 : 0;
                var info = contraption.getBlocks().get(root);
                reverse = info != null && info.nbt() != null && info.nbt().getBoolean(CustomRenderedDoorEntity.REVERSE_SLIDE_TAG);
            } else if (target.blocks().getBlockEntity(root) instanceof CustomRenderedDoorEntity entity) {
                progress = entity.crh2InteractionProgress();
                reverse = entity.hasReverseSlide();
            }
            var hit = door.interactionShape(lower, progress, reverse).clip(from, to, root);
            if (hit == null) return null;
            return new BlockControlHit(Control.DOOR,
                    hit.withPosition(hit.getLocation().y >= root.getY() + 1 ? root.above() : root));
        }
        if (CouplerInteraction.hasGeometry(state)) {
            CouplerBlockEntity entity = target.blocks().getBlockEntity(position) instanceof CouplerBlockEntity coupler ? coupler : null;
            float progress = entity != null ? entity.coverProgress(target.partialTick()) : CouplerInteraction.coverProgress(state);
            var hit = target.carrier() instanceof SnapshotCarriageEntity car
                    ? SnapshotCouplerModelInteraction.clip(car, position, state, progress, target.partialTick(), from, to)
                    : CouplerInteraction.clipWorld(state, position, progress, entity, target.partialTick(), from, to);
            return hit == null ? null : new BlockControlHit(hit.head() ? Control.COUPLER_HEAD : Control.COUPLER_BODY,
                    new BlockHitResult(hit.point(), hit.face(), position, false));
        }
        var hit = state.getShape(target.blocks(), position).clip(from, to, position);
        return hit == null ? null : new BlockControlHit(Control.COUPLER_HEAD, hit);
    }
}
