package dev.hexie.renderer;

import java.util.*;

/** Positive camera Z is forward. Only occupied receiver froxels need a light list. */
public final class FroxelClusterer {
    public record Camera(Pose cameraToWorld,int width,int height,double verticalFov,double near,double far) {
        public Camera {
            if (width<=0 || height<=0 || !(verticalFov>0 && verticalFov<Math.PI && near>0 && far>near))
                throw new IllegalArgumentException("camera frustum");
        }
    }
    public record Cell(int x,int y,int z) {}
    public record Grid(List<Bounds> boxes,int[] receiverCells) {}
    private final Camera camera;
    private final RendererConfig config;
    private final double tx,ty;
    private final Pose inverseRotation;
    public FroxelClusterer(Camera camera,RendererConfig config) {
        this.camera=camera; this.config=config;
        Pose p=camera.cameraToWorld;
        inverseRotation=new Pose(Vec.ZERO,-p.x(),-p.y(),-p.z(),p.w());
        ty=Math.tan(camera.verticalFov*0.5); tx=ty*camera.width/camera.height;
    }
    public Grid cluster(List<Vec> receivers) {
        List<Bounds> boxes=new ArrayList<>();
        Map<Cell,Integer> ids=new HashMap<>(); int[] found=new int[receivers.size()];
        Arrays.fill(found,-1);
        for (int i=0;i<found.length && config.pointLights();i++) {
            Vec p=receivers.get(i), v=inverseRotation.apply(p.minus(camera.cameraToWorld.translation()));
            if (v.z()<camera.near || v.z()>camera.far) continue;
            double x=(1+v.x()/(v.z()*tx))*camera.width*0.5, y=(1+v.y()/(v.z()*ty))*camera.height*0.5;
            if (x<0 || y<0 || x>camera.width || y>camera.height) continue;
            int tile=config.tilePixels(), layers=config.depthLayers();
            var cell=new Cell(Math.min((int)x,camera.width-1)/tile,Math.min((int)y,camera.height-1)/tile,
                    Math.min(layers-1,(int)(layers*Math.log(v.z()/camera.near)/Math.log(camera.far/camera.near))));
            int index=ids.computeIfAbsent(cell,key->{boxes.add(bounds(key)); return boxes.size()-1;});
            boxes.set(index,boxes.get(index).union(Bounds.point(p)));
            found[i]=index;
        }
        return new Grid(List.copyOf(boxes),found);
    }
    private Bounds bounds(Cell cell) {
        Bounds result=null;
        for (int k=0;k<8;k++) {
            double z=camera.near*Math.pow(camera.far/camera.near,(cell.z+((k>>2)&1))/(double)config.depthLayers());
            double x=Math.min(camera.width,((long)cell.x+(k&1))*config.tilePixels());
            double y=Math.min(camera.height,((long)cell.y+((k>>1)&1))*config.tilePixels());
            Vec corner=camera.cameraToWorld.apply(new Vec((2*x/camera.width-1)*tx*z,(2*y/camera.height-1)*ty*z,z));
            result=result==null?Bounds.point(corner):result.union(Bounds.point(corner));
        }
        return result;
    }
}
