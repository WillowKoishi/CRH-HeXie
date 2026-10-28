package willow.train.hexie.client.carriage.browser;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.util.*;
import java.util.function.Consumer;

/** Preserve native materials/formats, then apply screen-door fading to the completed image. */
final class CarriageRenderLayer implements AutoCloseable {
    private record Batch(RenderType type, VertexBuffer buffer) {}
    private final List<Batch> batches = new ArrayList<>();
    private static RenderTarget target;
    private final Set<net.minecraft.resources.ResourceLocation> signTextures = new HashSet<>();

    static CarriageRenderLayer capture(Consumer<MultiBufferSource> render) {
        var result = new CarriageRenderLayer();
        Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        try {
            render.accept(new willow.train.hexie.client.sign.SignTextRenderer.TextureOwner() {
                @Override public void retainSignTexture(net.minecraft.resources.ResourceLocation texture) {
                    if (result.signTextures.add(texture)) willow.train.hexie.client.sign.SignTextRenderer.retain(texture);
                }
                @Override public VertexConsumer getBuffer(RenderType type) {
                    return builders.computeIfAbsent(type, key -> {
                var bytes = new ByteBufferBuilder(65536);
                memory.put(key, bytes);
                return new BufferBuilder(bytes, key.mode(), key.format());
                    });
                }
            });
            for (var entry : builders.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                result.batches.add(new Batch(entry.getKey(), buffer));
                buffer.bind();
                buffer.upload(mesh);
            }
            return result;
        } catch (RuntimeException e) { result.close(); throw e; }
        finally {
            VertexBuffer.unbind();
            memory.values().forEach(ByteBufferBuilder::close);
        }
    }

    void drawNative(Matrix4f transform) {
        for (Batch batch : batches) {
            batch.type.setupRenderState();
            try {
                // GUI previews must not be redirected to Fabulous world render targets.
                target.bindWrite(false);
                RenderSystem.disableCull();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader.getUniform("ChunkOffset") != null) shader.getUniform("ChunkOffset").set(0f, 0f, 0f);
                batch.buffer.bind();
                batch.buffer.drawWithShader(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(transform),
                        RenderSystem.getProjectionMatrix(), shader);
            } finally {
                VertexBuffer.unbind();
                batch.type.clearRenderState();
            }
        }
    }

    static void composite(float opacity, Runnable draw) {
        if (opacity <= .001f || CarriagePreview.shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            // Clear the entire scratch image, while retaining the caller's GUI clipping for drawing.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (target == null) target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            else if (target.width != main.width || target.height != main.height)
                target.resize(main.width, main.height, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(false);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            draw.run();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> CarriagePreview.shader);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShaderTexture(1, target.getDepthTextureId());
            RenderSystem.setShaderColor(1, 1, 1, opacity);
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(256)) {
                BufferBuilder quad = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                quad.addVertex(-1, -1, 0).setUv(0, 0);
                quad.addVertex(1, -1, 0).setUv(1, 0);
                quad.addVertex(1, 1, 0).setUv(1, 1);
                quad.addVertex(-1, 1, 0).setUv(0, 1);
                BufferUploader.drawWithShader(quad.buildOrThrow());
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.enableCull();
            RenderSystem.disableDepthTest();
        }
    }

    static void releaseTarget() {
        if (target != null) { target.destroyBuffers(); target = null; }
    }

    @Override public void close() {
        batches.forEach(batch -> batch.buffer.close());
        batches.clear();
        signTextures.forEach(willow.train.hexie.client.sign.SignTextRenderer::release);
        signTextures.clear();
    }
}
