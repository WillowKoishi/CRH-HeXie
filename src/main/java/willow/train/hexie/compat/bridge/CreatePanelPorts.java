package willow.train.hexie.compat.bridge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.compat.create.panel.CreatePanelClipboard;
import willow.train.hexie.compat.create.panel.CreatePanelEditTarget;
import willow.train.hexie.compat.create.panel.CreatePanelEditorOpenPacket;

final class CreatePanelPorts implements CreatePanelBridge {
    private final CreatePanelEditTargetOperations createPanelEditTarget = new CreatePanelEditTargetOperations() {
        @Override public CreatePanelEditTargetView createHeadNumber(ServerPlayer player,
                        Entity carrier,
                        BlockPos rootPos,
                        BlockPos clickedPos) {
            return CreatePanelEditTarget.createHeadNumber(player, carrier, rootPos, clickedPos);
        }

        @Override public boolean updateCapturedContents(Contraption contraption,
                        BlockPos pos,
                        CreatePanelEditTargetContentType type,
                        int slot,
                        CompoundTag payload,
                        HolderLookup.Provider registries) {
            return CreatePanelEditTarget.updateCapturedContents(contraption, pos, CreatePanelEditTarget.ContentType.valueOf(type.name()), slot, payload, registries);
        }
    };

    @Override public boolean supportsCarrier(Entity carrier) {
        return carrier instanceof AbstractContraptionEntity;
    }

    @Override public boolean hasCapturedBlocks(Entity carrier) {
        return carrier instanceof AbstractContraptionEntity entity && entity.getContraption() != null;
    }

    @Override public @Nullable CompoundTag blockEntityNbt(Entity carrier, BlockPos pos) {
        if (!(carrier instanceof AbstractContraptionEntity entity) || entity.getContraption() == null) return null;
        var info = entity.getContraption().getBlocks().get(pos);
        return info == null ? null : info.nbt();
    }

    @Override public void registerPanelPayloads(PayloadRegistrar registrar) {
        registrar.playToServer(CreatePanelEditorOpenPacket.TYPE, CreatePanelEditorOpenPacket.CODEC, CreatePanelEditorOpenPacket::handle);
    }

    @Override public CreatePanelClipboardOperations createPanelClipboard() {
        return CreatePanelClipboard::canPaste;
    }

    @Override public CreatePanelEditTargetOperations createPanelEditTarget() {
        return createPanelEditTarget;
    }
}
