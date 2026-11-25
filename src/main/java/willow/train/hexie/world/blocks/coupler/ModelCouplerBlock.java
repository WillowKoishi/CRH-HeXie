package willow.train.hexie.world.blocks.coupler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.collision.CouplerCollision;
import willow.train.hexie.world.collision.CouplerGeometry;
import willow.train.hexie.world.placement.CouplerPlacement;

/** A model-defined coupler using the shared coupling and cover interaction paths. */
public final class ModelCouplerBlock extends AbstractCouplerBlock implements CouplerBlockBridge.ModelCouplerBlockView {
    public static final MapCodec<ModelCouplerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.STRING.fieldOf("coupling_type").forGetter(block -> block.couplingType),
            ResourceLocation.CODEC.fieldOf("geometry").forGetter(block -> block.geometryResource)
    ).apply(instance, ModelCouplerBlock::new));
    private final String couplingType;
    private final ResourceLocation geometryResource;
    private final CouplerGeometry geometry;

    public ModelCouplerBlock(BlockBehaviour.Properties properties, String couplingType, ResourceLocation geometryResource) {
        super(properties);
        this.couplingType = ResourceLocation.parse(couplingType).toString();
        this.geometryResource = geometryResource;
        this.geometry = CouplerGeometry.load("/data/" + geometryResource.getNamespace() + "/"
                + geometryResource.getPath() + ".json");
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.OPEN, false));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.OPEN);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return CouplerPlacement.ForModelCouplerBlock.getStateForPlacement(this, context);
    }

    @Override public String model() { return couplingType; }
    @Override public CouplerGeometry geometry() { return geometry; }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() {
        return HexieBridges.coupling().createCouplingEndpointAxis(geometry.modelAxis().x() - .5, geometry.modelAxis().z() - .5);
    }
    // Contact begins and completes at the authored mating face, not the knuckle's projecting tip.
    @Override public Point3 start() { return complete(); }
    @Override public Point3 complete() { return point(geometry.contact()); }
    private static Point3 point(Vec3 point) { return new Point3(point.x - .5, point.y - .5, point.z - .5); }

    @Override public VoxelShape couplerShape(BlockState state) {
        return CouplerCollision.ForModelCouplerBlock.couplerShape(this, state);
    }
}
