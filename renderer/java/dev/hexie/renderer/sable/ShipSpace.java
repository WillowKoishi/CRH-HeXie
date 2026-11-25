package dev.hexie.renderer.sable;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3d;
import java.util.*;
import net.minecraft.core.BlockPos;

public final class ShipSpace {
    public static boolean appendCasters(ClientLevel level,Vec light,double radius,float partial,List<ForwardLight.Box> boxes,int limit) {
        var container=SubLevelContainer.getContainer(level);
        if (container==null) return true;
        for (var ship:container.getAllSubLevels()) {
            var pose=ship.renderPose(partial);
            var bounds=ship.getPlot().getBoundingBox();
            if (bounds==null) return false;
            var r=new Vec(radius,radius,radius);
            var local=transform(pose,light.minus(r),light.plus(r),true);
            int x0=Math.max(bounds.minX(),(int)Math.floor(local.low().x()));
            int x1=Math.min(bounds.maxX(),(int)Math.floor(local.high().x()));
            int y0=Math.max(bounds.minY(),(int)Math.floor(local.low().y()));
            int y1=Math.min(bounds.maxY(),(int)Math.floor(local.high().y()));
            int z0=Math.max(bounds.minZ(),(int)Math.floor(local.low().z()));
            int z1=Math.min(bounds.maxZ(),(int)Math.floor(local.high().z()));
            if (x0>x1 || y0>y1 || z0>z1) continue;
            if ((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>131072) return false;
            var cell=new BlockPos.MutableBlockPos();
            for (int z=z0;z<=z1;z++) for (int x=x0;x<=x1;x++) {
                if (!level.hasChunk(x>>4,z>>4)) return false;
                for (int y=y0;y<=y1;y++) {
                    if (level.getBlockState(cell.set(x,y,z)).isAir()) continue;
                    if (boxes.size()>=limit) return false;
                    boxes.add(transform(pose,new Vec(x,y,z),new Vec(x+1,y+1,z+1),false));
                }
            }
        }
        return true;
    }
    private static ForwardLight.Box transform(Pose3dc pose,Vec min,Vec max,boolean inverse) {
        Vec lo=null,hi=null;
        for (int i=0;i<8;i++) {
            var p=new Vector3d((i&1)==0?min.x():max.x(),(i&2)==0?min.y():max.y(),(i&4)==0?min.z():max.z());
            if (inverse) pose.transformPositionInverse(p); else pose.transformPosition(p);
            var v=new Vec(p.x,p.y,p.z);
            lo=lo==null?v:Vec.min(lo,v); hi=hi==null?v:Vec.max(hi,v);
        }
        return new ForwardLight.Box(lo,hi);
    }
}
