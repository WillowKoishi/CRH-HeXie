package willow.train.hexie.ui.flow;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.util.Map;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import willow.train.hexie.core.HexieConstants;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowClient.java
final class CarriageFlowPreviewCache {
    static void shaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), HexieConstants.asResource("carriage_preview"),
                    DefaultVertexFormat.POSITION_TEX), shader -> CarriageFlowPreview.shader = shader);
        } catch (IOException e) { throw new IllegalStateException("Cannot load carriage preview shader", e); }
    }

    static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            clearPreviews();
            CarriageFlowPages.reload(resources);
            var requested = new java.util.LinkedHashSet<net.minecraft.resources.ResourceLocation>();
            for (var page : CarriageFlowPages.all()) requested.addAll(page.carriages());
            assets = CarriageFlowAssets.load(resources, java.util.List.copyOf(requested));
            CarriageFlowClient.resourceGeneration++;
        });
    }

    private static CarriageFlowAssets.Catalog assets = new CarriageFlowAssets.Catalog(java.util.List.of(), java.util.List.of());
    private static final Map<net.minecraft.resources.ResourceLocation, CarriageFlowPreview> PREVIEWS = new java.util.HashMap<>();
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> FAILED = new java.util.HashSet<>();
    private static net.minecraft.client.multiplayer.ClientLevel previewLevel;

    private static void clearPreviews() {
        // Views borrow meshes; only the cache owns their lifetime.
        CarriageFlowClient.closeViews();
        PREVIEWS.values().forEach(CarriageFlowPreview::close);
        PREVIEWS.clear();
        FAILED.clear();
        previewLevel = null;
        CarriageFlowRenderLayer.releaseTarget();
    }

    static void tickCache(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (previewLevel != mc.level) clearPreviews();
        // Model/texture reload must finish before capturing native renderers.
        if (mc.level != null && mc.getOverlay() == null) warmPreviews();
    }

    static void warmPreviews() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (previewLevel != mc.level) clearPreviews();
        if (mc.level == null) return;
        previewLevel = mc.level;
        for (var model : assets.models()) {
            if (PREVIEWS.containsKey(model.id()) || FAILED.contains(model.id())) continue;
            try { PREVIEWS.put(model.id(), new CarriageFlowPreview(model)); }
            catch (RuntimeException e) {
                FAILED.add(model.id());
                HexieConstants.LOGGER.warn("Could not bake carriage preview {}", model.id(), e);
            }
        }
    }

    static CarriageFlowAssets.Catalog catalog(CarriageFlowPages.Page page) {
        var byId = new java.util.HashMap<net.minecraft.resources.ResourceLocation, CarriageFlowAssets.Model>();
        for (var model : assets.models()) byId.put(model.id(), model);
        var models = new java.util.ArrayList<CarriageFlowAssets.Model>();
        for (var id : page.carriages()) if (PREVIEWS.containsKey(id)) models.add(byId.get(id));
        return new CarriageFlowAssets.Catalog(java.util.List.copyOf(models), assets.errors());
    }

    static CarriageFlowPreview preview(net.minecraft.resources.ResourceLocation id) { return PREVIEWS.get(id); }
}
