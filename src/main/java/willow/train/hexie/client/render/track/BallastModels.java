package willow.train.hexie.client.render.track;

import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static willow.train.hexie.client.render.track.BallastMesh.*;

public final class BallastModels {
    public record Material(TextureAtlasSprite sprite, int color) {}
    private record Key(TrackShape shape, BlockState texture) {}
    private static final Map<Key,List<BakedQuad>> STRAIGHTS = new java.util.concurrent.ConcurrentHashMap<>();
    public static void clear() { STRAIGHTS.clear(); }
    public static Material material(BlockState state) {
        var mc=Minecraft.getInstance();
        var model=mc.getBlockRenderer().getBlockModel(state);
        var quads=model.getQuads(state,Direction.UP,RandomSource.create(42));
        var sprite=quads.isEmpty()?model.getParticleIcon():quads.getFirst().getSprite();
        int color=quads.isEmpty()||!quads.getFirst().isTinted()?0xffffff:
                mc.getBlockColors().getColor(state,null,null,quads.getFirst().getTintIndex());
        return new Material(sprite,color);
    }
    public static List<BakedQuad> straight(TrackShape shape,BlockState texture) {
        return STRAIGHTS.computeIfAbsent(new Key(shape,texture),key -> {
            if(shape==TrackShape.NONE)return List.of();
            List<BakedQuad> result=new ArrayList<>();
            var material=material(texture);
            for(Vec3 axis:shape.getAxes()) {
                Vec3 center=new Vec3(.5,axis.y==0?0:.5,.5);
                Point side=point(new Vec3(axis.z,0,-axis.x).normalize());
                // One span for straight rails. Only atlas repeats introduce subdivisions.
                var mesh=sweep(List.of(new Frame(point(center.subtract(axis.scale(.5))),side),
                        new Frame(point(center.add(axis.scale(.5))),side)));
                for(Quad quad:mesh)result.add(bake(quad,material));
            }
            return List.copyOf(result);
        });
    }
    public static List<Quad> curve(BezierConnection curve) {
        int count=Math.max(2,(int)Math.ceil(curve.getLength()*2));
        List<Frame> frames=new ArrayList<>();
        Vec3 origin=Vec3.atLowerCornerOf(curve.bePositions.getFirst());
        for(int i=0;i<=count;i++) {
            double t=i/(double)count;
            Vec3 tangent=curve.getPosition(Math.min(1,t+.0001)).subtract(curve.getPosition(Math.max(0,t-.0001)));
            frames.add(new Frame(point(curve.getPosition(t).subtract(origin)),point(new Vec3(tangent.z,0,-tangent.x).normalize())));
        }
        return sweep(frames);
    }
    private static Point point(Vec3 v){return new Point(v.x,v.y,v.z);}
    private static BakedQuad bake(Quad quad,Material material) {
        int[] data=new int[32];
        Vertex[] vertices={quad.a(),quad.b(),quad.c(),quad.d()};
        Point n=quad.normal();
        int normal=((byte)(n.x()*127)&255)|(((byte)(n.y()*127)&255)<<8)|(((byte)(n.z()*127)&255)<<16);
        int rgb=material.color();
        int color=0xff000000|((rgb&255)<<16)|(rgb&0xff00)|((rgb>>16)&255);
        for(int i=0;i<4;i++) {
            Vertex v=vertices[i];int o=i*8;
            data[o]=Float.floatToRawIntBits((float)v.p().x()); data[o+1]=Float.floatToRawIntBits((float)v.p().y());
            data[o+2]=Float.floatToRawIntBits((float)v.p().z());data[o+3]=color;
            data[o+4]=Float.floatToRawIntBits(material.sprite().getU((float)(.0001+v.u()*.9998)));
            data[o+5]=Float.floatToRawIntBits(material.sprite().getV((float)(.0001+v.v()*.9998)));data[o+7]=normal;
        }
        return new BakedQuad(data,-1,Direction.getNearest((float)n.x(),(float)n.y(),(float)n.z()),material.sprite(),true);
    }
}
