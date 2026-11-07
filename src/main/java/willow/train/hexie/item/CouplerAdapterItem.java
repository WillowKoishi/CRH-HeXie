package willow.train.hexie.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerInteraction;
import willow.train.hexie.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** A reversible converter registered by its two mating models and physical thickness in blocks. */
public class CouplerAdapterItem extends Item {
    private final String firstModel;
    private final String secondModel;
    private final double thickness;
    private final willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point offset;

    public CouplerAdapterItem(Properties properties, String firstModel, String secondModel, double thickness) {
        this(properties, firstModel, secondModel, thickness,
                new willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point(0, 0, -thickness));
    }

    protected CouplerAdapterItem(Properties properties, String firstModel, String secondModel, double thickness,
            willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point offset) {
        super(properties);
        new CouplerAdapter("hexie:registration", firstModel, secondModel, thickness, offset);
        this.firstModel = firstModel;
        this.secondModel = secondModel;
        this.thickness = thickness;
        this.offset = offset;
    }

    public CouplerAdapter adapter() {
        return new CouplerAdapter(BuiltInRegistries.ITEM.getKey(this).toString(), firstModel, secondModel, thickness, offset);
    }

    /** CUSTOM_DATA may override the registered definition, retaining the held item's model and identity. */
    public CouplerAdapter adapter(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(CouplerAdapterData.KEY)) return adapter();
        data.getCompound(CouplerAdapterData.KEY).putString("Item", BuiltInRegistries.ITEM.getKey(this).toString());
        CouplerAdapter configured = CouplerAdapterData.read(data);
        if (configured == null) return null;
        return new CouplerAdapter(BuiltInRegistries.ITEM.getKey(this).toString(), configured.firstModel(),
                configured.secondModel(), configured.thickness(), configured.offset());
    }

    /** Preserves the configured dimensions when the installed converter is dropped again. */
    public static ItemStack stack(CouplerAdapter adapter) {
        ResourceLocation id = ResourceLocation.tryParse(adapter.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (stack.isEmpty()) return stack;
        CompoundTag tag = new CompoundTag();
        CouplerAdapterData.write(tag, adapter);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CouplerBlock coupler)) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()
                || coupler.assembled(state))
            return InteractionResult.FAIL;
        // Sneaking can bypass the block's useItemOn hook and arrive directly here.
        // Preserve the same surface routing before attempting to install an adapter.
        if (coupler.geometry() != null) {
            var requested = new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                    context.getClickedFace(), pos, context.isInside());
            var surface = CouplerInteraction.classifyHit(level, pos, state, requested);
            if (surface == null) return InteractionResult.FAIL;
            if (!surface.head()) {
                if (!coupler.geometry().hasCovers()) return InteractionResult.FAIL;
                AbstractCouplerBlock.toggleCover(level, pos, state);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        CouplerAdapter value = adapter(context.getItemInHand());
        if (value == null || !(level.getBlockEntity(pos) instanceof CouplerBlockEntity entity)
                || value.otherModel(entity.adapter() == null ? coupler.model() : entity.adapter().otherModel(coupler.model())) == null)
            return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!entity.install(value)) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
