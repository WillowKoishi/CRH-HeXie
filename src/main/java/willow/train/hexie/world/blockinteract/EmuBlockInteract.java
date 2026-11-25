package willow.train.hexie.world.blockinteract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.core.api.cab.CabTargetView;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blockentity.head.CRH1HeadEntity;
import willow.train.hexie.world.blockentity.panel.CRH2PartEntity;
import willow.train.hexie.world.blocks.emu.CRH1CabGeometry;
import willow.train.hexie.world.blocks.emu.CRH1DoorBlock;
import willow.train.hexie.world.blocks.emu.CRH1DoorMemberBlock;
import willow.train.hexie.world.blocks.emu.CRH1DrivingConsoleBlock;
import willow.train.hexie.world.blocks.emu.CRH1FloorBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadAnimation;
import willow.train.hexie.world.blocks.emu.CRH1HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadCoupling;
import willow.train.hexie.world.blocks.emu.CRH1HeadGeometry;
import willow.train.hexie.world.blocks.emu.CRH2CabGeometry;
import willow.train.hexie.world.blocks.emu.CRH2DoorBlock;
import willow.train.hexie.world.blocks.emu.CRH2DrivingConsoleBlock;
import willow.train.hexie.world.blocks.emu.CRH2EndFaceBlock;
import willow.train.hexie.world.blocks.emu.CRH2PanelBlock;
import willow.train.hexie.world.blocks.emu.CRH2PartBlock;
import willow.train.hexie.world.blocks.emu.HarmonyMarking;
import willow.train.hexie.world.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.world.placement.PartInstallation;

public final class EmuBlockInteract {
    private EmuBlockInteract() {}

    public static final class ForCRH1CabGeometry {
        public static AABB controlBounds(Direction facing) {
            var north = new AABB(.43, .63, .08, 1.12, 1.42, .96);
            return switch (facing) {
                case EAST -> new AABB(1 - north.maxZ, north.minY, north.minX, 1 - north.minZ, north.maxY, north.maxX);
                case SOUTH -> new AABB(1 - north.maxX, north.minY, 1 - north.maxZ, 1 - north.minX, north.maxY, 1 - north.minZ);
                case WEST -> new AABB(north.minZ, north.minY, 1 - north.maxX, north.maxZ, north.maxY, 1 - north.minX);
                default -> north;
            };
        }
    }

    public static final class ForCRH1DoorBlock {
        public static ControlSurface surfaceGeometry(BlockState state) { return HexieBridges.interaction().nativeControlSurface().door(); }

        public static AABB controlBounds(CRH1DoorBlock block, BlockState state) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return new AABB(0, 0, 0, 1, 1, 1);
            return block.interactionShape(state, false).bounds().minmax(block.interactionShape(state, true).bounds());
        }

        public static BlockControlHit pickControl(CRH1DoorBlock block, BlockControlContext target, Vec3 from, Vec3 to) {
            BlockPos lower = CRH1DoorBlock.root(target.state(), target.position());
            BlockState state = target.blocks().getBlockState(lower);
            if (!state.is(block)) return null;
            var hit = block.interactionShape(state, block.fullyOpen(state, target.blocks(), lower)).clip(from, to, lower);
            return hit == null ? null : new BlockControlHit(HexieBridges.interaction().nativeControlSurfaceControl().door(),
                    hit.withPosition(hit.getLocation().y >= lower.getY() + 1 ? lower.above() : lower));
        }
    }

    public static final class ForCRH1DoorMemberBlock {
        public static BlockHitResult redirect(BlockPos root, BlockHitResult hit) {
            return new BlockHitResult(hit.getLocation(), hit.getDirection(), root, hit.isInside());
        }

        public static InteractionResult useWithoutItem(CRH1DoorMemberBlock block, BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            var owner = PartStructures.resolve(world, pos, state);
            return owner == null ? InteractionResult.PASS : owner.state().useWithoutItem(world, player, block.redirect(owner.pos(), hit));
        }

        public static ItemInteractionResult useItemOn(CRH1DoorMemberBlock block, ItemStack stack, BlockState state, Level world, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit) {
            var owner = PartStructures.resolve(world, pos, state);
            return owner == null ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : owner.state().useItemOn(stack, world, player, hand, block.redirect(owner.pos(), hit));
        }
    }

    public static final class ForCRH1DrivingConsoleBlock {
        private final Map<BlockState, ControlInputLayout> detailedLayouts = new ConcurrentHashMap<>();

        private final Map<BlockState, ControlInputLayout> simpleLayouts = new ConcurrentHashMap<>();

        public ControlInputLayout inputLayout(BlockControlContext target, Supplier<ControlInputLayout> baseInputLayout) {
            var base = baseInputLayout.get();
            boolean simple = CabTargetView.find(target).controls().simple();
            var layouts = simple ? simpleLayouts : detailedLayouts;
            return layouts.computeIfAbsent(target.state(), ignored -> {
                var bindings = new ArrayList<>(base.bindings());
                if (!simple) {
                    bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, 'W', ControlInputLayout.Action.STEP_HOLD,
                            HexieBridges.cabControl().crh1CabHandle().instance().id(), 1).seated());
                    bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, 'S', ControlInputLayout.Action.STEP_HOLD,
                            HexieBridges.cabControl().crh1CabHandle().instance().id(), -1).seated());
                }
                bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, 'R', ControlInputLayout.Action.PRESS,
                        HexieBridges.cabControl().crh1CabControlsDirectionButton().instance().id(), 0).seated());
                return new ControlInputLayout(base.seatedCapture(), base.pointedEntries(), bindings, base.view());
            });
        }

        public void endCapture(CRH1DrivingConsoleBlock block, BlockControlUse use, boolean seated, boolean explicit, BiFunction<BlockControlUse, ControlInput, InteractionResult> baseOperateInput, Runnable baseEndCapture) {
            int current = HexieBridges.cabControl().crh1CabHandle().instance().notch(block.surfaceState(use.target()));
            int released = HexieBridges.cabControl().crh1CabHandle().instance().releasedNotch(current);
            if (released != current)
                baseOperateInput.apply(use, new ControlInput(HexieBridges.cabControl().crh1CabHandle().instance().id(), released, ControlInput.Kind.SET, Set.of()));
            baseEndCapture.run();
        }

        public AABB controlBounds(CRH1DrivingConsoleBlock block, BlockState state) { return CRH1CabGeometry.controlBounds(block.cabFacing(state)); }
    }

    public static final class ForCRH1FloorBlock {
        public static ItemInteractionResult useItemOn(CRH1FloorBlock block, ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            var skirt = CRH1FloorBlock.skirtProperty(state, hit.getDirection());
            if (!(stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) || player.isShiftKeyDown() || skirt == null)
                return baseUseItemOn.get();
            if (player.isSpectator() || !player.mayBuild() || player.level() != level)
                return ItemInteractionResult.FAIL;
            var root = PartStructures.resolve(level, pos, state);
            if (root == null || root.structure() != block
                    || !PartInstallation.complete(level, root.pos(), root.state(), block))
                return ItemInteractionResult.FAIL;
            boolean changed = PartInstallation.replace(level, block.partCells(level, root.pos(), root.state()),
                    block.partCells(level, root.pos(), root.state().cycle(skirt)), at -> false,
                    at -> level.mayInteract(player, at) && player.mayUseItemAt(at, hit.getDirection(), stack));
            return changed ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
        }
    }

    public static final class ForCRH1HeadBlock {
        public static AABB controlBounds(BlockState state) { return CRH1HeadGeometry.controlBounds(state); }

        public static BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
            return CRH1HeadGeometry.pick(target, from, to);
        }

        public static boolean nativeInteraction() { return false; }

        public static InteractionResult operateControl(BlockControlUse use, BlockControl control, int value) {
            if (use.player().isSpectator() || !use.player().mayBuild()) return InteractionResult.FAIL;
            var target = use.target();
            if (control == HexieBridges.interaction().nativeControlSurfaceControl().couplerHead()) {
                return target.carrier() instanceof SnapshotCarriageView carriage
                        && HexieBridges.createCarriage().snapshotCouplerInteraction().handle(carriage, use.player(), target.position(), use.hand())
                        ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (control != HexieBridges.interaction().nativeControlSurfaceControl().couplerBody()) return InteractionResult.PASS;
            if (target.carrier() instanceof SnapshotCarriageView carriage) {
                var info = carriage.asEntity().getContraption().getBlocks().get(target.position());
                if (info == null) return InteractionResult.FAIL;
                if (info.state().getValue(BlockStateProperties.OPEN)) CRH1HeadCoupling.release(carriage, target.position());
                var next = CRH1HeadAnimation.toggle(info, carriage.asEntity().level().getGameTime());
                carriage.setBlock(target.position(), next);
                if (!carriage.asEntity().level().isClientSide)
                    HexieBridges.sync().sendCRH1HeadAnimationToTracking(target.carrier(), next);
                carriage.syncCarriage();
                return InteractionResult.SUCCESS;
            }
            if (!(target.blocks() instanceof Level level)
                    || !(level.getBlockEntity(target.position()) instanceof CRH1HeadEntity head)) return InteractionResult.FAIL;
            head.beginAnimation(level.getGameTime());
            if (!level.setBlock(target.position(), target.state().cycle(BlockStateProperties.OPEN), Block.UPDATE_ALL))
                return InteractionResult.FAIL;
            head.syncAnimation();
            return InteractionResult.SUCCESS;
        }
    }

    public static final class ForCRH1HeadGeometry {
        private static final AABB CONTROLS = new AABB(-.4, -.1, -3.1, 1.4, 2, -1.35);

        public static AABB controlBounds(BlockState state) {
            return CRH1HeadGeometry.rotateBox(CONTROLS, state.getValue(CRH1HeadBlock.FACING));
        }

        public static BlockControlHit pick(BlockControlContext target, Vec3 from, Vec3 to) {
            BlockState state = target.state();
            float progress;
            SnapshotCarriageView carriage = target.carrier() instanceof SnapshotCarriageView car ? car : null;
            if (carriage != null) {
                var info = carriage.asEntity().getContraption().getBlocks().get(target.position());
                progress = info == null ? 0 : CRH1HeadAnimation.progress(info.state(), info.nbt(), target.time());
            } else if (target.blocks().getBlockEntity(target.position()) instanceof CRH1HeadEntity entity)
                progress = entity.animationProgress(target.partialTick());
            else progress = state.getValue(BlockStateProperties.OPEN) ? 1 : 0;
            Vec3 a = CouplerInteraction.toModel(state, target.position(), from);
            Vec3 b = CouplerInteraction.toModel(state, target.position(), to);
            CRH1HeadGeometry.Hit upper = clip(CRH1HeadGeometry.UPPER, a, b, point -> CRH1HeadGeometry.upper(point, progress), false);
            CRH1HeadGeometry.Hit lower = clip(CRH1HeadGeometry.LOWER, a, b, point -> CRH1HeadGeometry.lower(point, progress), false);
            var body = nearer(a, upper, lower);
            if (body != null) body = new CRH1HeadGeometry.Hit(CouplerInteraction.fromModel(state, target.position(), body.point()),
                    rotateFacing(body.normal(), state.getValue(CRH1HeadBlock.FACING)), false);

            var frame = carriage == null ? null : carriage.couplerAnimation().frameAt(target.position(), target.partialTick());
            CRH1HeadGeometry.Hit front;
            if (frame == null) {
                front = clip(CRH1HeadGeometry.FRONT, a, b, point -> point.add(0, 0, -CRH1HeadAnimation.EXTENSION
                        * CRH1HeadAnimation.extension(progress)), true);
                if (front != null) front = new CRH1HeadGeometry.Hit(CouplerInteraction.fromModel(state, target.position(), front.point()),
                        rotateFacing(front.normal(), state.getValue(CRH1HeadBlock.FACING)), true);
            } else {
                Vec3 pivot = new Vec3(frame.x(), frame.y(), frame.z());
                Quaternionf rotation = frame.rotation(), inverse = new Quaternionf(rotation).conjugate();
                Vec3 af = CRH1HeadGeometry.rotate(inverse, from.subtract(pivot)).add(CRH1HeadGeometry.armOrigin());
                Vec3 bf = CRH1HeadGeometry.rotate(inverse, to.subtract(pivot)).add(CRH1HeadGeometry.armOrigin());
                front = clip(CRH1HeadGeometry.FRONT, af, bf, point -> point.add(0, 0, -CRH1HeadAnimation.EXTENSION
                        * CRH1HeadAnimation.extension(progress)), true);
                if (front != null) front = new CRH1HeadGeometry.Hit(CRH1HeadGeometry.rotate(rotation, front.point().subtract(CRH1HeadGeometry.armOrigin())).add(pivot),
                        CRH1HeadGeometry.rotate(rotation, front.normal()), true);
            }
            CRH1HeadGeometry.Hit hit = nearer(from, body, front);
            if (hit == null) return null;
            Direction face = Direction.getNearest((float) hit.normal().x, (float) hit.normal().y, (float) hit.normal().z);
            return new BlockControlHit(hit.head() ? HexieBridges.interaction().nativeControlSurfaceControl().couplerHead() : HexieBridges.interaction().nativeControlSurfaceControl().couplerBody(),
                    new BlockHitResult(hit.point(), face, target.position(), false));
        }

        public static CRH1HeadGeometry.Hit clip(List<CRH1HeadGeometry.Triangle> triangles, Vec3 from, Vec3 to, UnaryOperator<Vec3> transform, boolean head) {
            Vec3 ray = to.subtract(from);
            CRH1HeadGeometry.Hit best = null;
            double distance = Double.POSITIVE_INFINITY;
            for (var triangle : triangles) {
                Vec3 a = transform.apply(triangle.a()), b = transform.apply(triangle.b()), c = transform.apply(triangle.c());
                Vec3 e1 = b.subtract(a), e2 = c.subtract(a), p = ray.cross(e2);
                double determinant = e1.dot(p);
                if (Math.abs(determinant) < 1e-9) continue;
                double inverse = 1 / determinant;
                Vec3 origin = from.subtract(a);
                double u = origin.dot(p) * inverse;
                if (u < -1e-9 || u > 1 + 1e-9) continue;
                Vec3 q = origin.cross(e1);
                double v = ray.dot(q) * inverse;
                if (v < -1e-9 || u + v > 1 + 1e-9) continue;
                double t = e2.dot(q) * inverse;
                if (t < 0 || t > 1 || t >= distance) continue;
                Vec3 normal = e1.cross(e2);
                if (normal.dot(ray) > 0) normal = normal.scale(-1);
                distance = t;
                best = new CRH1HeadGeometry.Hit(from.add(ray.scale(t)), normal, head);
            }
            return best;
        }

        public static CRH1HeadGeometry.Hit nearer(Vec3 from, CRH1HeadGeometry.Hit a, CRH1HeadGeometry.Hit b) {
            return a == null ? b : b == null || a.point().distanceToSqr(from) <= b.point().distanceToSqr(from) ? a : b;
        }

        public static Vec3 rotateFacing(Vec3 normal, Direction facing) {
            return switch (facing) {
                case EAST -> new Vec3(-normal.z, normal.y, normal.x);
                case SOUTH -> new Vec3(-normal.x, normal.y, -normal.z);
                case WEST -> new Vec3(normal.z, normal.y, -normal.x);
                default -> normal;
            };
        }
    }

    public static final class ForCRH2CabGeometry {
        public static AABB controlBounds(Direction facing) { return CRH2CabGeometry.CONTROL_BOUNDS.get(facing); }
    }

    public static final class ForCRH2DoorBlock {
        public static ItemInteractionResult useItemOn(CRH2DoorBlock block, ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (block.supportsPassengerFrame() && stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView && !player.isShiftKeyDown()) {
                if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;
                BlockPos lower = state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
                if (!level.getBlockState(lower).is(block)
                        || !(level.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door))
                    return ItemInteractionResult.FAIL;
                HexieBridges.toolOps().floorSwitcherItem().beginUsingDoor(player, hand, door);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return baseUseItemOn.get();
        }
    }

    public static final class ForCRH2DrivingConsoleBlock {
        private final Map<BlockState, ControlInputLayout> handleLayouts = new ConcurrentHashMap<>();

        public ControlInputLayout inputLayout(CRH2DrivingConsoleBlock block, BlockControlContext target, Supplier<ControlInputLayout> baseInputLayout) {
            var base = baseInputLayout.get();
            var state = target.state();
            if (block.trainControl(state) != HexieBridges.cabControl().crh2CabControls().instance() || CabTargetView.find(target).controls().simple())
                return base;
            return handleLayouts.computeIfAbsent(state, ignored -> {
                var bindings = new ArrayList<>(base.bindings());
                bindings.add(handleKey('W', HexieBridges.cabControl().crh2CabHandle().reverser(), 1));
                bindings.add(handleKey('S', HexieBridges.cabControl().crh2CabHandle().reverser(), -1));
                bindings.add(handleKey(';', HexieBridges.cabControl().crh2CabHandle().brake(), -1));
                bindings.add(handleKey('\'', HexieBridges.cabControl().crh2CabHandle().brake(), 1));
                bindings.add(handleKey('D', HexieBridges.cabControl().crh2CabHandle().power(), -1));
                bindings.add(handleKey('A', HexieBridges.cabControl().crh2CabHandle().power(), 1));
                return new ControlInputLayout(base.seatedCapture(), base.pointedEntries(), bindings, base.view());
            });
        }

        public static ControlInputLayout.Binding handleKey(int key, CabControlBridge.Crh2CabHandleView handle, int direction) {
            return new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, key,
                    ControlInputLayout.Action.STEP_HOLD, handle.id(), direction).seated();
        }

        public Component keyboardHint(CRH2DrivingConsoleBlock block, BlockState state) {
            return block.trainControl(state) == HexieBridges.cabControl().crh2CabControls().instance()
                    ? Component.translatable("gui.hexie.cab.handle_keys", hintKeys("W", "S"),
                            hintKeys(";", "'"), hintKeys("A", "D"))
                    : Component.empty();
        }

        public static Component hintKeys(String first, String second) {
            return Component.empty()
                    .append(Component.literal("[" + first + "]").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("[" + second + "]").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }

        public AABB controlBounds(CRH2DrivingConsoleBlock block, BlockState state) {
            return block.surfaceGeometry(state) == ControlSurface.EMPTY ? null : CRH2CabGeometry.controlBounds(block.cabFacing(state));
        }
    }

    public static final class ForCRH2EndFaceBlock {
        public static void showVariant(Player player, BlockState state) {
            if (!player.level().isClientSide)
                player.displayClientMessage(Component.translatable("block.hexie.crh2a_endface.variant.changed",
                        CRH2EndFaceBlock.type(state).displayName()), true);
        }

        public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (!(stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) || player.isShiftKeyDown())
                return baseUseItemOn.get();
            if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, hit.getDirection(), stack)) return ItemInteractionResult.FAIL;
            BlockState next = state.cycle(CRH2EndFaceBlock.VARIANT);
            // A same-block state change retains the coupler block entity and its installed adapters.
            if (!level.isClientSide) {
                if (!level.setBlock(pos, next, Block.UPDATE_ALL)) return ItemInteractionResult.FAIL;
                showVariant(player, next);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public static final class ForCRH2PanelBlock {
        public static CRH2PartEntity markingEntity(CRH2PanelBlock block, Level level,
                BlockPos pos,BlockState state) {
            var owner=block.root(pos,state);var ownerState=level.getBlockState(owner);
            if(!ownerState.is(block)||ownerState.getValue(CRH2PartBlock.PART)!=0)return null;
            if(!level.isClientSide&&level.getBlockEntity(owner)==null)
                level.setBlockEntity(new CRH2PartEntity(owner,ownerState));
            return level.getBlockEntity(owner) instanceof CRH2PartEntity entity?entity:null;
        }

        public static ItemInteractionResult useItemOn(CRH2PanelBlock block, ItemStack stack,
                BlockState state,Level level,BlockPos pos,
                Player player,InteractionHand hand,
                BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if(!(stack.getItem() instanceof ToolOpsBridge.ColoredBrushView))
                return baseUseItemOn.get();
            if(level!=player.level()||!player.mayBuild()||player.isSpectator())return ItemInteractionResult.FAIL;
            if(level.isClientSide)return ItemInteractionResult.SUCCESS;
            var entity=block.markingEntity(level,pos,state);
            if(entity==null)return ItemInteractionResult.FAIL;
            entity.selectMarkingSlot(HarmonyMarking.slot(block,state,entity.getBlockPos(),hit.getLocation()));
            if(player instanceof ServerPlayer server)server.openMenu(entity,buf->{
                buf.writeBlockPos(entity.getBlockPos());for(String field:entity.fields())buf.writeUtf(field,128);
                buf.writeBoolean(entity.descriptionLeft());
            });
            return ItemInteractionResult.CONSUME;
        }

        public static InteractionResult useWithoutItem(CRH2PanelBlock block, BlockState state,Level level,
                BlockPos pos,Player player,BlockHitResult hit) {
            if(level!=player.level()||!player.mayBuild()||player.isSpectator()||!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())
                return InteractionResult.PASS;
            var entity=block.markingEntity(level,pos,state);
            if(entity==null)return InteractionResult.PASS;
            var root=PartStructures.resolve(level,pos,state);
            if(root==null||PanelAttachmentHit.displayRegion(level,root,hit))
                return InteractionResult.PASS;
            var target=PanelAttachmentHit.resolve(level,pos,hit);
            if (player.isShiftKeyDown() && target.kind()==PartBlockBridge.PanelAttachmentHitKind.PANEL_TEXT) {
                if (player instanceof ServerPlayer server)
                    HexieBridges.menu().panelTextMenu().open(server, entity, target.slot(), InteractionHand.MAIN_HAND);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if(target.kind()==PartBlockBridge.PanelAttachmentHitKind.PANEL_TEXT)
                return InteractionResult.PASS;
            if(target.kind()==PartBlockBridge.PanelAttachmentHitKind.HARMONY) {
                if(!level.isClientSide)entity.toggleHarmony(target.slot());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            int logoSlot=PanelAttachmentHit.lowerMarkingSlot(level,root,hit);
            if(logoSlot<0)return InteractionResult.PASS;
            if(Arrays.stream(entity.fields()).allMatch(String::isBlank))return InteractionResult.PASS;
            if(!level.isClientSide) {
                entity.selectMarkingSlot(logoSlot);
                entity.toggleDescription();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public static final class ForCRH2PartBlock {
        public static InteractionResult onWrenched(CRH2PartBlock block, BlockState s,UseOnContext c) {
            var level=c.getLevel();var root=block.root(c.getClickedPos(),s);var owner=level.getBlockState(root);
            if(!owner.is(block)||owner.getValue(CRH2PartBlock.PART)!=0)return InteractionResult.PASS;
            var rotated=block.getRotatedBlockState(owner,c.getClickedFace());
            if(rotated.equals(owner))return InteractionResult.PASS;
            if(level.isClientSide)return InteractionResult.SUCCESS;
            if(!PartInstallation.replace(level,block.partCells(level,root,owner),block.partCells(level,root,rotated),
                    at->false,at->c.getPlayer()==null||c.getPlayer().mayUseItemAt(at,c.getClickedFace(),c.getItemInHand())))
                return InteractionResult.FAIL;
            BaseBlockInteract.playWrenchRotateSound(level,root);
            return InteractionResult.SUCCESS;
        }

        public static InteractionResult onSneakWrenched(CRH2PartBlock block, BlockState s,UseOnContext c, BiFunction<BlockState, UseOnContext, InteractionResult> baseOnSneakWrenched) {
            var root=block.root(c.getClickedPos(),s);var owner=c.getLevel().getBlockState(root);
            if(!owner.is(block))return InteractionResult.PASS;
            return baseOnSneakWrenched.apply(owner,new UseOnContext(c.getLevel(),c.getPlayer(),c.getHand(),c.getItemInHand(),
                new BlockHitResult(Vec3.atCenterOf(root),c.getClickedFace(),root,false)));
        }
    }

    public static final class ForTestDrivingConsoleBlock {
        public static AABB controlBounds(TestDrivingConsoleBlock block, BlockState state) {
            return block.getShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                    CollisionContext.empty()).bounds();
        }
    }
}
