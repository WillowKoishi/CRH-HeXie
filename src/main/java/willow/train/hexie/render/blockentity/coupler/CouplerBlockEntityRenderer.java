package willow.train.hexie.render.blockentity.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.RescueCouplerItemView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplerAdapterView;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.core.api.carriage.CarriageFlowRegistry;
import willow.train.hexie.render.coupler.RescueCouplerRenderer;
import willow.train.hexie.render.coupler.SnapshotCouplerRenderer;

/** Adds the registered item model at the original hook head, in world and carriage rendering. */
public final class CouplerBlockEntityRenderer implements BlockEntityRenderer<BlockEntity> {
    private static final Map<String, ItemStack> ADAPTER_ITEMS = new HashMap<>();

    public CouplerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(BlockEntity blockEntity, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (!(blockEntity instanceof CouplerBlockEntityView entity)) return;
        var adapter = entity.adapter();
        var state = entity.asBlockEntity().getBlockState();
        var level = blockEntity.getLevel();
        if (level instanceof net.minecraft.client.multiplayer.ClientLevel || level instanceof CarriageFlowRegistry.PreviewLevel
                || ModList.get().isLoaded("create") && level instanceof com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld)
            SnapshotCouplerRenderer.renderWorldAttachedParts(
                    state, entity.asBlockEntity(), partialTick, stack, buffers, light, overlay);
        if (adapter == null || !(state.getBlock() instanceof CouplerBlockView hook)
                || hook.assembled(state)) return;
        stack.pushPose();
        stack.translate(.5, .5, .5);
        stack.mulPose(Axis.YP.rotationDegrees(yaw(hook.facing(state))));
        var complete = hook.complete();
        stack.translate(complete.x(), complete.y(), complete.z());
        renderAdapter(adapter, !hook.model().equals(adapter.firstModel()), entity.rescueKnuckleProgress(partialTick),
                entity.asBlockEntity().getLevel(), stack, buffers, light, overlay);
        stack.popPose();
    }

    /** Origin is the original model's complete point, with local north pointing out of the hook. */
    public static void renderAdapter(CouplerAdapterView adapter, boolean reversed, Level level, PoseStack stack,
                                     MultiBufferSource buffers, int light, int overlay) {
        renderAdapter(adapter, reversed, 1, level, stack, buffers, light, overlay);
    }

    public static void renderAdapter(CouplerAdapterView adapter, boolean reversed, float knuckleProgress,
            Level level, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        stack.pushPose();
        String model = reversed ? adapter.secondModel() : adapter.firstModel();
        var layers = reversed ? adapter.items().reversed() : adapter.items();
        for (int i = 0; i < layers.size(); i++) {
            var layer = layers.get(i);
            boolean layerReversed = !model.equals(layer.firstModel());
            // Interior Janney ends are already engaged with their adjacent hook/adapter.
            float progress = i == layers.size() - 1 && !layerReversed ? knuckleProgress : 0;
            renderLayer(layer, layerReversed, progress, level, stack, buffers, light, overlay);
            var offset = layer.offsetFrom(model);
            stack.translate(offset.x(), offset.y(), offset.z());
            model = layer.otherModel(model);
        }
        stack.popPose();
    }

    private static void renderLayer(CouplerAdapterView adapter, boolean reversed, float progress,
            Level level, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        var item = ADAPTER_ITEMS.computeIfAbsent(adapter.itemId(), id -> {
            var location = ResourceLocation.tryParse(id);
            return location == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(location));
        });
        if (item.isEmpty()) return;
        if (item.getItem() instanceof RescueCouplerItemView) {
            RescueCouplerRenderer.render(adapter, reversed, progress,
                    stack, buffers, light, overlay);
            return;
        }
        stack.pushPose();
        // ItemRenderer centres [0,1]^3. Its z=1 face meets the old head and z=0 is the new head.
        stack.translate(0, 0, -adapter.thickness() / 2);
        stack.scale(1, 1, (float) adapter.thickness());
        if (reversed) stack.mulPose(Axis.YP.rotationDegrees(180));
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.NONE,
                light, overlay, stack, buffers, level, 0);
        stack.popPose();
    }

    @Override public AABB getRenderBoundingBox(BlockEntity blockEntity) {
        if (!(blockEntity instanceof CouplerBlockEntityView entity)) return new AABB(blockEntity.getBlockPos());
        var base = new AABB(entity.asBlockEntity().getBlockPos());
        var modelBounds = HexieBridges.worldBlock().couplerAdapterGeometry().bounds(entity.asBlockEntity().getBlockState(), entity);
        if (modelBounds != null) base = base.minmax(modelBounds.move(entity.asBlockEntity().getBlockPos()));
        var adapter = entity.adapter();
        var state = entity.asBlockEntity().getBlockState();
        var attached = SnapshotCouplerRenderer.attachedBounds(state);
        if (attached != null) base = base.minmax(attached.move(entity.asBlockEntity().getBlockPos()));
        if (adapter == null || !(state.getBlock() instanceof CouplerBlockView hook)) return base;
        var complete = hook.complete();
        double x = complete.x(), z = complete.z() - adapter.thickness() / 2;
        Direction facing = hook.facing(state);
        double cx = switch (facing) { case EAST -> -z; case SOUTH -> -x; case WEST -> z; default -> x; };
        double cz = switch (facing) { case EAST -> x; case SOUTH -> -z; case WEST -> -x; default -> z; };
        double dx = facing.getAxis() == Direction.Axis.X ? adapter.thickness() / 2 : .5;
        double dz = facing.getAxis() == Direction.Axis.Z ? adapter.thickness() / 2 : .5;
        return base.minmax(new AABB(cx + .5 - dx, complete.y(), cz + .5 - dz,
                cx + .5 + dx, complete.y() + 1, cz + .5 + dz).move(entity.asBlockEntity().getBlockPos()));
    }

    private static float yaw(Direction direction) {
        return switch (direction) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; };
    }
}
