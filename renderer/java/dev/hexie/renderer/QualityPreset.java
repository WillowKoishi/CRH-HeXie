package dev.hexie.renderer;

import static dev.hexie.renderer.RendererConfig.Policy.FIXED_FRAME_TIME;

public enum QualityPreset {
    LOW(new RendererConfig(true,32,16,2,24,64,128,4,128,
        new RendererConfig.Limits(12,32768,2097152,2048,262144),0.5,2,1024,48,3.5,256,FIXED_FRAME_TIME)),
    MEDIUM(new RendererConfig(true,16,24,4,96,128,256,16,256,
        new RendererConfig.Limits(30,81920,4194304,4096,1048576),0.5,3,1536,80,5,512,FIXED_FRAME_TIME)),
    HIGH(new RendererConfig(true,16,24,8,192,128,512,24,256,
        new RendererConfig.Limits(60,163840,8388608,8192,4194304),0.25,4,2048,128,6,1024,FIXED_FRAME_TIME));

    private final RendererConfig config;
    QualityPreset(RendererConfig config) { this.config=config; }
    public RendererConfig config() { return config; }
}
