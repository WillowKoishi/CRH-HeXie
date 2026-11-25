package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.TrainHUDUpdatePacket;
import com.simibubi.create.content.trains.entity.TrainPromptPacket;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.platform.CatnipServices;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainControlSource;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.compat.create.motion.AssembledVehicleCabControl;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.signal.CreateCabTimetable;
import willow.train.hexie.compat.create.signal.CreateNavigationState;
import willow.train.hexie.compat.create.signal.CreateSharedCabTimetables;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.traincontrol.CabTrainControl.Demand;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

// split from AssembledVehicleMotion.java
public final class AssembledVehicleControlInput {
    private final AssembledVehicleMotion vehicleMotion;

    public AssembledVehicleControlInput(AssembledVehicleMotion vehicleMotion) { this.vehicleMotion = vehicleMotion; }

    // Last accepted operations remain ordered when independent trains couple, even within one tick.
    public static final AtomicLong NATIVE_INPUT_SEQUENCE = new AtomicLong();

    /** Convert and validate parameters before installing them into the existing controller binding. */
    public boolean updateCabKeyboard(Player player, SnapshotCarriageEntity car, BlockPos position,
                                     boolean toggleSimple, int mask) {
        if (!HexieBridges.cabControl().simpleCabControls().validMask(mask)) return false;
        var cab = DrivingConsoleContext.find(player);
        if (cab == null || !cab.console().equals(position)) return false;
        var state = cab.seatAccess().blockState(position);
        if (state == null || !(state.getBlock() instanceof CabControlHost host)) return false;
        return updateCabControls(car, cab, player,
                (previous, speed) -> HexieBridges.cabControl().simpleCabControls().applyBindings(host, state, previous, toggleSimple, mask, speed,
                        blocked -> HexieBridges.cabControl().simpleCabControls().notifyShuntingBlocked(player, blocked)), mask);
    }

    /** Resolve a physical gesture against the mounted desk and convert its declared parameters at train speed. */
    public boolean operateCabControl(SnapshotCarriageEntity car, DrivingConsoleContext cab,
                                     Player player, String controlId, int value) {
        var block = cab.seatAccess().blockState(cab.console());
        if (block == null || !(block.getBlock() instanceof CabControlHost host)) return false;
        if (player.getVehicle() != car || cab.seatAccess().vehicle() != car) return false;
        return updateCabControls(car, cab.console(), player, (previous, speed) ->
                HexieBridges.cabControl().cabModule().operateControls(host, block, controlId, value, previous, speed), -1, true,
                HexieBridges.cabControl().cabModule().operatedDemands(host, block, controlId), host.operatesWhenDerailed(block, controlId));
    }

    /** The input transport validated this physical target; operating it grants no seated keyboard bindings. */
    public boolean operateCabControl(SnapshotCarriageEntity car, BlockPos position,
                                     Player player, String controlId, int value) {
        var info = car.getContraption() == null ? null : car.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof CabControlHost host)) return false;
        return updateCabControls(car, position, player, (previous, speed) ->
                HexieBridges.cabControl().cabModule().operateControls(host, info.state(), controlId, value, previous, speed), -1, false,
                HexieBridges.cabControl().cabModule().operatedDemands(host, info.state(), controlId), host.operatesWhenDerailed(info.state(), controlId));
    }

    public boolean updateCabControls(SnapshotCarriageEntity car, DrivingConsoleContext cab,
            Player player, java.util.function.BiFunction<CabState, Double,
                    CabState> input) {
        return updateCabControls(car, cab, player, input, -1);
    }

    public boolean updateCabControls(SnapshotCarriageEntity car, DrivingConsoleContext cab,
            Player player, java.util.function.BiFunction<CabState, Double, CabState> input, int keyboardMask) {
        if (player.getVehicle() != car || cab.seatAccess().vehicle() != car) return false;
        return updateCabControls(car, cab.console(), player, input, keyboardMask, true);
    }

    public boolean updateCabControls(SnapshotCarriageEntity car, BlockPos position,
            Player player, java.util.function.BiFunction<CabState, Double, CabState> input,
            int keyboardMask, boolean seated) {
        return updateCabControls(car, position, player, input, keyboardMask, seated, Set.of(), false);
    }

    public boolean updateCabControls(SnapshotCarriageEntity car, BlockPos position,
            Player player, java.util.function.BiFunction<CabState, Double, CabState> input,
            int keyboardMask, boolean seated, Set<Demand> operated, boolean allowDerailed) {
        var info = car.getContraption() == null ? null : car.getContraption().getBlocks().get(position);
        var state = info == null ? null : info.state();
        if (!vehicleMotion.owns(car.getUUID()) || !player.isAlive() || player.isSpectator() || player.level() != vehicleMotion.level
                || car.level() != vehicleMotion.level || car.isRemoved()
                || state == null || !(state.getBlock() instanceof CabControlHost host)
                || vehicleMotion.isDerailed(car.getUUID()) && !allowDerailed) return false;
        var facing = host.cabFacing(state);
        if (facing == null || !facing.getAxis().isHorizontal()) return false;
        var key = new AssembledVehicleCabControl.ControlKey(car.getUUID(), position);
        vehicleMotion.applyCouplingControlResets();
        var driver = vehicleMotion.cabControl.controllers.get(key);
        if (driver != null && (driver.cabControls == null
                || !driver.player.equals(player.getUUID()) && occupiesCab(vehicleMotion.level.getPlayerByUUID(driver.player),
                        car.getUUID(), position))) return false;
        if (driver == null) {
            driver = new AssembledVehicleCabControl.Controller(player.getUUID(), position, vehicleMotion.level.getGameTime());
            var data = info == null ? null : info.nbt();
            driver.cabControls = data != null && (data.contains("CabControls") || data.contains("TestControls"))
                    ? HexieBridges.cabControl().cabModule().read(data.getCompound(data.contains("CabControls") ? "CabControls" : "TestControls"))
                    : (CabModuleView) host.createCabModule();
            driver.cabTimetable = new CreateCabTimetable(key, vehicleMotion.level.registryAccess(), data);
        }
        driver.cabControls.submit(HexieBridges.cabControl().cabModule().normalize(host.trainControl(state), driver.cabControls.state()));
        var previous = driver.cabControls.state();
        int previousKeyboard = driver.cabControls.keyboardMask();
        var converted = input.apply(previous, vehicleMotion.runtime.speed(car.getUUID()));
        if (converted == null) return false;
        if (!seated) converted = converted.withApproach(false);
        boolean wasAutomatic = driver.cabControls.state().automatic();
        boolean wasSimple = driver.cabControls.state().simple();
        vehicleMotion.cabControl.controllers.put(key, driver);
        driver.cabConverter = host.trainControl(state);
        driver.cabBlock = state;
        driver.player = player.getUUID(); driver.attended = seated;
        driver.cabFacing = facing; driver.lastInput = vehicleMotion.level.getGameTime();
        HexieBridges.cabControl().simpleCabControls().submitAndNotify(player, driver.cabControls, converted);
        vehicleMotion.synchronizeCabDemands(key, previous, converted, operated);
        if (keyboardMask < 0 || !previous.equals(converted) || keyboardMask != previousKeyboard)
            driver.inputSequence = NATIVE_INPUT_SEQUENCE.incrementAndGet();
        if (wasAutomatic != converted.automatic()) vehicleMotion.releaseCabApproach(car.getUUID(), driver);
        if (!seated || wasAutomatic != converted.automatic() || wasSimple != converted.simple()) {
            driver.cabControls.keyboard(0);
        }
        if (keyboardMask >= 0) {
            driver.cabControls.keyboard(keyboardMask);
            driver.lastKeyboardInput = vehicleMotion.level.getGameTime();
        }
        if (converted.approachHeld() && (converted.simple() || !converted.automatic())) {
            driver.approachHeld = true; driver.lastApproachInput = vehicleMotion.level.getGameTime();
        } else vehicleMotion.releaseCabApproach(car.getUUID(), driver);
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(car.getUUID());
        if (train != null) {
            if (converted.simple()) syncTrainHud(train, driver, player);
            if (wasSimple && (!converted.simple() || !converted.enabled())) train.manualSteer = SteerDirection.NONE;
            if (converted.enabled() && !converted.automatic() && !driver.approachHeld) {
                var execution = driver.cabTimetable;
                execution.with(train, execution == vehicleMotion.selectedCabTimetable(car.getUUID()), () -> {
                    CreateNavigationState.clearSignalWait(train);
                    return null;
                });
            }
        }
        vehicleMotion.applyControls(car.getUUID());
        return true;
    }

    public static boolean occupiesCab(Player player, UUID carriage, BlockPos position) {
        var cab = DrivingConsoleContext.find(player);
        return cab != null && cab.console().equals(position)
                && cab.seatAccess().vehicle().getUUID().equals(carriage);
    }

    public boolean startControl(SnapshotCarriageEntity entity, BlockPos pos, Player player) {
        if (!validControl(entity, pos, player) || vehicleMotion.isDerailed(entity.getUUID())) return false;
        // Create owns the per-entity control handoff. It does not impose a train-wide exclusive seat.
        vehicleMotion.cabControl.controllers.entrySet().removeIf(e -> e.getKey().carriage().equals(entity.getUUID()) && e.getValue().cabControls == null);
        vehicleMotion.cabControl.controllers.put(new AssembledVehicleCabControl.ControlKey(entity.getUUID(), pos), new AssembledVehicleCabControl.Controller(player.getUUID(), pos, vehicleMotion.level.getGameTime()));
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(entity.getUUID());
        // Taking the controls changes the actuator source, not the timetable or its selected route.
        if (train != null) CreateNavigationState.clearSignalWait(train);
        vehicleMotion.applyControls(entity.getUUID());
        return true;
    }

    /** Uses Create's existing controls input packets and timeout handling, with our own forces. */
    public boolean control(SnapshotCarriageEntity entity, BlockPos pos, Collection<Integer> keys, Player player) {
        if (player == null) return false;
        AssembledVehicleCabControl.Controller driver = vehicleMotion.cabControl.controllers.get(new AssembledVehicleCabControl.ControlKey(entity.getUUID(), pos));
        if (driver == null || driver.cabControls != null || !driver.player.equals(player.getUUID())) {
            // Returning false tells Create to stop the whole entity's current driver. Discard
            // late input from a former driver or desk while the replacement remains in control.
            return vehicleMotion.cabControl.controllers.entrySet().stream().anyMatch(e ->
                    e.getKey().carriage().equals(entity.getUUID()) && e.getValue().cabControls == null);
        }
        if (!validControl(entity, pos, player) || vehicleMotion.isDerailed(entity.getUUID()) || keys.contains(5)) return false;
        driver.lastInput = vehicleMotion.level.getGameTime();
        driver.timedOut = false;
        int direction = (keys.contains(0) ? 1 : 0) - (keys.contains(1) ? 1 : 0);
        int steering = (keys.contains(2) ? 1 : 0) - (keys.contains(3) ? 1 : 0);
        var state = entity.getContraption().getBlocks().get(pos).state();
        // FACING points towards the operator (ControlsBlock's placement uses the opposite
        // of the player's look direction). W must drive away from that face. Our positive
        // route direction follows the assembly scan, unlike Create's native train speed.
        boolean inverted = state.getValue(ControlsBlock.FACING) == vehicleMotion.forward(entity.getUUID());
        if (inverted) {
            direction = -direction;
            steering = -steering;
        }
        double speed = vehicleMotion.runtime.speed(entity.getUUID());
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(entity.getUUID());
        var backendInput = train == null ? null : CreateTrackCompatibility.manualInput(train);
        if (backendInput != null) direction = backendInput.reverser();
        double power = backendInput == null ? Math.abs(direction) : backendInput.power();
        double brake = backendInput == null ? 0 : backendInput.brake();
        if (power > 0 && direction * speed < -1e-5) brake = Math.max(brake, 1);
        driver.nativeRequest = HexieBridges.cabControl().trainControl().service(TrainControlSource.CREATE_MANUAL, power * direction, brake);
        driver.nativeBasis = vehicleMotion.forward(entity.getUUID());
        driver.inputSequence = NATIVE_INPUT_SEQUENCE.incrementAndGet();
        if (train != null) {
            if (!vehicleMotion.hasEnabledCabModule(entity.getUUID()))
                train.manualSteer = steering < 0 ? SteerDirection.LEFT : steering > 0 ? SteerDirection.RIGHT : SteerDirection.NONE;
            train.manualTick = vehicleMotion.isManuallyControlled(entity.getUUID());
            syncTrainHud(train, driver, player);
            if (!vehicleMotion.hasEnabledCabModule(entity.getUUID()) && train.speedBeforeStall != null && power > 0 && direction != 0
                    && Math.signum(train.speedBeforeStall) != -direction)
                train.cancelStall();
            if (controlStationApproach(train, driver, player, keys.contains(4), direction, speed, !inverted))
                driver.nativeRequest = new TrainCommand(0, driver.nativeRequest.brake());
        }
        vehicleMotion.applyControls(entity.getUUID());
        return true;
    }

    public void syncTrainHud(CompatibilityTrain train, AssembledVehicleCabControl.Controller driver, Player player) {
        long now = vehicleMotion.level.getGameTime();
        if (player instanceof ServerPlayer serverPlayer && now >= driver.nextHud) {
            CatnipServices.NETWORK.sendToClient(serverPlayer, new TrainHUDUpdatePacket.Clientbound(train));
            driver.nextHud = now + 6;
        }
    }

    /** Preserve Create's station search, navigation and prompts without using its motion integrator. */
    public boolean controlStationApproach(CompatibilityTrain train, AssembledVehicleCabControl.Controller driver, Player player,
                                           boolean space, int direction, double speed, boolean forwardCab) {
        if (CreateSharedCabTimetables.hasConflict(vehicleMotion, train.carriageIds.getFirst())) {
            driver.approachingStation = false;
            return false;
        }
        if (!space && driver.approachingStation) CreateNavigationState.clearSignalWait(train);
        var station = train.isWaitingForDeparture() ? train.waitingStation() : null;
        if (station != null && (!space || vehicleMotion.compatibility.dockedAtStation(train, station))) {
            driver.approachingStation = false;
            if (space) {
                if (driver.cabControls == null)
                    driver.nativeRequest = HexieBridges.cabControl().trainControl().service(TrainControlSource.CREATE_MANUAL, 0, 1);
                sendPrompt(player, CreateLang.translateDirect("train.arrived_at",
                        Component.literal(station.name).withStyle(s -> s.withColor(0x704630))), false);
                return true;
            }
            if (train.runtime.getSchedule() == null && train.navigation.destination != null)
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.MANUAL_APPROACH_CANCELLED);
            if (direction != 0) {
                driver.stationMessage = false;
                sendPrompt(player, CreateLang.translateDirect("train.departing_from",
                        Component.literal(station.name).withStyle(s -> s.withColor(0x704630))), false);
            }
            return false;
        }

        var navigation = train.navigation;
        if (navigation.destination != null) {
            if (space) {
                driver.approachingStation = true;
                double remaining = navigation.distanceToDestination / Math.max(1e-6, navigation.distanceStartedAt);
                int progress = (int) (Math.clamp(1 - (1 - remaining) * (1 - remaining), 0, 1) * 30);
                sendPrompt(player, Component.literal("|".repeat(30 - progress)).withStyle(s -> s.withColor(0x529915))
                        .append(Component.literal("|".repeat(progress)).withStyle(s -> s.withColor(0x544D45))), true);
                return true;
            }
            if (train.runtime.getSchedule() == null)
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.MANUAL_APPROACH_CANCELLED);
        }
        driver.approachingStation = false;
        // A running timetable owns its route even between instructions. Space must not insert a different station.
        if (train.runtime.getSchedule() != null) return false;
        double directedSpeed = direction != 0 ? direction : speed;
        var lookAhead = navigation.findNearestApproachable(directedSpeed != 0 ? directedSpeed < 0 : !forwardCab);
        if (lookAhead != null) {
            if (space) {
                navigation.startNavigation(navigation.findPathTo(lookAhead, -1));
                driver.approachingStation = navigation.destination != null;
                if (driver.approachingStation) return true;
            }
            sendPrompt(player, CreateLang.translateDirect("contraption.controls.approach_station",
                    Component.keybind("key.jump"), lookAhead.name), false);
            driver.stationMessage = true;
        } else if (driver.stationMessage) {
            sendPrompt(player, Component.empty(), false);
            driver.stationMessage = false;
        }
        return false;
    }

    public static void sendPrompt(Player player, MutableComponent message, boolean shadow) {
        if (player instanceof ServerPlayer serverPlayer)
            CatnipServices.NETWORK.sendToClient(serverPlayer, new TrainPromptPacket(message, shadow));
    }

    public boolean ownsNativeControls(UUID carriage, BlockPos pos, UUID player) {
        var driver = vehicleMotion.cabControl.controllers.get(new AssembledVehicleCabControl.ControlKey(carriage, pos));
        return driver != null && driver.cabControls == null && driver.player.equals(player);
    }

    public void stopControl(SnapshotCarriageEntity entity, BlockPos pos) {
        // Native handoff can supply the newly clicked position when stopping the previous controller.
        boolean removed = vehicleMotion.cabControl.controllers.entrySet().removeIf(e -> e.getKey().carriage().equals(entity.getUUID())
                && e.getValue().cabControls == null);
        if (removed) {
            vehicleMotion.releaseCompatibilityControl(entity.getUUID());
            vehicleMotion.applyControls(entity.getUUID());
        }
    }

    public boolean validControl(SnapshotCarriageEntity entity, BlockPos pos, Player player) {
        if (player == null || player.isSpectator() || player.level() != vehicleMotion.level || entity.level() != vehicleMotion.level
                || entity.isRemoved() || !vehicleMotion.owns(entity.getUUID())
                || !vehicleMotion.sourceAssembly(entity.getUUID()).equals(entity.assemblyId()) || entity.getContraption() == null)
            return false;
        var info = entity.getContraption().getBlocks().get(pos);
        return info != null && info.state().getBlock() instanceof ControlsBlock
                && info.state().getValue(ControlsBlock.FACING).getAxis() == vehicleMotion.forward(entity.getUUID()).getAxis()
                && entity.canInteractWithBlock(player, Vec3.atCenterOf(pos), 8);
    }

    public void expireControls(long now) {
        // Usually empty; never enumerate the world's players/entities or all train blocks.
        vehicleMotion.cabControl.controllers.entrySet().removeIf(entry -> {
            AssembledVehicleCabControl.Controller driver = entry.getValue();
            UUID member = entry.getKey().carriage();
            var player = vehicleMotion.level.getPlayerByUUID(driver.player);
            boolean expired = player == null || player.isSpectator();
            if (driver.cabControls != null) {
                var cab = DrivingConsoleContext.find(player);
                expired |= cab == null || !cab.seatAccess().vehicle().getUUID().equals(member)
                        || !cab.console().equals(driver.pos);
                driver.attended = !expired;
                if (expired) vehicleMotion.releaseCabApproach(member, driver);
                if (expired || now - driver.lastKeyboardInput > 20) driver.cabControls.keyboard(0);
                return false; // Enable and handles belong to each module, independent of operator attendance.
            }
            if (expired || !driver.timedOut && now - driver.lastInput > 40) {
                // A later keepalive may resume the session, but its old command has expired.
                driver.nativeRequest = null;
                driver.approachingStation = false;
                driver.timedOut = true;
                vehicleMotion.releaseCompatibilityControl(member);
            }
            return expired;
        });
    }
}
