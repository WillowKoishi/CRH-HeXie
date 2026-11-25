package dev.hexie.renderer;

import java.nio.ByteBuffer;
import java.util.Set;

/** One nonblocking acquisition snapshots revision, descriptor and producer-owned lease together.
 * The producer pins pixels until release; a shared texture is flushed after its producer fence
 * and released by the consumer only after its last GPU read fence signals. */
@FunctionalInterface
public interface ImageSource {
    AcquireResult tryAcquire(long knownRevision, ImageCapabilities capabilities);

    enum Format { SRGB8_ALPHA8 }
    enum RowOrder { TOP_DOWN, BOTTOM_UP }
    record ImageCapabilities(Set<Format> formats, long shareGroup) {
        public ImageCapabilities { formats = Set.copyOf(formats); }
    }
    sealed interface AcquireResult {}
    enum Status implements AcquireResult { UNCHANGED, PENDING }
    record Removed(long revision) implements AcquireResult {}
    record PixelLease(long revision, int width, int height, int stride, RowOrder rows,
                      Format format, ByteBuffer pixels, Runnable release) implements AcquireResult {
        public PixelLease {
            if (width <= 0 || height <= 0 || stride < 4L*width ||
                pixels.remaining() < (height-1L)*stride+4L*width) throw new IllegalArgumentException("pixel layout");
            pixels = pixels.slice().asReadOnlyBuffer();
        }
        @Override public ByteBuffer pixels() { return pixels.asReadOnlyBuffer(); }
    }
    record GlTextureLease(long revision, long shareGroup, int texture, int target, int level, int width,
                          int height, Format format, long producerFence, Runnable release) implements AcquireResult {}
}
