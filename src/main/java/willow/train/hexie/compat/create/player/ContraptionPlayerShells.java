package willow.train.hexie.compat.create.player;

import com.simibubi.create.content.contraptions.Contraption;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge.PlayerCollisionShellView;
import willow.train.hexie.compat.create.interaction.ContraptionInteractionTargets;

// split from ContraptionInteractionTargets.java
public final class ContraptionPlayerShells {
    private ContraptionPlayerShells() {}

    public static boolean hasPlayerShells(Contraption contraption) {
        ContraptionInteractionTargets.ensure(contraption);
        synchronized (ContraptionInteractionTargets.TARGETS) {
            var holder = ContraptionInteractionTargets.TARGETS.get(contraption);
            return holder != null && !holder.shells.isEmpty();
        }
    }

    /** Only the installed shells are visited; their shifted boxes are cached until the block changes. */
    public static void collectPlayerShells(Contraption contraption, AABB query, Shapes.DoubleLineConsumer out) {
        ContraptionInteractionTargets.ensure(contraption);
        synchronized (ContraptionInteractionTargets.TARGETS) {
            var holder = ContraptionInteractionTargets.TARGETS.get(contraption);
            if (holder == null) return;
            for (var shell : holder.shells.values()) {
                shell.prepare();
                if (!shell.bounds.intersects(query)) continue;
                for (var box : shell.boxes)
                    if (box.intersects(query)) out.consume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            }
        }
    }

    public static final class Shell {
        private final BlockPos owner;
        public final Direction facing;
        public final boolean endFace;
        public final BlockState state;
        private List<AABB> boxes;
        private AABB bounds;

        public Shell(BlockPos owner, Direction facing, boolean endFace, BlockState state) {
            this.owner = owner;
            this.facing = facing;
            this.endFace = endFace;
            this.state = state;
        }

        /** Server-side metadata discovery does not need the client player's collision boxes. */
        private void prepare() {
            if (boxes != null) return;
            var local = state.getBlock() instanceof PlayerCollisionShellView custom
                    ? custom.playerCollisionBoxes(state) : endFace ? HexieBridges.carriageBlock().crh2EndFaceBlock().playerBoxes(facing) : HexieBridges.carriageBlock().headGeometry().boxes(facing);
            boxes = local.stream().map(box -> box.move(owner)).toList();
            bounds = boxes.getFirst();
            for (int i = 1; i < boxes.size(); i++) bounds = bounds.minmax(boxes.get(i));
        }
    }
}
