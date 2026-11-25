package dev.hexie.renderer;

import java.util.Objects;

/** Requested limits. A zero disables the corresponding resource or scheduler. */
public record RendererConfig(boolean lighting, int tilePixels, int depthLayers, int priorityLights,
        int ownMiB, int ownMinSize, int ownMaxSize, int foreignCount, int foreignSize,
        Limits limits, double cellWidth, int cascades, int cascadeSize, double sunDistance,
        double gpuMillis, int memoryMiB, Policy policy) {
    public enum Policy { FIXED_FRAME_TIME, FULL_QUALITY }
    public record Limits(int faces, int rays, int ddaSteps, int activeLights, int indices) {
        public Limits {
            if (Math.min(Math.min(faces,rays), Math.min(ddaSteps,Math.min(activeLights,indices))) < 0)
                throw new IllegalArgumentException("negative frame limit");
        }
    }
    public RendererConfig {
        Objects.requireNonNull(limits);
        Objects.requireNonNull(policy);
        for (int n : new int[]{tilePixels,depthLayers,priorityLights,ownMiB,ownMinSize,ownMaxSize,
                foreignCount,foreignSize,cascades,cascadeSize,memoryMiB})
            if (n < 0) throw new IllegalArgumentException("negative capacity");
        for (double value : new double[]{cellWidth,sunDistance,gpuMillis})
            if (!(value >= 0 && Double.isFinite(value))) throw new IllegalArgumentException("invalid setting");
        if (cascades > 4 || (ownMaxSize != 0 && ownMinSize > ownMaxSize))
            throw new IllegalArgumentException("resource range");
    }
    public boolean pointLights() {
        return lighting && tilePixels > 0 && depthLayers > 0 && memoryMiB > 0
            && limits.activeLights > 0 && limits.indices > 0;
    }
    public boolean shadows() { return memoryMiB > 0 && limits.faces > 0 && cascades > 0 && cascadeSize > 0 && sunDistance > 0; }
    public RendererConfig withPolicy(Policy value) {
        return new RendererConfig(lighting,tilePixels,depthLayers,priorityLights,ownMiB,ownMinSize,
                ownMaxSize,foreignCount,foreignSize,limits,cellWidth,cascades,cascadeSize,
                sunDistance,gpuMillis,memoryMiB,value);
    }
}
