package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.SnapshotBodyAnchor;

@Mixin(value = DimensionalCarriageEntity.class, remap = false)
public abstract class MixinSnapshotBodyAnchor implements SnapshotBodyAnchor {
    @Unique private Couple<Vec3> hexie$bodyAnchors;

    @Override public void hexie$setBodyAnchor(Vec3 position, Vec3 heading) {
        hexie$bodyAnchors = Couple.create(position.add(heading), position);
    }

    @Override public Vec3 hexie$bodyPosition(Vec3 railAnchor) {
        return hexie$bodyAnchors == null ? railAnchor : hexie$bodyAnchors.getSecond();
    }

    @ModifyExpressionValue(method = {"createEntity", "alignEntity", "updatePassengerLoadout"},
            at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;positionAnchor:Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hexie$placeBody(Vec3 railAnchor) {
        return hexie$bodyPosition(railAnchor);
    }

    @ModifyExpressionValue(method = "alignEntity", at = @At(value = "FIELD",
            target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;rotationAnchors:Lnet/createmod/catnip/data/Couple;"))
    private Couple<Vec3> hexie$orientBody(Couple<Vec3> railAnchors) {
        return hexie$bodyAnchors == null ? railAnchors : hexie$bodyAnchors;
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void hexie$saveBody(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (hexie$bodyAnchors != null)
            cir.getReturnValue().put("HeXieBodyAnchors", hexie$bodyAnchors.serializeEach(VecHelper::writeNBTCompound));
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void hexie$loadBody(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        var anchors = tag.getList("HeXieBodyAnchors", Tag.TAG_COMPOUND);
        if (anchors.size() == 2) hexie$bodyAnchors = Couple.deserializeEach(anchors, VecHelper::readNBTCompound);
    }
}
