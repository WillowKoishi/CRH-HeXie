package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeployerMovementBehaviour.class)
public class MixinDeployerMovementBehavior {

    @WrapOperation(method = "activate",
            at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    public boolean hexie$includeCustomSchematics(ItemEntry<?> instance, ItemStack itemStack,
                                                 Operation<Boolean> original) {
        if (instance == AllItems.SCHEMATIC)
            return itemStack.getItem() instanceof SchematicItem;
        return original.call(instance, itemStack);
    }

    @WrapOperation(method = "tryGrabbingItem",
            at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    public boolean hexie$excludeCustomSchematicsFromPickup(ItemEntry<?> instance, ItemStack itemStack,
                                                           Operation<Boolean> original) {
        if (instance == AllItems.SCHEMATIC)
            return itemStack.getItem() instanceof SchematicItem;
        return original.call(instance, itemStack);
    }
}
