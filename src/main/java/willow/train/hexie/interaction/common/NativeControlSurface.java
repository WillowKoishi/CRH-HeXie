package willow.train.hexie.interaction.common;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.ContraptionWorldAccessorView;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.interaction.InteractionBridge.NativeControlSurfaceControlView;
import willow.train.hexie.bridge.interaction.InteractionBridge.NativeControlSurfaceOperations;
import willow.train.hexie.bridge.interaction.InteractionBridge.NativeControlSurfaceView;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.CustomRenderedDoorEntityOperations;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;

/** Door leaves and hook parts register through the same surface API as desk buttons and handles. */
public final class NativeControlSurface implements NativeControlSurfaceOperations {
    public enum Control implements NativeControlSurfaceControlView {
        DOOR, COUPLER_HEAD, COUPLER_BODY;
        @Override public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
        @Override public Component nameText() { return Component.translatable("interaction.hexie." + id()); }
        @Override public Component valueText(ControlState state) { return Component.empty(); }
    }
    public static final ControlSurface DOOR = declared(Control.DOOR);
    private static final ControlSurface COUPLER = declared(Control.COUPLER_HEAD, Control.COUPLER_BODY);
    private static ControlSurface declared(Control... controls) {
        return new ControlSurface(java.util.Arrays.stream(controls)
                .map(control -> new ControlSurface.Part(control, null, ControlSurface.NO_BODY, null)).toList(),
                ControlSurface.NO_BODY, List.of());
    }
    @Override public ControlSurface surfaceGeometry(NativeControlSurfaceView surface, BlockState state) {
        return state.getBlock() instanceof CRH2DoorBlockView ? DOOR : COUPLER;
    }
    @Override public ControlSurface door() { return DOOR; }
    @Override public AABB controlBounds(NativeControlSurfaceView surface, BlockState state) {
        if (state.getBlock() instanceof CRH2DoorBlockView door) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return new AABB(0, 0, 0, 1, 1, 1);
            return door.interactionShape(state, 0).bounds()
                    .minmax(door.interactionShape(state, 1).bounds())
                    .minmax(door.interactionShape(state, 1, true).bounds());
        }
        var hook = HexieBridges.couplerBlock().couplerBlock().of(state);
        return hook != null && hook.geometry() != null ? hook.geometry().bounds(hook.facing(state))
                : new AABB(0, 0, 0, 1, 1, 1);
    }
    @Override public AABB controlBounds(NativeControlSurfaceView surface, BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof CouplerBlockEntityView coupler
                ? HexieBridges.worldBlock().couplerAdapterGeometry().bounds(state, coupler) : surface.controlBounds(state);
    }
    @Override public BlockControlHit pickControl(NativeControlSurfaceView surface, BlockControlContext target, Vec3 from, Vec3 to) {
        var state = target.state();
        var position = target.position();
        if (state.getBlock() instanceof CRH2DoorBlockView door) {
            BlockPos root = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
            var lower = target.blocks().getBlockState(root);
            if (!lower.is(door.asBlock())) return null;
            float progress = lower.getValue(DoorBlock.OPEN) ? 1 : 0;
            boolean reverse = false;
            if (target.blocks() instanceof ContraptionWorldAccessorView view) {
                var contraption = view.hexie$getContraption();
                progress = HexieBridges.createDoor().crh2DoorMovement().fullyOpen(contraption, root, door) ? 1 : 0;
                var info = contraption.getBlocks().get(root);
                reverse = info != null && info.nbt() != null && info.nbt().getBoolean(CustomRenderedDoorEntityOperations.REVERSE_SLIDE_TAG);
            } else if (target.blocks().getBlockEntity(root) instanceof CustomRenderedDoorEntityView entity) {
                progress = entity.crh2InteractionProgress();
                reverse = entity.hasReverseSlide();
            }
            var hit = door.interactionShape(lower, progress, reverse).clip(from, to, root);
            if (hit == null) return null;
            return new BlockControlHit(Control.DOOR,
                    hit.withPosition(hit.getLocation().y >= root.getY() + 1 ? root.above() : root));
        }
        if (HexieBridges.partBlock().couplerInteraction().hasGeometry(state)) {
            CouplerBlockEntityView entity = target.blocks().getBlockEntity(position) instanceof CouplerBlockEntityView coupler ? coupler : null;
            float progress = entity != null ? entity.coverProgress(target.partialTick()) : HexieBridges.partBlock().couplerInteraction().coverProgress(state);
            var hit = target.carrier() instanceof SnapshotCarriageView car
                    ? HexieBridges.createCarriage().snapshotCouplerModelInteraction().clip(car, position, state, progress, target.partialTick(), from, to)
                    : HexieBridges.partBlock().couplerInteraction().clipWorld(state, position, progress, entity, target.partialTick(), from, to);
            return hit == null ? null : new BlockControlHit(hit.head() ? Control.COUPLER_HEAD : Control.COUPLER_BODY,
                    new BlockHitResult(hit.point(), hit.face(), position, false));
        }
        var hit = state.getShape(target.blocks(), position).clip(from, to, position);
        return hit == null ? null : new BlockControlHit(Control.COUPLER_HEAD, hit);
    }
}
