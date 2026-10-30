package willow.train.hexie.client.render.blockentity;

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
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.TestCouplerBlock;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** Adds the registered item model at the original hook head, in world and carriage rendering. */
public final class CouplerBlockEntityRenderer implements BlockEntityRenderer<CouplerBlockEntity> {
    private static final Map<String, ItemStack> ADAPTER_ITEMS = new HashMap<>();

    public CouplerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(CouplerBlockEntity entity, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light, int overlay) {
        var adapter = entity.adapter();
        var state = entity.getBlockState();
        if (adapter == null || !(state.getBlock() instanceof CouplerBlock hook)
                || state.getOptionalValue(TestCouplerBlock.ASSEMBLED).orElse(false)) return;
        stack.pushPose();
        stack.translate(.5, .5, .5);
        stack.mulPose(Axis.YP.rotationDegrees(yaw(hook.facing(state))));
        var complete = hook.complete();
        stack.translate(complete.x(), complete.y(), complete.z());
        renderAdapter(adapter, !hook.model().equals(adapter.firstModel()), entity.getLevel(), stack, buffers, light, overlay);
        stack.popPose();
    }

    /** Origin is the original model's complete point, with local north pointing out of the hook. */
    public static void renderAdapter(CouplerAdapter adapter, boolean reversed, Level level, PoseStack stack,
                                     MultiBufferSource buffers, int light, int overlay) {
        if (!adapter.layers().isEmpty()) {
            stack.pushPose();
            String model = reversed ? adapter.secondModel() : adapter.firstModel();
            for (var layer : reversed ? adapter.layers().reversed() : adapter.layers()) {
                renderAdapter(layer, !model.equals(layer.firstModel()), level, stack, buffers, light, overlay);
                model = layer.otherModel(model);
                stack.translate(0, 0, -layer.thickness());
            }
            stack.popPose();
            return;
        }
        var item = ADAPTER_ITEMS.computeIfAbsent(adapter.itemId(), id -> {
            var location = ResourceLocation.tryParse(id);
            return location == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(location));
        });
        if (item.isEmpty()) return;
        stack.pushPose();
        // ItemRenderer centres [0,1]^3. Its z=1 face meets the old head and z=0 is the new head.
        stack.translate(0, 0, -adapter.thickness() / 2);
        stack.scale(1, 1, (float) adapter.thickness());
        if (reversed) stack.mulPose(Axis.YP.rotationDegrees(180));
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.NONE,
                light, overlay, stack, buffers, level, 0);
        stack.popPose();
    }

    @Override public AABB getRenderBoundingBox(CouplerBlockEntity entity) {
        var base = new AABB(entity.getBlockPos());
        var adapter = entity.adapter();
        var state = entity.getBlockState();
        if (adapter == null || !(state.getBlock() instanceof CouplerBlock hook)) return base;
        var complete = hook.complete();
        double x = complete.x(), z = complete.z() - adapter.thickness() / 2;
        Direction facing = hook.facing(state);
        double cx = switch (facing) { case EAST -> -z; case SOUTH -> -x; case WEST -> z; default -> x; };
        double cz = switch (facing) { case EAST -> x; case SOUTH -> -z; case WEST -> -x; default -> z; };
        double dx = facing.getAxis() == Direction.Axis.X ? adapter.thickness() / 2 : .5;
        double dz = facing.getAxis() == Direction.Axis.Z ? adapter.thickness() / 2 : .5;
        return base.minmax(new AABB(cx + .5 - dx, complete.y(), cz + .5 - dz,
                cx + .5 + dx, complete.y() + 1, cz + .5 + dz).move(entity.getBlockPos()));
    }

    private static float yaw(Direction direction) {
        return switch (direction) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; };
    }
}
