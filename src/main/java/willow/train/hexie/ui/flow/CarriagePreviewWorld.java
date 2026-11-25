package willow.train.hexie.ui.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.core.api.carriage.CarriageFlowRegistry;

/** One carriage at its structure coordinates. Rendering can query neighbours and chunks without loading terrain. */
final class CarriagePreviewWorld extends Level implements CarriageFlowRegistry.PreviewLevel {
    private final ClientLevel client;
    private final int height;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final PreviewChunks chunks = new PreviewChunks();
    private final LevelEntityGetter<Entity> entities = new LevelEntityGetterAdapter<>(new EntityLookup<>(),
            new EntitySectionStorage<>(Entity.class, ignored -> Visibility.HIDDEN));

    CarriagePreviewWorld(ClientLevel client, int modelHeight) {
        super(client.getLevelData(), client.dimension(), client.registryAccess(), client.dimensionTypeRegistration(),
                client.getProfilerSupplier(), true, false, 0, 0);
        this.client = client;
        height = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(modelHeight - 1) + 1);
    }

    @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (isOutsideBuildHeight(pos) || getBlockState(pos) == state) return false;
        blocks.put(pos.immutable(), state);
        // Some models inspect a chunk section directly. Populate it without firing placement callbacks.
        getChunkAt(pos).getSection(getSectionIndex(pos.getY()))
                .setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
        return true;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }

    @Override public void setBlockEntity(BlockEntity entity) {
        var pos = entity.getBlockPos();
        blockEntities.put(pos, entity);
        getChunkAt(pos).getBlockEntities().put(pos, entity);
    }

    @Override public void removeBlockEntity(BlockPos pos) {
        var entity = blockEntities.remove(pos);
        if (entity != null) {
            getChunkAt(pos).getBlockEntities().remove(pos);
            entity.setRemoved();
        }
    }

    @Override public ModelData getModelData(BlockPos pos) {
        var entity = getBlockEntity(pos);
        return entity == null ? ModelData.EMPTY : entity.getModelData();
    }

    void clear() {
        blocks.clear();
        blockEntities.clear();
        chunks.loaded.clear();
    }

    @Override public int getMinBuildHeight() { return 0; }
    @Override public int getHeight() { return height; }
    @Override public boolean isLoaded(BlockPos pos) { return true; }
    @Override public boolean isAreaLoaded(BlockPos center, int range) { return true; }
    @Override public ChunkSource getChunkSource() { return chunks; }
    @Override public String gatherChunkSourceStats() { return chunks.gatherStats(); }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }
    @Override public int getRawBrightness(BlockPos pos, int skyDarken) { return 15; }
    @Override public float getShade(Direction face, boolean shade) { return 1; }

    private final class PreviewChunks extends ChunkSource {
        private final Map<ChunkPos, LevelChunk> loaded = new HashMap<>();
        private final LevelLightEngine light = new PreviewLight(this);

        @Override public Level getLevel() { return CarriagePreviewWorld.this; }
        @Override public LevelLightEngine getLightEngine() { return light; }
        @Override public int getLoadedChunksCount() { return loaded.size(); }
        @Override public String gatherStats() { return "Carriage preview chunks: " + loaded.size(); }
        @Override public void tick(BooleanSupplier hasTime, boolean tickChunks) {}

        @Override public LevelChunk getChunk(int x, int z, ChunkStatus status, boolean load) {
            return loaded.computeIfAbsent(new ChunkPos(x, z), p -> new LevelChunk(getLevel(), p));
        }
    }

    /** Also serves renderers that sample a layer of the light engine directly. */
    private static final class PreviewLight extends LevelLightEngine implements LayerLightEventListener {
        PreviewLight(ChunkSource chunks) { super(chunks, false, false); }
        @Override public LayerLightEventListener getLayerListener(LightLayer layer) { return this; }
        @Override public int getLightValue(BlockPos pos) { return 15; }
        @Override public int getRawBrightness(BlockPos pos, int skyDarken) { return 15; }
        @Override public DataLayer getDataLayerData(SectionPos section) { return new DataLayer(15); }
    }

    @Override public Holder<Biome> getBiome(BlockPos pos) { return client.getBiome(pos); }
    @Override public BiomeManager getBiomeManager() { return client.getBiomeManager(); }
    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z) { return client.getNoiseBiome(x, y, z); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return client.getUncachedNoiseBiome(x, y, z); }
    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return client.getBlockTint(pos, resolver); }
    @Override public FeatureFlagSet enabledFeatures() { return client.enabledFeatures(); }
    @Override public RecipeManager getRecipeManager() { return client.getRecipeManager(); }
    @Override public PotionBrewing potionBrewing() { return client.potionBrewing(); }
    @Override public Scoreboard getScoreboard() { return client.getScoreboard(); }
    @Override public TickRateManager tickRateManager() { return client.tickRateManager(); }
    @Override public float getDayTimeFraction() { return client.getDayTimeFraction(); }
    @Override public float getDayTimePerTick() { return client.getDayTimePerTick(); }
    @Override public void setDayTimeFraction(float fraction) {}
    @Override public void setDayTimePerTick(float speed) {}

    @Override protected LevelEntityGetter<Entity> getEntities() { return entities; }
    @Override public Entity getEntity(int id) { return null; }
    @Override public List<? extends Player> players() { return List.of(); }
    @Override public LevelTickAccess<Block> getBlockTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public MapItemSavedData getMapData(MapId id) { return null; }
    @Override public MapId getFreeMapId() { return new MapId(0); }
    @Override public void setMapData(MapId id, MapItemSavedData data) {}
    @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}
    @Override public void destroyBlockProgress(int breaker, BlockPos pos, int progress) {}
    @Override public void levelEvent(Player player, int type, BlockPos pos, int data) {}
    @Override public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {}
    @Override public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed) {}
    @Override public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed) {}
}
