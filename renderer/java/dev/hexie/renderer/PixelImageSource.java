package dev.hexie.renderer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/** CPU pixel source. The current immutable lease also pins its backing storage for past
 * readers. Producers can stage a new version while tryAcquire performs one atomic load. */
public final class PixelImageSource implements ImageSource {
    private final AtomicReference<AcquireResult> current=new AtomicReference<>(Status.PENDING);
    public void publish(long revision,int width,int height,int stride,RowOrder rows,ByteBuffer rgba) {
        ByteBuffer copy=ByteBuffer.allocate(rgba.remaining());
        copy.put(rgba.duplicate()).flip();
        replace(new PixelLease(revision,width,height,stride,rows,Format.SRGB8_ALPHA8,copy,()->{}),revision);
    }
    public void remove(long revision) { replace(new Removed(revision),revision); }
    private static long revision(AcquireResult state) {
        if (state instanceof PixelLease pixels) return pixels.revision();
        if (state instanceof Removed removed) return removed.revision();
        return -1;
    }
    private void replace(AcquireResult next,long version) {
        if (version<0) throw new IllegalArgumentException("negative image revision");
        current.getAndUpdate(old->version>revision(old)?next:old);
    }
    @Override public AcquireResult tryAcquire(long knownRevision,ImageCapabilities capabilities) {
        AcquireResult snapshot=current.get();
        if (snapshot==Status.PENDING) return snapshot;
        if (revision(snapshot)==knownRevision) return Status.UNCHANGED;
        if (snapshot instanceof PixelLease lease && !capabilities.formats().contains(lease.format())) return Status.PENDING;
        return snapshot;
    }
}
