package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.client.SchematicEditScreen;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.init.item.SchematicItemInit;

@Mixin(SchematicEditScreen.class)
public class MixinSchematicEditScreen {

    @WrapOperation(method = "renderWindow",
            at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;asStack()Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    public ItemStack hexie$renderCustomSchematicIcon(ItemEntry<?> instance,
                                                     Operation<ItemStack> original) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (instance == AllItems.SCHEMATIC && player != null
                && player.getItemInHand(InteractionHand.MAIN_HAND).is(SchematicItemInit.SCHEMATIC_25G.get())) {
            return player.getItemInHand(InteractionHand.MAIN_HAND);
        }
        return original.call(instance);
    }
}
