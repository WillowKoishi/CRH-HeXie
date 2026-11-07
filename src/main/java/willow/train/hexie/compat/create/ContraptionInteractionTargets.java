package willow.train.hexie.compat.create;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import willow.train.hexie.api.interaction.BlockControlHost;
import willow.train.hexie.api.interaction.InteractionSpatialIndex;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
import willow.train.hexie.blocks.signs.PanelDisplayMount;
import willow.train.hexie.blocks.signs.PassengerDisplayPanel;

/**
 * Static body-mounted targets and small block-type inventories in each contraption's local coordinates.
 * This is deliberately separate from WorldInteractionTargets, even when a display
 * block entity uses the real level. Independent moving arms are queried from their
 * existing pose list. Collision shells, bogeys and signs share the same existing
 * capture/load pass and single-block updates instead of rediscovering the structure.
 */
public final class ContraptionInteractionTargets {
    private static final Map<Contraption, Holder> TARGETS = new WeakHashMap<>();

    private ContraptionInteractionTargets() {}

    /** Add this call to an existing capture/read loop, or to a single block change. */
    public static void update(Contraption contraption, BlockPos owner, BlockState state) {
        BlockControlHost host = BlockControlHost.find(state);
        var bounds = host == null ? null : host.controlBounds(state);
        var info = contraption.getBlocks().get(owner);
        if (host != null && info != null && willow.train.hexie.blocks.coupler.CouplerBlock.of(state) != null) {
            var adapter = willow.train.hexie.blocks.coupler.CouplerAdapterData.read(info.nbt());
            if (adapter != null) bounds = willow.train.hexie.blocks.coupler.CouplerAdapterGeometry.bounds(state, adapter);
        }
        boolean shell = state.getBlock() instanceof CRH2HeadBlock || state.getBlock() instanceof CRH2EndFaceBlock;
        boolean bogey = state.getBlock() instanceof HexieBogeyBlock<?>;
        boolean sign = PassengerDisplayPanel.supports(state.getBlock()) && state.hasProperty(PanelDisplayMount.PROPERTY)
                && state.getValue(PanelDisplayMount.PROPERTY).hasSign();
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            if (holder == null && bounds == null && !shell && !bogey && !sign) return;
            if (holder == null) TARGETS.put(contraption, holder = new Holder(false));
            owner = owner.immutable();
            if (bounds == null) holder.index.remove(owner);
            else holder.index.put(owner, bounds.move(owner));
            if (shell) {
                boolean endFace = state.getBlock() instanceof CRH2EndFaceBlock;
                Direction facing = state.getValue(CRH2HeadBlock.FACING);
                var previous = holder.shells.get(owner);
                if (previous == null || previous.endFace != endFace || previous.facing != facing)
                    holder.shells.put(owner, new Shell(owner, facing, endFace));
            } else holder.shells.remove(owner);
            if (bogey) holder.bogeys.add(owner);
            else holder.bogeys.remove(owner);
            if (sign) holder.signs.add(owner);
            else holder.signs.remove(owner);
        }
    }

    public static void remove(Contraption contraption, BlockPos owner) {
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            if (holder != null) {
                holder.index.remove(owner);
                holder.shells.remove(owner);
                holder.bogeys.remove(owner);
                holder.signs.remove(owner);
            }
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

    /** Fallback for third-party carriers which bypass the ordinary attachment hooks. */
    public static void ensure(Contraption contraption) {
        if (contraption == null) return;
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            if (holder == null || !holder.complete) rebuild(contraption);
        }
    }

    /** Attachment/load rebuild; ensure uses it once for carriers that bypass those hooks. */
    public static void rebuild(Contraption contraption) {
        synchronized (TARGETS) {
            begin(contraption);
            for (var info : contraption.getBlocks().values()) update(contraption, info.pos(), info.state());
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

    public static boolean hasPlayerShells(Contraption contraption) {
        ensure(contraption);
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            return holder != null && !holder.shells.isEmpty();
        }
    }

    /** Only the installed shells are visited; their shifted boxes are cached until the block changes. */
    public static void collectPlayerShells(Contraption contraption, AABB query, Shapes.DoubleLineConsumer out) {
        ensure(contraption);
        synchronized (TARGETS) {
            var holder = TARGETS.get(contraption);
            if (holder == null) return;
            for (var shell : holder.shells.values()) {
                shell.prepare();
                if (!shell.bounds.intersects(query)) continue;
                for (var box : shell.boxes)
                    if (box.intersects(query)) out.consume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            }
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

    private static final class Holder {
        private final InteractionSpatialIndex index = new InteractionSpatialIndex();
        private final Map<BlockPos, Shell> shells = new HashMap<>();
        private final Set<BlockPos> bogeys = new LinkedHashSet<>();
        private final Set<BlockPos> signs = new LinkedHashSet<>();
        private final boolean complete;
        private Holder(boolean complete) { this.complete = complete; }
    }

    private static final class Shell {
        private final BlockPos owner;
        private final Direction facing;
        private final boolean endFace;
        private List<AABB> boxes;
        private AABB bounds;

        private Shell(BlockPos owner, Direction facing, boolean endFace) {
            this.owner = owner;
            this.facing = facing;
            this.endFace = endFace;
        }

        /** Server-side metadata discovery does not need the client player's collision boxes. */
        private void prepare() {
            if (boxes != null) return;
            var local = endFace ? CRH2EndFaceBlock.playerBoxes(facing) : HeadGeometry.boxes(facing);
            boxes = local.stream().map(box -> box.move(owner)).toList();
            bounds = boxes.getFirst();
            for (int i = 1; i < boxes.size(); i++) bounds = bounds.minmax(boxes.get(i));
        }
    }
}
