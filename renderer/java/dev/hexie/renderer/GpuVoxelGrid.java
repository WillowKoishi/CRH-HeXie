package dev.hexie.renderer;

import java.util.List;

/** Closed AABB voxelization. Occupied candidates go to the same-frame triangle BVH. */
final class GpuVoxelGrid {
    final int[] origin=new int[3], size=new int[3];
    int[] data={0};
    boolean enabled;
    GpuVoxelGrid(List<Triangle> triangles,double width,long bytes) {
        if (triangles.isEmpty() || !(width>0) || Math.scalb(1,Math.getExponent(width))!=width) return;
        Vec low=triangles.get(0).low(),high=triangles.get(0).high();
        for (var t:triangles) { low=Vec.min(low,t.low()); high=Vec.max(high,t.high()); }
        long count=1;
        for (int axis=0;axis<3;axis++) {
            double a=Math.floor(Math.nextDown(low.at(axis)/width)),b=Math.floor(high.at(axis)/width);
            if (a < -1001 || b>1001) return;
            origin[axis]=(int)a; size[axis]=(int)b-origin[axis]+1;
            count*=size[axis]; if (count>=bytes/4 || count>=Integer.MAX_VALUE) return;
        }
        data=new int[1+(int)count];
        for (var t:triangles) {
            int[] a=new int[3],b=new int[3];
            for (int i=0;i<3;i++) {
                a[i]=(int)Math.floor(Math.nextDown(t.low().at(i)/width))-origin[i];
                b[i]=(int)Math.floor(t.high().at(i)/width)-origin[i];
            }
            for (int z=a[2];z<=b[2];z++) for (int y=a[1];y<=b[1];y++)
                java.util.Arrays.fill(data,1+(z*size[1]+y)*size[0]+a[0],2+(z*size[1]+y)*size[0]+b[0],1);
        }
        enabled=true;
    }
}
