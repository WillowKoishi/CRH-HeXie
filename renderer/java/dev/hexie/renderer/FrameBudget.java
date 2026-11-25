package dev.hexie.renderer;

/** Render-thread owned. One ledger covers every view, pass and batch of a presentation frame. */
public final class FrameBudget {
    public record Usage(int faces, int rays, int ddaSteps, int activeLights, int indices) {}
    private final RendererConfig config;
    private final long frameId;
    private int faces,rays,steps,active,indices;
    private double gpuMillis;
    public FrameBudget(long frameId, RendererConfig config) {
        this.frameId=frameId;
        this.config=config;
    }
    public long frameId() { return frameId; }
    public RendererConfig config() { return config; }
    public void recordGpuMillis(double measured) {
        if (!(measured >= 0 && Double.isFinite(measured))) throw new IllegalArgumentException("GPU duration");
        gpuMillis+=measured;
    }
    public double gpuMillis() { return gpuMillis; }
    public boolean hasTime() {
        return config.policy()==RendererConfig.Policy.FULL_QUALITY || config.gpuMillis()==0 || gpuMillis<config.gpuMillis();
    }
    public boolean faces(int count) {
        if (count<0 || count>config.limits().faces()-faces || !hasTime()) return false;
        faces+=count;
        return true;
    }
    public boolean ray() {
        if (!hasTime() || rays==config.limits().rays()) return false;
        rays++;
        return true;
    }
    public boolean step() {
        if (!hasTime() || steps==config.limits().ddaSteps()) return false;
        steps++;
        return true;
    }
    void returnUnusedRays(int count) {
        if (count<0 || count>rays) throw new IllegalStateException("invalid exact-ray credit");
        rays-=count;
    }
    void chargeSteps(int measured) {
        if (measured<0 || measured>config.limits().ddaSteps()-steps) throw new IllegalStateException("GPU DDA overrun");
        steps+=measured;
    }
    public boolean light() {
        if (!hasTime() || active==config.limits().activeLights()) return false;
        active++;
        return true;
    }
    public int indices(int requested) {
        int granted=hasTime()?Math.min(Math.max(0,requested),config.limits().indices()-indices):0;
        indices+=granted;
        return granted;
    }
    public Usage usage() { return new Usage(faces,rays,steps,active,indices); }
}
