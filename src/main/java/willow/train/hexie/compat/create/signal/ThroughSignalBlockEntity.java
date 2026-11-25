package willow.train.hexie.compat.create.signal;

import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort;

/*
MIT License

Copyright (c) 2019 simibubi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

public class ThroughSignalBlockEntity extends SignalBlockEntity implements TransformableBlockEntity, CreateTrainBridgePort.ThroughSignalBlockEntityView {

    protected static final int LIGHT_MODE_DRAG_STEP = 16;

    private SignalBlockEntity.SignalState state;
    private SignalBlockEntity.OverlayState overlay;
    private int switchToRedAfterTrainEntered;
    private boolean lastReportedPower;
    private ScrollValueBehaviour lightMode;

    public ThroughSignalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.state = SignalBlockEntity.SignalState.INVALID;
        this.overlay = SignalBlockEntity.OverlayState.SKIP;
        this.lastReportedPower = false;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("State", state.name());
        tag.putString("Overlay", overlay.name());
        tag.putBoolean("Power", lastReportedPower);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state = readEnum(tag.getString("State"), SignalBlockEntity.SignalState.values());
        overlay = readEnum(tag.getString("Overlay"), SignalBlockEntity.OverlayState.values());
        lastReportedPower = tag.getBoolean("Power");
        invalidateRenderBoundingBox();
    }

    private static <E extends Enum<E>> E readEnum(String name, E[] values) {
        for (E value : values) {
            if (value.name().equals(name)) return value;
        }
        return values[0];
    }

    @Nullable
    public SignalBoundary getSignal() {
        return edgePoint.getEdgePoint();
    }

    public boolean isPowered() {
        return state == SignalBlockEntity.SignalState.RED;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        lightMode = new ScrollValueBehaviour(Component.translatable("gui.hexie.semaphore.light_mode"), this,
                new ThroughSignalBlockEntity.LightModeValueBox()) {
            {
                between(0, 2);
                value = 0;
            }

            @Override
            public boolean mayInteract(Player player) {
                return player.mayBuild() && !player.isSpectator();
            }

            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
                return createLightModeBoard();
            }

            @Override
            public ValueSettingsBehaviour.ValueSettings getValueSettings() {
                return new ValueSettingsBehaviour.ValueSettings(0, swapFirstTwoModes(getValue()) * LIGHT_MODE_DRAG_STEP);
            }

            @Override
            public void setValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown) {
                if (applyAdditionalLightSetting(player, settings))
                    return;
                int mode = swapFirstTwoModes(modeFromDragValue(settings.value()));
                if (mode == getValue())
                    return;
                setValue(mode);
                playFeedbackSound(this);
            }
        };
        behaviours.add(lightMode);
    }

    protected ValueSettingsBoard createLightModeBoard() {
        return new ValueSettingsBoard(Component.translatable("gui.hexie.semaphore.light_mode"),
                LIGHT_MODE_DRAG_STEP * 2, LIGHT_MODE_DRAG_STEP,
                List.of(Component.translatable("gui.hexie.semaphore.light_mode.options")),
                new ValueSettingsFormatter(settings -> Component.translatable(
                        "gui.hexie.semaphore.light_mode.option." + (modeFromDragValue(settings.value()) + 1))));
    }

    protected boolean applyAdditionalLightSetting(Player player, ValueSettingsBehaviour.ValueSettings settings) {
        return false;
    }

    public int getLightMode() {
        return lightMode == null ? 0 : lightMode.getValue();
    }

    protected static int modeFromDragValue(int dragValue) {
        return Mth.clamp(Math.round(dragValue / (float) LIGHT_MODE_DRAG_STEP), 0, 2);
    }

    private static int swapFirstTwoModes(int mode) {
        return mode == 0 ? 1 : mode == 1 ? 0 : 2;
    }

    private static class LightModeValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(.5, .5, 15 / 16.0);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal();
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            if (!isSideActive(state, getSide()))
                return false;

            // Create's default value-box hit radius is only four pixels wide. Let the
            // whole side of this signal block start the same hold-and-drag UI.
            return localHit.x >= 0 && localHit.x <= 1
                    && localHit.y >= 0 && localHit.y <= 1
                    && localHit.z >= 0 && localHit.z <= 1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide)
            return;

        SignalBoundary boundary = getSignal();
        if (boundary == null) {
            enterState(SignalBlockEntity.SignalState.INVALID);
            setOverlay(SignalBlockEntity.OverlayState.RENDER);
            return;
        }

        BlockState blockState = getBlockState();

        blockState.getOptionalValue(SignalBlock.POWERED).ifPresent(powered -> {
            if (lastReportedPower == powered)
                return;
            lastReportedPower = powered;
            boundary.updateBlockEntityPower(this);
            notifyUpdate();
        });

        blockState.getOptionalValue(SignalBlock.TYPE)
                .ifPresent(stateType -> {
                    SignalBlock.SignalType targetType = boundary.getTypeFor(worldPosition);
                    if (stateType != targetType) {
                        level.setBlock(worldPosition, blockState.setValue(SignalBlock.TYPE, targetType), 3);
                        refreshBlockState();
                    }
                });

        enterState(boundary.getStateFor(worldPosition));
        setOverlay(boundary.getOverlayFor(worldPosition));
    }

    public boolean getReportedPower() {
        return lastReportedPower;
    }

    public SignalBlockEntity.SignalState getState() {
        return HexieBridges.createTrain().createSignalAspects().visibleState(this, state);
    }

    public SignalBlockEntity.OverlayState getOverlay() {
        return overlay;
    }

    public void setOverlay(SignalBlockEntity.OverlayState state) {
        if (this.overlay == state)
            return;
        this.overlay = state;
        notifyUpdate();
    }

    public void enterState(SignalBlockEntity.SignalState state) {
        if (switchToRedAfterTrainEntered > 0)
            switchToRedAfterTrainEntered--;
        if (this.state == state)
            return;
        if (state == SignalBlockEntity.SignalState.RED && switchToRedAfterTrainEntered > 0)
            return;
        this.state = state;
        switchToRedAfterTrainEntered = state == SignalBlockEntity.SignalState.GREEN || state == SignalBlockEntity.SignalState.YELLOW ? 15 : 0;
        notifyUpdate();
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return AABB.encapsulatingFullBlocks(worldPosition, edgePoint.getGlobalPosition()).inflate(2);
    }

    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {
        edgePoint.transform(blockEntity, transform);
    }
}
