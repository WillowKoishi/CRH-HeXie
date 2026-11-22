package willow.train.hexie.blocks.locos.ss7d;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** The cosmetic rain sensor uses the real world's precipitation at the cab. */
final class SS7DWipers {
    private static final int SAMPLE_INTERVAL = 20;
    private final Sweep left = new Sweep(9, SS7DHeadGeometry.WIPERS.get(0).sweepDegrees());
    private final Sweep right = new Sweep(11, SS7DHeadGeometry.WIPERS.get(1).sweepDegrees());
    private long nextSample = Long.MIN_VALUE;
    private long lastTick = Long.MIN_VALUE;
    private boolean raining;

    void tick(Level world, BlockPos windshield) {
        long now = world.getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        if (now >= nextSample) {
            raining = world.hasChunkAt(windshield) && world.isRainingAt(windshield);
            nextSample = now + SAMPLE_INTERVAL;
        }
        left.tick(raining);
        right.tick(raining);
    }

    float angle(int side, float partialTick) {
        return side == 0 ? left.angle(partialTick) : right.angle(partialTick);
    }

    private static final class Sweep {
        private static final float END_DWELL = 2;
        private final float stroke;
        private final float limit;
        private float phase;
        private float previous, current;

        Sweep(float stroke, float limit) { this.stroke = stroke; this.limit = limit; }
        void tick(boolean raining) {
            previous = current;
            // Finish the current cycle before parking at the authored rest position.
            float period = 2 * (stroke + END_DWELL);
            if (raining || phase != 0) {
                phase += 1;
                if (phase >= period) phase = 0;
            }
            if (phase <= stroke) current = ease(phase);
            else if (phase <= stroke + END_DWELL) current = limit;
            else current = ease(Math.max(0, 2 * stroke + END_DWELL - phase));
        }
        private float ease(float progress) { return limit * (1 - Mth.cos(Mth.PI * progress / stroke)) / 2; }
        float angle(float partialTick) { return Mth.lerp(partialTick, previous, current); }
    }
}
