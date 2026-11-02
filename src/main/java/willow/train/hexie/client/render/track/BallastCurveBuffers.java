package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.init.track.TrackMaterialInit;
import java.util.*;
import org.joml.Matrix4f;
import static willow.train.hexie.client.render.track.BallastMesh.*;

/** Tick-maintained spatial index; static GPU pages with local light invalidation. */
public final class BallastCurveBuffers {
    private static final Map<BezierConnection,Entry> CACHE=new IdentityHashMap<>();
    private static final Map<BezierConnection,Indexed> INDEX=new IdentityHashMap<>();
    private static final Map<BlockPos,Bucket> SPACE=new HashMap<>();
    private static final Set<BezierConnection> VISIBLE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static Object indexedLevel;
    private record Indexed(TrackBlockEntity owner, List<BlockPos> buckets) {}
    private static final class Bucket {
        final AABB bounds;
        final Set<BezierConnection> curves=Collections.newSetFromMap(new IdentityHashMap<>());
        Bucket(BlockPos p) { bounds=new AABB(p.getX()*16,p.getY()*16,p.getZ()*16,p.getX()*16+16,p.getY()*16+16,p.getZ()*16+16); }
    }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(BallastCurveBuffers::renderWorld);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> updateIndex());
        Main.bus.addListener((ModelEvent.BakingCompleted event) -> {
            BallastModels.clear(); RenderSystem.recordRenderCall(BallastCurveBuffers::clear);
        });
    }
    public static void clear() {
        CACHE.values().forEach(Entry::close);CACHE.clear();INDEX.clear();SPACE.clear();VISIBLE.clear();indexedLevel=null;
    }
    private static void remove(BezierConnection curve) {
        var old=INDEX.remove(curve);
        if(old!=null) for(var key:old.buckets) {
            var bucket=SPACE.get(key);
            if(bucket!=null) { bucket.curves.remove(curve);if(bucket.curves.isEmpty())SPACE.remove(key); }
        }
        var cached=CACHE.remove(curve);if(cached!=null)cached.close();
    }
    private static void updateIndex() {
        var level=Minecraft.getInstance().level;
        if(level!=indexedLevel) {clear();indexedLevel=level;}
        if(level==null)return;
        var alive=Collections.newSetFromMap(new IdentityHashMap<BezierConnection,Boolean>());
        for(var owner:TrackBlockOutline.TRACKS_WITH_TURNS.get(level).values()) {
            if(owner.isRemoved()||owner.getLevel()!=level||!(owner instanceof BallastData))continue;
            for(var curve:owner.getConnections().values()) {
                if(!curve.isPrimary()||curve.getMaterial()!=TrackMaterialInit.STANDARD)continue;
                alive.add(curve);
                var old=INDEX.get(curve);if(old!=null&&old.owner==owner)continue;
                if(old!=null)remove(curve);
                var b=curve.getBounds().inflate(2);var keys=new ArrayList<BlockPos>();
                for(int x=(int)Math.floor(b.minX/16);x<=Math.floor(b.maxX/16);x++)
                    for(int y=(int)Math.floor(b.minY/16);y<=Math.floor(b.maxY/16);y++)
                        for(int z=(int)Math.floor(b.minZ/16);z<=Math.floor(b.maxZ/16);z++) {
                            var key=new BlockPos(x,y,z);keys.add(key);
                            SPACE.computeIfAbsent(key,Bucket::new).curves.add(curve);
                        }
                INDEX.put(curve,new Indexed(owner,List.copyOf(keys)));
            }
        }
        for(var curve:new ArrayList<>(INDEX.keySet()))if(!alive.contains(curve))remove(curve);
    }
    private static void renderWorld(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        var level=Minecraft.getInstance().level;if(level==null||level!=indexedLevel)return;
        var frustum=event.getFrustum();VISIBLE.clear();
        for(var bucket:SPACE.values())if(frustum==null||frustum.isVisible(bucket.bounds))VISIBLE.addAll(bucket.curves);
        if(VISIBLE.isEmpty())return;
        var camera=event.getCamera().getPosition();
        RenderType layer=RenderType.cutoutMipped();layer.setupRenderState();
        try {
            for(var curve:VISIBLE) {
                var indexed=INDEX.get(curve);var owner=indexed.owner;
                if(owner.isRemoved()||frustum!=null&&!frustum.isVisible(curve.getBounds().inflate(2)))continue;
                var texture=((BallastData)owner).hexie$getBallast(curve.getKey());
                var entry=CACHE.get(curve);
                if(entry==null||entry.texture!=texture) {
                    if(entry!=null)entry.close();
                    entry=new Entry(owner,curve,texture);CACHE.put(curve,entry);
                }
                var pos=owner.getBlockPos();
                entry.draw(modelView(event.getModelViewMatrix(),pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z),
                        event.getProjectionMatrix(),frustum);
            }
        } finally {VertexBuffer.unbind();layer.clearRenderState();}
    }
    public static Matrix4f modelView(Matrix4f view,double x,double y,double z) {
        return new Matrix4f(view).translate((float)x,(float)y,(float)z);
    }
    /** Standalone caller API; the world path shares one render-state setup for all pages. */
    public static void drawBuffer(VertexBuffer buffer,Matrix4f modelView,Matrix4f projection) {
        var layer=RenderType.cutoutMipped();layer.setupRenderState();
        try {drawBound(buffer,modelView,projection);} finally {VertexBuffer.unbind();layer.clearRenderState();}
    }
    private static void drawBound(VertexBuffer buffer,Matrix4f modelView,Matrix4f projection) {
        buffer.bind();var shader=RenderSystem.getShader();
        if(shader.CHUNK_OFFSET!=null)shader.CHUNK_OFFSET.set(0.0F,0.0F,0.0F);
        buffer.drawWithShader(modelView,projection,shader);
    }
    private static final class Entry implements AutoCloseable {
        final TrackBlockEntity owner;
        final BlockState texture;
        final List<Quad> mesh;
        final BallastModels.Material material;
        final BlockPos[] probes;
        final int[] faceProbe,lights;
        final List<Page> pages=new ArrayList<>();
        long nextLightCheck=Long.MIN_VALUE;
        final int stagger;
        Entry(TrackBlockEntity owner,BezierConnection curve,BlockState texture) {
            this.owner=owner;this.texture=texture;mesh=BallastModels.curve(curve);material=BallastModels.material(texture);
            stagger=Math.floorMod(owner.getBlockPos().hashCode(),10);
            Map<BlockPos,Integer> unique=new LinkedHashMap<>();faceProbe=new int[mesh.size()];
            for(int i=0;i<mesh.size();i++) {
                Quad q=mesh.get(i);Point p=q.a().p().add(q.b().p()).add(q.c().p()).add(q.d().p()).mul(.25);
                var sample=owner.getBlockPos().offset(BlockPos.containing(p.x(),p.y()+.5,p.z()));
                faceProbe[i]=unique.computeIfAbsent(sample,key->unique.size());
            }
            probes=unique.keySet().toArray(BlockPos[]::new);lights=new int[probes.length];
            for(int i=0;i<mesh.size();i+=128)pages.add(new Page(i,Math.min(mesh.size(),i+128)));
        }
        void draw(Matrix4f view,Matrix4f projection,net.minecraft.client.renderer.culling.Frustum frustum) {
            long now=owner.getLevel().getGameTime();
            if(now>=nextLightCheck) {
                var changed=new BitSet(probes.length);
                for(int i=0;i<probes.length;i++) {
                    int light=LevelRenderer.getLightColor(owner.getLevel(),probes[i]);
                    if(light!=lights[i]) {lights[i]=light;changed.set(i);}
                }
                for(var page:pages)for(int i=page.start;i<page.end;i++)
                    if(changed.get(faceProbe[i])) {page.dirty=true;break;}
                nextLightCheck=now+1+Math.floorMod(stagger-(int)Math.floorMod(now+1,10),10);
            }
            for(var page:pages) {
                if(frustum!=null&&!frustum.isVisible(page.bounds))continue;
                if(page.dirty)page.upload();
                drawBound(page.buffer,view,projection);
            }
        }
        private final class Page implements AutoCloseable {
            final int start,end;
            final AABB bounds;
            final VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
            boolean dirty=true;
            Page(int start,int end) {
                this.start=start;this.end=end;
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
                for(int i=start;i<end;i++)for(var v:List.of(mesh.get(i).a(),mesh.get(i).b(),mesh.get(i).c(),mesh.get(i).d())) {
                    var p=v.p();minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
                    maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());
                }
                bounds=new AABB(minX,minY,minZ,maxX,maxY,maxZ).move(owner.getBlockPos()).inflate(.01);
            }
            void upload() {
                try(var memory=new ByteBufferBuilder((end-start)*128)) {
                    var builder=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
                    for(int i=start;i<end;i++) {
                        var q=mesh.get(i);var n=q.normal();int rgb=material.color();float shade=(float)(.6+.4*Math.max(0,n.y()));
                        emit(builder,q.a(),n,rgb,shade,lights[faceProbe[i]],material.sprite());
                        emit(builder,q.b(),n,rgb,shade,lights[faceProbe[i]],material.sprite());
                        emit(builder,q.c(),n,rgb,shade,lights[faceProbe[i]],material.sprite());
                        emit(builder,q.d(),n,rgb,shade,lights[faceProbe[i]],material.sprite());
                    }
                    buffer.bind();buffer.upload(builder.buildOrThrow());dirty=false;
                }
            }
            @Override public void close(){buffer.close();}
        }
        @Override public void close(){pages.forEach(Page::close);}
    }
    private static void emit(BufferBuilder builder,Vertex v,Point n,int rgb,float shade,int light,
                             net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        var p=v.p();builder.addVertex((float)p.x(),(float)p.y(),(float)p.z())
                .setColor((int)(((rgb>>16)&255)*shade),(int)(((rgb>>8)&255)*shade),(int)((rgb&255)*shade),255)
                .setUv(sprite.getU((float)(.0001+v.u()*.9998)),sprite.getV((float)(.0001+v.v()*.9998)))
                .setLight(light).setNormal((float)n.x(),(float)n.y(),(float)n.z());
    }
}
