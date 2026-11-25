package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.ui.MenuBridge.CarriageNumberMenuTargetView;
import willow.train.hexie.compat.create.assembly.IndexedContraptionActors;
import willow.train.hexie.compat.mixins.create.ContraptionUpdateTagsAccessor;
import willow.train.hexie.core.api.display.PassengerCarriageNumber;
import willow.train.hexie.core.api.display.PassengerDisplayHost;

/** A menu edits one captured panel's number, never its mount, column or orientation. */
public final class CreateCarriageNumberTarget implements CarriageNumberMenuTargetView {
    private final ServerPlayer owner;
    private final AbstractContraptionEntity entity;
    private final Contraption contraption;
    private final BlockPos rootPos;
    private final BlockPos clickedPos;
    private final Block block;

    private CreateCarriageNumberTarget(ServerPlayer owner, AbstractContraptionEntity entity,
            BlockPos rootPos, BlockPos clickedPos, Block block) {
        this.owner = owner;
        this.entity = entity;
        this.contraption = entity.getContraption();
        this.rootPos = rootPos.immutable();
        this.clickedPos = clickedPos.immutable();
        this.block = block;
    }

    public static CreateCarriageNumberTarget create(ServerPlayer player, AbstractContraptionEntity entity,
            BlockPos rootPos, BlockPos clickedPos) {
        var contraption = entity.getContraption();
        var info = contraption == null ? null : contraption.getBlocks().get(rootPos);
        if (info == null) return null;
        var target = new CreateCarriageNumberTarget(player, entity, rootPos, clickedPos, info.state().getBlock());
        return target.stillValid(player) ? target : null;
    }

    public static boolean open(ServerPlayer player, AbstractContraptionEntity entity,
            BlockPos rootPos, BlockPos clickedPos) {
        var target = create(player, entity, rootPos, clickedPos);
        return target != null && HexieBridges.menu().carriageNumberMenu().open(player, target);
    }

    @Override public String number() {
        var info = contraption.getBlocks().get(rootPos);
        return info == null || info.nbt() == null ? "" : PassengerCarriageNumber.read(info.nbt());
    }

    @Override public boolean stillValid(Player player) {
        if (player != owner || entity.isRemoved() || entity.level().isClientSide
                || entity.level() != player.level() || entity.getContraption() != contraption
                || player.isSpectator() || !player.mayBuild()
                || !entity.canInteractWithBlock(player, clickedPos, 4)) return false;
        var root = HexieBridges.partBlock().partStructures().resolve(contraption.getContraptionWorld(), clickedPos);
        if (root == null || !root.pos().equals(rootPos) || !root.state().is(block)
                || !HexieBridges.signBlock().passengerDisplayPanel().supports(block)
                || !HexieBridges.signBlock().panelDisplayMount().hasNumber(root.state().getValue(HexieBridges.signBlock().panelDisplayMount().property()))) return false;
        for (var entry : root.structure().partCells(contraption.getContraptionWorld(), rootPos, root.state()).entrySet()) {
            var local = entry.getKey();
            var captured = contraption.getBlocks().get(local);
            if (captured == null || !captured.state().equals(entry.getValue())) return false;
            var world = BlockPos.containing(entity.toGlobalVector(Vec3.atCenterOf(local), 1));
            if (!entity.level().mayInteract(player, world)) return false;
        }
        return true;
    }

    @Override public void setNumber(String number) {
        if (!PassengerCarriageNumber.valid(number) || !stillValid(owner) || number.equals(number())) return;
        var previous = contraption.getBlocks().get(rootPos);
        CompoundTag data;
        if (previous.nbt() == null) {
            // Old native contraptions may predate the panel's block entity. Establish a
            // complete registered BE tag before this first edit creates persisted data.
            var factory = (net.minecraft.world.level.block.EntityBlock) previous.state().getBlock();
            var blockEntity = factory.newBlockEntity(rootPos, previous.state());
            if (!(blockEntity instanceof PassengerDisplayHost)) return;
            blockEntity.setLevel(entity.level());
            data = blockEntity.saveWithFullMetadata(entity.registryAccess());
        } else data = previous.nbt().copy();
        PassengerCarriageNumber.write(data, number);
        var next = new StructureBlockInfo(rootPos, previous.state(), data);
        contraption.getBlocks().put(rootPos, next);
        // Content changes do not alter geometry or invalidate the contraption structure.
        updateCapturedNumber(contraption, rootPos, number);
        HexieBridges.sync().sendCarriageNumberToTracking(entity, entity.getId(), entity.getUUID(), rootPos, number);
    }

    /** Shared by the authoritative commit and its number-only client notification. */
    public static void updateCapturedNumber(Contraption contraption, BlockPos pos, String number) {
        var previous = contraption.getBlocks().get(pos);
        if (previous == null || !PassengerCarriageNumber.valid(number)) return;
        var data = previous.nbt() == null ? new CompoundTag() : previous.nbt().copy();
        PassengerCarriageNumber.write(data, number);
        var next = new StructureBlockInfo(pos, previous.state(), data);
        contraption.getBlocks().put(pos, next);
        var actor = IndexedContraptionActors.get(contraption, pos);
        if (actor != null) {
            actor.left = next;
            if (actor.right != null) {
                var actorData = data.copy();
                if (actor.right.blockEntityData != null) actorData.merge(actor.right.blockEntityData);
                PassengerCarriageNumber.write(actorData, number);
                actor.right.blockEntityData = actorData;
            }
        }
        var tags = ((ContraptionUpdateTagsAccessor) contraption).hexie$getUpdateTags();
        var update = tags.containsKey(pos) ? tags.get(pos).copy() : data.copy();
        PassengerCarriageNumber.write(update, number);
        tags.put(pos, update);
    }
}
