package willow.train.hexie.ui.flow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge.BogeyFloorDataOperations;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.datafix.Constants;

/** Rest poses for the built-in carriage assets whose usual rendering is supplied by the train mod. */
final class CarriageFlowModels {
    static final ModelResourceLocation STANDARD_TRACK = model("standard_track");
    private static final Map<ResourceLocation, ModelResourceLocation> BOGEYS =
            Stream.of("pk209p_bogey", "pk209p_no_motor_bogey", "sw160_bogey", "sw220k_bogey")
                    .collect(Collectors.toUnmodifiableMap(HexieConstants::asResource, CarriageFlowModels::model));

    private CarriageFlowModels() {}

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(HexieConstants.asResource("block/carriage_preview/" + name));
    }

    static void register(ModelEvent.RegisterAdditional event) {
        event.register(STANDARD_TRACK);
        BOGEYS.values().forEach(event::register);
    }

    static ModelResourceLocation bogey(BlockState state) {
        return BOGEYS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    static void renderBogey(ModelResourceLocation model, CarriageFlowAssets.Cell cell, PoseStack pose, MultiBufferSource buffers) {
        var data = cell.data() == null ? new CompoundTag() : cell.data().getCompound("BogeyData");
        var floor = HexieBridges.worldBlock().bogeyFloorData();
        pose.pushPose();
        pose.translate(.5, .5, .5);
        if (cell.state().getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X)
            pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.translate(0, -1.5 - 1 / 128d + TrackRegistryBridge.RAIL_HEIGHT_INCREASE,
                floor.longitudinalSetting().blocks(floor.longitudinal(data)));
        if (data.getBoolean(BogeyFloorDataOperations.REVERSED)) pose.scale(1, 1, -1);
        String direction = data.getString(Constants.BOGEY_ASSEMBLY_DIRECTION_KEY);
        if (model.equals(BOGEYS.get(HexieConstants.asResource("pk209p_bogey")))
                && ("south".equalsIgnoreCase(direction) || "east".equalsIgnoreCase(direction)))
            pose.mulPose(Axis.YP.rotationDegrees(180));
        draw(model, cell.state(), pose, buffers);
        pose.popPose();
    }

    static void draw(ModelResourceLocation id, BlockState state, PoseStack pose, MultiBufferSource buffers) {
        var mc = Minecraft.getInstance();
        var baked = mc.getModelManager().getModel(id);
        for (var layer : baked.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY)) {
            var buffer = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer, false));
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffer, state, baked, 1, 1, 1,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, layer);
        }
    }
}
