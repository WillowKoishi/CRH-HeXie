package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.init.track.TrackMaterialInit;
import java.util.*;
import org.joml.Matrix4f;
import static willow.train.hexie.client.render.track.BallastMesh.*;

/** Static GPU buffers, independent of Flywheel's backend. No per-frame vertex or light traversal. */
public final class BallastCurveBuffers {
    private static final Map<BezierConnection,Entry> CACHE=new IdentityHashMap<>();
    public static void register() {
        NeoForge.EVENT_BUS.addListener(BallastCurveBuffers::renderWorld);
        Main.bus.addListener((ModelEvent.BakingCompleted event) -> {
            BallastModels.clear();
            RenderSystem.recordRenderCall(BallastCurveBuffers::clear);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            var level=Minecraft.getInstance().level;
            CACHE.entrySet().removeIf(item -> {
                Entry e=item.getValue();
                boolean expired=e.owner.getLevel()!=level||e.owner.isRemoved()
                        ||e.owner.getConnections().get(item.getKey().getKey())!=item.getKey();
                if(expired)e.close();
                return expired;
            });
        });
    }
    public static void clear() { CACHE.values().forEach(Entry::close);CACHE.clear(); }
    private static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = event.getCamera().getPosition();
        // Create maintains this index for loaded, primary curves, irrespective of Flywheel's backend.
        for (TrackBlockEntity be : TrackBlockOutline.TRACKS_WITH_TURNS.get(level).values()) {
            if (be.isRemoved() || be.getLevel() != level) continue;
            var pos = be.getBlockPos();
            Matrix4f modelView = modelView(event.getModelViewMatrix(),
                    pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            render(be, modelView, event.getProjectionMatrix(), event.getFrustum());
        }
    }

    /** GPU vertices are endpoint-local; preserve camera rotation before applying the relative translation. */
    public static Matrix4f modelView(Matrix4f view, double x, double y, double z) {
        return new Matrix4f(view).translate((float) x, (float) y, (float) z);
    }

    /** Shared by world rendering and the live-GL regression test. */
    public static void drawBuffer(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection) {
        RenderType layer = RenderType.cutoutMipped();
        layer.setupRenderState();
        try {
            buffer.bind();
            var shader = RenderSystem.getShader();
            // ChunkOffset is vec3, not ivec3: integer literals select Uniform's incompatible overload.
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            buffer.drawWithShader(modelView, projection, shader);
        } finally {
            VertexBuffer.unbind();
            layer.clearRenderState();
        }
    }

    private static void render(TrackBlockEntity be, Matrix4f modelView, Matrix4f projection,
            net.minecraft.client.renderer.culling.Frustum frustum) {
        if(be.getLevel()==null||!(be instanceof BallastData data))return;
        for(var curve:be.getConnections().values()) {
            if(!curve.isPrimary()||curve.getMaterial()!=TrackMaterialInit.STANDARD)continue;
            if(frustum!=null&&!frustum.isVisible(curve.getBounds().inflate(2)))continue;
            Entry entry=CACHE.get(curve);
            BlockState texture=data.hexie$getBallast(curve.getKey());
            if(entry==null||entry.texture!=texture||entry.owner!=be) {
                if(entry!=null)entry.close();
                entry=new Entry(be,curve,texture);CACHE.put(curve,entry);
            }
            entry.draw(modelView, projection);
        }
    }
    private static final class Entry implements AutoCloseable {
        final TrackBlockEntity owner;
        final BlockState texture;
        final List<Quad> mesh;
        final BlockPos[] probes;
        final int[] faceProbe, lights;
        final VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
        long nextLightCheck=Long.MIN_VALUE;
        boolean uploaded;
        Entry(TrackBlockEntity owner,BezierConnection curve,BlockState texture) {
            this.owner=owner;this.texture=texture;mesh=BallastModels.curve(curve);
            Map<BlockPos,Integer> unique=new LinkedHashMap<>();
            faceProbe=new int[mesh.size()];
            for(int i=0;i<mesh.size();i++) {
                Quad q=mesh.get(i);
                Point p=q.a().p().add(q.b().p()).add(q.c().p()).add(q.d().p()).mul(.25);
                BlockPos sample=owner.getBlockPos().offset(BlockPos.containing(p.x(),p.y()+.5,p.z()));
                faceProbe[i]=unique.computeIfAbsent(sample,key->unique.size());
            }
            probes=unique.keySet().toArray(BlockPos[]::new);lights=new int[probes.length];
        }
        void draw(Matrix4f modelView, Matrix4f projection) {
            long now=owner.getLevel().getGameTime();
            if(!uploaded||now>=nextLightCheck) {
                boolean changed=!uploaded;
                for(int i=0;i<probes.length;i++) {
                    int light=LevelRenderer.getLightColor(owner.getLevel(),probes[i]);
                    if(light!=lights[i]){lights[i]=light;changed=true;}
                }
                nextLightCheck=now+10; // At most 0.5 seconds for block-light changes; sky brightness is shader-driven.
                if(changed)upload();
            }
            if(!uploaded)return;
            drawBuffer(buffer, modelView, projection);
        }
        void upload() {
            if(mesh.isEmpty())return;
            var material=BallastModels.material(texture);
            try(ByteBufferBuilder memory=new ByteBufferBuilder(Math.max(4096,mesh.size()*128))) {
                BufferBuilder builder=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
                for(int i=0;i<mesh.size();i++) {
                    Quad q=mesh.get(i);int light=lights[faceProbe[i]];
                    for(Vertex v:new Vertex[]{q.a(),q.b(),q.c(),q.d()}) {
                        Point p=v.p(),n=q.normal();
                        float shade=(float)(.6+.4*Math.max(0,n.y()));int rgb=material.color();
                        builder.addVertex((float)p.x(),(float)p.y(),(float)p.z())
                                .setColor((int)(((rgb>>16)&255)*shade),(int)(((rgb>>8)&255)*shade),(int)((rgb&255)*shade),255)
                                .setUv(material.sprite().getU((float)(.0001+v.u()*.9998)),material.sprite().getV((float)(.0001+v.v()*.9998)))
                                .setLight(light).setNormal((float)n.x(),(float)n.y(),(float)n.z());
                    }
                }
                buffer.bind();buffer.upload(builder.buildOrThrow());VertexBuffer.unbind();uploaded=true;
            }
        }
        @Override public void close(){buffer.close();}
    }
}
