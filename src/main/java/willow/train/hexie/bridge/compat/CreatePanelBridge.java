package willow.train.hexie.bridge.compat;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.ui.MenuBridge;

public interface CreatePanelBridge {
    static @Nullable CreatePanelBridge forCarrier(@Nullable Entity carrier) {
        if (carrier == null || !ModList.get().isLoaded("create")) return null;
        var panel = HexieBridges.createPanel();
        return panel.supportsCarrier(carrier) ? panel : null;
    }

    boolean supportsCarrier(Entity carrier);
    boolean hasCapturedBlocks(Entity carrier);
    @Nullable CompoundTag blockEntityNbt(Entity carrier, BlockPos pos);

    void registerPanelPayloads(PayloadRegistrar registrar);

    CreatePanelClipboardOperations createPanelClipboard();

    interface CreatePanelClipboardOperations {
        boolean canPaste(BlockEntity entity, Player player, Direction side);
    }

    CreatePanelEditTargetOperations createPanelEditTarget();

    interface CreatePanelEditTargetOperations {
        @Nullable CreatePanelEditTargetView createHeadNumber(ServerPlayer player, Entity carrier,
                BlockPos rootPos, BlockPos clickedPos);
        boolean updateCapturedContents(Contraption contraption, BlockPos pos,
                CreatePanelEditTargetContentType type, int slot, CompoundTag payload,
                HolderLookup.Provider registries);
    }

    interface CreatePanelEditTargetView extends MenuBridge.BlockEntityEditMenuTargetView {}

    enum CreatePanelEditTargetContentType {
        CARRIAGE_NUMBER, CARRIAGE_TYPE, LACQUERED, SPEED, MARKINGS, PANEL_TEXT, HEAD_NUMBER, SS7D_NUMBER
    }
}
