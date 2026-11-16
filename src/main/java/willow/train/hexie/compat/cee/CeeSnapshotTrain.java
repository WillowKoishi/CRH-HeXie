package willow.train.hexie.compat.cee;

import com.george_vi.electroenergetics.CEEElectricTrainSoundTypes;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.IPantographBlock;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.content.railway_electrification.sound_effects.TrainSoundModifier;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blocks.locos.PantographBlock;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

public final class CeeSnapshotTrain {
    private CeeSnapshotTrain() {}

    public static boolean hasLinePower(Carriage carriage) {
        if (!(carriage instanceof IPantographList equipment) || !equipment.electroEnergetics$hasElectricMotor()
                || !(carriage.train instanceof ICEETrainExtension train)) return false;
        var data = train.getElectricTrainData();
        return data.trainNode != null && data.lastVoltage >
                com.george_vi.electroenergetics.config.CEEConfigs.server().voltageValues.trainMinVoltage.get();
    }

    public static void bind(SnapshotCarriageEntity entity, Carriage carriage) {
        if (!(entity.getContraption() instanceof CarriageContraption view)
                || !(carriage instanceof IPantographList equipment)
                || !(carriage.train instanceof ICEETrainExtension electricTrain)) return;

        var direction = view.getAssemblyDirection();
        var rotation = switch (direction) {
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            case EAST -> Rotation.CLOCKWISE_180;
            case SOUTH -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        var previous = new HashMap<BlockPos, TrainPantographEntry>();
        for (var entry : equipment.electroEnergetics$getPantographList()) {
            previous.put(entry.originalPos, entry);
        }
        var pantographs = new ArrayList<TrainPantographEntry>();
        var sounds = equipment.electroEnergetics$getSoundModifyingBlocks();
        sounds.clear();
        boolean hasMotor = false;
        for (var info : view.getBlocks().values()) {
            var state = info.state();
            if (state.is(CEETags.TRAIN_ELECTRIC_MOTOR)) hasMotor = true;
            if (state.is(CEETags.TRAIN_SOUND_MODIFIER)) {
                for (var sound : CEERegistries.ELECTRIC_TRAIN_SOUND_TYPE) {
                    var blocks = sound.validBlocks.get();
                    if (blocks.containsKey(state.getBlock())) {
                        sounds.add(new TrainSoundModifier(state.getBlock(), blocks.getInt(state.getBlock()), sound));
                        break;
                    }
                }
            }
            if (!(state.getBlock() instanceof IPantographBlock pantograph)) continue;
            var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (pantograph.isSidewaysPantograph() == (facing.getAxis() == direction.getAxis())) continue;
            var pos = info.pos();
            var rotated = pos.rotate(rotation);
            var type = pantograph.getPantographType(state);
            boolean forward = facing == direction;
            var existing = previous.get(pos);
            if (existing != null && existing.rotatedPos.equals(rotated)
                    && existing.type == type && existing.facingForward == forward) {
                pantographs.add(existing);
            } else {
                boolean active = !(state.getBlock() instanceof PantographBlock) || state.getValue(PantographBlock.OPEN);
                pantographs.add(new TrainPantographEntry(pos, rotated, type, active, forward));
            }
        }
        equipment.electroEnergetics$setElectricMotor(hasMotor);
        equipment.electroEnergetics$setPantographList(pantographs);

        var trainPantographs = electricTrain.getElectricTrainData().pantographs;
        trainPantographs.clear();
        var trainSounds = electricTrain.getSoundModifyingBlocks();
        trainSounds.clear();
        for (var member : carriage.train.carriages) {
            if (member instanceof IPantographList blocks) {
                trainPantographs.addAll(blocks.electroEnergetics$getPantographList());
                trainSounds.addAll(blocks.electroEnergetics$getSoundModifyingBlocks());
            }
        }
        electricTrain.setSoundType(trainSounds.stream().max(Comparator.comparingInt(TrainSoundModifier::priority))
                .map(TrainSoundModifier::soundType).orElseGet(CEEElectricTrainSoundTypes.MODERN));
    }
}
