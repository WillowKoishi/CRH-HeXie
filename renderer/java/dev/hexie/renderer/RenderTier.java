package dev.hexie.renderer;

import org.lwjgl.opengl.GL;

public enum RenderTier {
    BASIC, FULL;

    public static RenderTier select(boolean macOS, boolean supportedTerrain, boolean packActive, boolean gl45) {
        return !macOS && supportedTerrain && !packActive && gl45 ? FULL : BASIC;
    }
    public static RenderTier current(boolean supportedTerrain, boolean packActive) {
        return select(org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.MACOSX,
                supportedTerrain,packActive,GL.getCapabilities().OpenGL45);
    }
}
