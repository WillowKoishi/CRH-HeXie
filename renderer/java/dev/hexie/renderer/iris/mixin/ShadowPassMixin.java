package dev.hexie.renderer.iris.mixin;

import dev.hexie.renderer.iris.IrisFrames;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.hexie.renderer.mc.*;
import static dev.hexie.renderer.mc.ViewPassOwnership.Pass.*;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ShadowRenderer.class,remap=false)
abstract class ShadowPassMixin {
    @Shadow @Final private boolean shouldRenderTranslucent;
    @Unique private static final String HEXIE_RENDER="renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V";
    @Unique private static final String HEXIE_COPY="Lnet/irisshaders/iris/shadows/ShadowRenderer;copyPreTranslucentDepth(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;)V";
    @Inject(method=HEXIE_RENDER,at=@At(value="INVOKE",target=HEXIE_COPY))
    private void hexie$opaque(LevelRendererAccessor level,Camera camera,CallbackInfo ci,@Share("hexieView") LocalRef<Object> view) {
        if (ClientRenderState.engine().route().tier()!=RenderSettings.Tier.IRIS) return;
        view.set(new Object());
        IrisFrames.expectShadow.accept(view.get(),shouldRenderTranslucent);
        IrisFrames.geometry.draw(view.get(),SHADOW_SOLID,camera,ShadowRenderer.MODELVIEW);
        IrisFrames.geometry.draw(view.get(),SHADOW_CUTOUT,camera,ShadowRenderer.MODELVIEW);
    }
    @Inject(method=HEXIE_RENDER,at=@At(value="INVOKE",target=HEXIE_COPY,shift=At.Shift.AFTER))
    private void hexie$transparent(LevelRendererAccessor level,Camera camera,CallbackInfo ci,@Share("hexieView") LocalRef<Object> view) {
        if (shouldRenderTranslucent && view.get()!=null)
            IrisFrames.geometry.draw(view.get(),SHADOW_TRANSLUCENT,camera,ShadowRenderer.MODELVIEW);
    }
}
