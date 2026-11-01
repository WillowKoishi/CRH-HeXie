package willow.train.hexie.mixins.mixin;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.blocks.locos.head.HeadGeometry;

@Mixin(CollisionGetter.class)
public interface MixinHeadPlayerCollision {
    @Inject(method="getBlockCollisions",at=@At("RETURN"),cancellable=true)
    private void hexie$headShell(Entity entity,AABB box,CallbackInfoReturnable<Iterable<VoxelShape>> callback) {
        if (!(entity instanceof Player)) return;
        var extra=HeadGeometry.collisions((CollisionGetter)(Object)this,box);
        if(extra.isEmpty())return;
        var original=callback.getReturnValue();
        callback.setReturnValue(() -> Stream.concat(StreamSupport.stream(original.spliterator(),false),extra.stream()).iterator());
    }
}
