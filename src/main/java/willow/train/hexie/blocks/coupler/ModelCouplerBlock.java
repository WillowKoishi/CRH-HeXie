package willow.train.hexie.blocks.coupler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** A model-defined coupler using the shared coupling and cover interaction paths. */
public final class ModelCouplerBlock extends AbstractCouplerBlock {
    public static final MapCodec<ModelCouplerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.STRING.fieldOf("coupling_type").forGetter(block -> block.couplingType),
            ResourceLocation.CODEC.fieldOf("geometry").forGetter(block -> block.geometryResource)
    ).apply(instance, ModelCouplerBlock::new));
    private final String couplingType;
    private final ResourceLocation geometryResource;
    private final CouplerGeometry geometry;

    public ModelCouplerBlock(Properties properties, String couplingType, ResourceLocation geometryResource) {
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
        var support = context.getLevel().getBlockState(context.getClickedPos().below());
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face
                : support.hasProperty(FACING) ? support.getValue(FACING) : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public String model() { return couplingType; }
    @Override public CouplerGeometry geometry() { return geometry; }
    @Override public Axis axis() {
        return new Axis(geometry.modelAxis().x() - .5, geometry.modelAxis().z() - .5);
    }
    // Contact begins and completes at the authored mating face, not the knuckle's projecting tip.
    @Override public Point start() { return complete(); }
    @Override public Point complete() { return point(geometry.contact()); }
    private static Point point(Vec3 point) { return new Point(point.x - .5, point.y - .5, point.z - .5); }

    @Override protected VoxelShape couplerShape(BlockState state) {
        return geometry.shape(facing(state), state.getValue(BlockStateProperties.OPEN));
    }
}
