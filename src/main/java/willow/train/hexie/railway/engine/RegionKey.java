package willow.train.hexie.railway.engine;

public record RegionKey(Id dimensionId, long networkId, int regionX, int regionZ) {
    public static RegionKey fromChunk(Id dimension, long network, int chunkX, int chunkZ) {
        return new RegionKey(dimension, network, Math.floorDiv(chunkX, 16), Math.floorDiv(chunkZ, 16));
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return Integer.toUnsignedLong(chunkX) | ((long) chunkZ << 32);
    }
}
