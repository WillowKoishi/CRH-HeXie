package dev.hexie.renderer;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Render-thread allocator. Each page is a VBO/IBO pair, subdivided into fixed-size slots. */
public final class MeshPages {
    public record Handle(int slot,long generation) {}
    public record Location(int page,long vertexOffset,int vertexBytes,long indexOffset,int indexBytes,String format) {}
    private static final class Slot {
        long generation;
        Location location;
        BooleanSupplier lastRead;
    }
    private final int perPage,vertexStride,indexStride,maxPages;
    private final List<Slot> slots=new ArrayList<>();
    public MeshPages(int perPage,int vertexStride,int indexStride,int maxPages) {
        if (Math.min(Math.min(perPage,maxPages),Math.min(vertexStride,indexStride))<1)
            throw new IllegalArgumentException("page layout");
        this.perPage=perPage; this.vertexStride=vertexStride; this.indexStride=indexStride; this.maxPages=maxPages;
    }
    public Optional<Handle> allocate(int vertices,int indices,String format) {
        if (vertices<=0 || vertices>vertexStride || indices<=0 || indices>indexStride)
            throw new IllegalArgumentException("mesh does not fit slot");
        for (int i=0;i<(long)perPage*maxPages;i++) {
            if (i==slots.size()) slots.add(new Slot());
            Slot slot=slots.get(i);
            if (slot.location!=null || (slot.lastRead!=null && !slot.lastRead.getAsBoolean())) continue;
            slot.lastRead=null;
            long local=i%perPage;
            slot.location=new Location(i/perPage,local*vertexStride,vertices,local*indexStride,indices,format);
            return Optional.of(new Handle(i,++slot.generation));
        }
        return Optional.empty();
    }
    public Optional<Location> resolve(Handle h) {
        if (h.slot<0 || h.slot>=slots.size()) return Optional.empty();
        Slot s=slots.get(h.slot);
        return s.generation==h.generation ? Optional.ofNullable(s.location) : Optional.empty();
    }
    public void retire(Handle h,BooleanSupplier lastReadFence) {
        if (resolve(h).isEmpty()) throw new IllegalArgumentException("stale handle");
        Slot slot=slots.get(h.slot);
        slot.lastRead=Objects.requireNonNull(lastReadFence); slot.location=null;
    }
}
