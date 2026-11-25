package dev.hexie.renderer.mc;

import java.util.function.BooleanSupplier;
import static dev.hexie.renderer.mc.RenderSettings.Tier;

public final class FrameRoute {
    private long frame = Long.MIN_VALUE;
    private Tier tier = Tier.BASIC;
    public Tier begin(long id,BooleanSupplier shaderPack,boolean full) {
        if (frame != id) {
            tier = shaderPack.getAsBoolean() ? Tier.IRIS : full ? Tier.FULL : Tier.BASIC;
            frame = id;
        }
        return tier;
    }
    public Tier tier() { return tier; }
    public long frameId() { return frame; }
}
