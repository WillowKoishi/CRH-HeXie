package dev.hexie.renderer;

public record ViewPassKey(long frameId, long viewId, long passId, long ownerId, long batchId) {}
