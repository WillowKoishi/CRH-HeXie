package willow.train.hexie.interaction.item.tools;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.CouplerAdapterItemView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplerAdapterView;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerAdapterDataOperations;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.core.api.vehicle.Point3;

/** A reversible converter registered by its two mating models and physical thickness in blocks. */
public class CouplerAdapterItem extends Item implements CouplerAdapterItemView {
    private final String firstModel;
    private final String secondModel;
    private final double thickness;
    private final Point3 offset;

    public CouplerAdapterItem(Properties properties, String firstModel, String secondModel, double thickness) {
        this(properties, firstModel, secondModel, thickness,
                new Point3(0, 0, -thickness));
    }

    protected CouplerAdapterItem(Properties properties, String firstModel, String secondModel, double thickness,
            Point3 offset) {
        super(properties);
        HexieBridges.coupling().couplerAdapter().create("hexie:registration", firstModel, secondModel, thickness, offset);
        this.firstModel = firstModel;
        this.secondModel = secondModel;
        this.thickness = thickness;
        this.offset = offset;
    }

    public CouplerAdapterView adapter() {
        return HexieBridges.coupling().couplerAdapter().create(BuiltInRegistries.ITEM.getKey(this).toString(), firstModel, secondModel, thickness, offset);
    }

    /** CUSTOM_DATA may override the registered definition, retaining the held item's model and identity. */
    public CouplerAdapterView adapter(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(CouplerAdapterDataOperations.KEY)) return adapter();
        data.getCompound(CouplerAdapterDataOperations.KEY).putString("Item", BuiltInRegistries.ITEM.getKey(this).toString());
        CouplerAdapterView configured = HexieBridges.couplerBlock().couplerAdapterData().read(data);
        if (configured == null) return null;
        return HexieBridges.coupling().couplerAdapter().create(BuiltInRegistries.ITEM.getKey(this).toString(), configured.firstModel(),
                configured.secondModel(), configured.thickness(), configured.offset());
    }

    /** Preserves the configured dimensions when the installed converter is dropped again. */
    public static ItemStack stack(CouplerAdapterView adapter) {
        ResourceLocation id = ResourceLocation.tryParse(adapter.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (stack.isEmpty()) return stack;
        CompoundTag tag = new CompoundTag();
        HexieBridges.couplerBlock().couplerAdapterData().write(tag, adapter);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CouplerBlockView coupler)) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()
                || coupler.assembled(state))
            return InteractionResult.FAIL;
        // Sneaking can bypass the block's useItemOn hook and arrive directly here.
        // Preserve the same surface routing before attempting to install an adapter.
        if (coupler.geometry() != null) {
            var requested = new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                    context.getClickedFace(), pos, context.isInside());
            var surface = HexieBridges.partBlock().couplerInteraction().classifyHit(level, pos, state, requested);
            if (surface == null) return InteractionResult.FAIL;
            if (!surface.head()) {
                if (!coupler.geometry().hasCovers()) return InteractionResult.FAIL;
                HexieBridges.couplerBlock().abstractCouplerBlock().toggleCover(level, pos, state);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        CouplerAdapterView value = adapter(context.getItemInHand());
        if (value == null || !(level.getBlockEntity(pos) instanceof CouplerBlockEntityView entity)
                || value.otherModel(entity.adapter() == null ? coupler.model() : entity.adapter().otherModel(coupler.model())) == null)
            return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!entity.install(value)) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
