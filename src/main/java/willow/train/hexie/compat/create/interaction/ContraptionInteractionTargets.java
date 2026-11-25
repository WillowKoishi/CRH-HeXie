package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.OwnedBogeyMountView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2EndFaceBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.HeadFloorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.HeadGeometryOperations;
import willow.train.hexie.bridge.world.WorldBlockBridge.HexieBogeyBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.PlayerCollisionShellView;
import willow.train.hexie.compat.create.player.ContraptionPlayerShells;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.InteractionSpatialIndex;

/**
 * Static body-mounted targets and small block-type inventories in each contraption's local coordinates.
 * This is deliberately separate from WorldInteractionTargets, even when a display
 * block entity uses the real level. Independent moving arms are queried from their
 * existing pose list. Collision shells, bogeys and signs share the same existing
 * capture/load pass and single-block updates instead of rediscovering the structure.
 */
public final class ContraptionInteractionTargets {
    public static boolean hasPlayerShells(Contraption contraption) {
        return ContraptionPlayerShells.hasPlayerShells(contraption);
    }

    public static void collectPlayerShells(Contraption contraption, AABB query, Shapes.DoubleLineConsumer out) {
        ContraptionPlayerShells.collectPlayerShells(contraption, query, out);
    }

    public static final Map<Contraption, Holder> TARGETS = new WeakHashMap<>();

    private ContraptionInteractionTargets() {}

    /** Add this call to an existing capture/read loop, or to a single block change. */
    public static void update(Contraption contraption, BlockPos owner, BlockState state) {
        BlockControlHost host = BlockControlHost.find(state);
        var bounds = host == null ? null : host.controlBounds(state);
        var info = contraption.getBlocks().get(owner);
        if (host != null && info != null && HexieBridges.couplerBlock().couplerBlock().of(state) != null) {
            var adapter = HexieBridges.couplerBlock().couplerAdapterData().read(info.nbt());
            if (adapter != null) bounds = HexieBridges.worldBlock().couplerAdapterGeometry().bounds(state, adapter);
        }
        boolean shell = state.getBlock() instanceof CRH2HeadBlockView || state.getBlock() instanceof CRH2EndFaceBlockView
                || state.getBlock() instanceof PlayerCollisionShellView;
        boolean bogey = state.getBlock() instanceof HexieBogeyBlockView
                || state.getBlock() instanceof HeadFloorBlockView
                    && state.getValue(HexieBridges.carriageBlock().headFloorBlock().part()) == HeadGeometryOperations.BOGEY_PART
                || state.getBlock() instanceof OwnedBogeyMountView mount
                    && mount.isBogeyMount(state);
        boolean sign = HexieBridges.signBlock().passengerDisplayPanel().supports(state.getBlock()) && state.hasProperty(HexieBridges.signBlock().panelDisplayMount().property())
                && HexieBridges.signBlock().panelDisplayMount().hasSign(state.getValue(HexieBridges.signBlock().panelDisplayMount().property()));
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            if (holder == null && bounds == null && !shell && !bogey && !sign) return;
            if (holder == null) TARGETS.put(contraption, holder = new Holder(false));
            owner = owner.immutable();
            holder.blocks.put(owner, info);
            if (bounds == null) holder.index.remove(owner);
            else holder.index.put(owner, bounds.move(owner));
            if (shell) {
                boolean endFace = state.getBlock() instanceof CRH2EndFaceBlockView;
                Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                var previous = holder.shells.get(owner);
                if (previous == null || previous.endFace != endFace || previous.facing != facing
                        || previous.state.getBlock() != state.getBlock())
                    holder.shells.put(owner, new ContraptionPlayerShells.Shell(owner, facing, endFace, state));
            } else holder.shells.remove(owner);
            if (bogey) holder.bogeys.add(owner);
            else holder.bogeys.remove(owner);
            if (sign) holder.signs.add(owner);
            else holder.signs.remove(owner);
        }
    }

    public static void clear(Contraption contraption) {
        synchronized (TARGETS) {
            TARGETS.remove(contraption);
        }
    }

    /** Starts an existing complete block-copy loop without a second structure pass. */
    public static void begin(Contraption contraption) {
        synchronized (TARGETS) {
            TARGETS.put(contraption, new Holder(true));
        }
    }

    /** Create replaces block info on setBlock and handleBlockChange, even when map size stays constant. */
    public static void ensure(Contraption contraption) {
        if (contraption == null) return;
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            var blocks = contraption.getBlocks();
            if (holder == null || !holder.complete || holder.blocks.size() != blocks.size()) {
                rebuild(contraption);
                return;
            }
            // Share the change check across collision, pointer and inventory queries within a tick.
            long tick = contraption.entity == null ? Long.MIN_VALUE : contraption.entity.level().getGameTime();
            if (contraption.entity != null && tick == holder.checkedTick) return;
            holder.checkedTick = tick;
            for (var info : blocks.values()) {
                var previous = holder.blocks.get(info.pos());
                if (previous == info) continue;
                if (previous == null) {
                    // One owner was removed and another inserted between queries.
                    rebuild(contraption);
                    return;
                }
                update(contraption, info.pos(), info.state());
            }
        }
    }

    public static void rebuild(Contraption contraption) {
        synchronized (TARGETS) {
            begin(contraption);
            for (var info : contraption.getBlocks().values()) update(contraption, info.pos(), info.state());
            if (contraption.entity != null) TARGETS.get(contraption).checkedTick = contraption.entity.level().getGameTime();
        }
    }

    /** Use when the backend already traverses local ray cells. */
    public static List<BlockPos> lookup(Contraption contraption, BlockPos localCell) {
        ensure(contraption);
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            return holder == null ? List.of() : holder.index.lookup(localCell);
        }
    }

    /** Both endpoints must first be transformed into this contraption's coordinates. */
    public static List<BlockPos> query(Contraption contraption, Vec3 localFrom, Vec3 localTo) {
        ensure(contraption);
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            return holder == null ? List.of() : holder.index.query(localFrom, localTo);
        }
    }

    /** Positions only: callers read live NBT, including settings mutated in place. */
    public static List<BlockPos> bogeys(Contraption contraption) {
        ensure(contraption);
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            return holder == null ? List.of() : List.copyOf(holder.bogeys);
        }
    }

    public static boolean hasPassengerSigns(Contraption contraption) {
        ensure(contraption);
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            return holder != null && !holder.signs.isEmpty();
        }
    }

    public static final class Holder {
        private final Map<BlockPos, StructureBlockInfo> blocks = new HashMap<>();
        private long checkedTick = Long.MIN_VALUE;
        private final InteractionSpatialIndex index = new InteractionSpatialIndex();
        public final Map<BlockPos, ContraptionPlayerShells.Shell> shells = new HashMap<>();
        private final Set<BlockPos> bogeys = new LinkedHashSet<>();
        private final Set<BlockPos> signs = new LinkedHashSet<>();
        private final boolean complete;
        private Holder(boolean complete) { this.complete = complete; }
    }
}
