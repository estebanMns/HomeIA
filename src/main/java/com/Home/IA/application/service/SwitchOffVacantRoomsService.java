package com.home.ia.application.service;

import com.home.ia.application.port.in.AutomationResult;
import com.home.ia.application.port.in.SwitchOffVacantRoomsUseCase;
import com.home.ia.application.port.out.DeviceCommandPort;
import com.home.ia.application.port.out.DeviceRepositoryPort;
import com.home.ia.application.port.out.DomainEventPublisherPort;
import com.home.ia.application.port.out.OccupancyPredictionPort;
import com.home.ia.domain.event.DevicesSwitchedOffAutomaticallyEvent;
import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.device.PowerState;
import com.home.ia.domain.model.device.PresenceSensor;
import com.home.ia.domain.model.device.SmartOutlet;
import com.home.ia.domain.model.device.SwitchableDevice;
import com.home.ia.domain.model.home.RoomId;
import com.home.ia.domain.policy.OccupancyPolicy;
import com.home.ia.domain.policy.OutletProtectionPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SwitchOffVacantRoomsService implements SwitchOffVacantRoomsUseCase {

    private final DeviceRepositoryPort deviceRepository;
    private final DeviceCommandPort deviceCommand;
    private final OccupancyPredictionPort occupancyPrediction;
    private final DomainEventPublisherPort eventPublisher;
    private final OccupancyPolicy occupancyPolicy;
    private final OutletProtectionPolicy outletProtectionPolicy;
    private final Clock clock;

    public SwitchOffVacantRoomsService(DeviceRepositoryPort deviceRepository,
            DeviceCommandPort deviceCommand,
            OccupancyPredictionPort occupancyPrediction,
            DomainEventPublisherPort eventPublisher,
            OccupancyPolicy occupancyPolicy,
            OutletProtectionPolicy outletProtectionPolicy,
            Clock clock) {
        this.deviceRepository = Objects.requireNonNull(deviceRepository);
        this.deviceCommand = Objects.requireNonNull(deviceCommand);
        this.occupancyPrediction = Objects.requireNonNull(occupancyPrediction);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.occupancyPolicy = Objects.requireNonNull(occupancyPolicy);
        this.outletProtectionPolicy = Objects.requireNonNull(outletProtectionPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AutomationResult execute() {
        Instant now = clock.instant();
        List<PresenceSensor> sensors = deviceRepository.findAllPresenceSensors();
        List<DeviceId> switchedOff = new ArrayList<>();

        for (PresenceSensor sensor : sensors) {
            double probabilityOfReturn = predictReturnSafely(sensor.getRoomId(), now);
            if (occupancyPolicy.shouldSwitchOffRoom(sensor, now, probabilityOfReturn)) {
                switchedOff.addAll(switchOffRoom(sensor.getRoomId(), now));
            }
        }
        return new AutomationResult(sensors.size(), switchedOff);
    }

    private List<DeviceId> switchOffRoom(RoomId roomId, Instant now) {
        List<DeviceId> switchedOff = new ArrayList<>();

        for (SwitchableDevice device : deviceRepository.findSwitchableByRoom(roomId)) {
            if (isEligibleForAutomaticSwitchOff(device)) {
                deviceCommand.sendPowerCommand(device.getId(), PowerState.OFF);
                device.turnOff();
                deviceRepository.save(device);
                switchedOff.add(device.getId());
            }
        }

        if (!switchedOff.isEmpty()) {
            eventPublisher.publish(
                    new DevicesSwitchedOffAutomaticallyEvent(roomId, switchedOff, now));
        }
        return switchedOff;
    }

    private boolean isEligibleForAutomaticSwitchOff(SwitchableDevice device) {
        if (device instanceof SmartOutlet outlet) {
            return outletProtectionPolicy.canDisableAutomatically(outlet);
        }
        return device.isOnline() && device.isOn();
    }

    /**
     * ASR-2: si el modelo de IA falla, se usa probabilidad 0
     * y el sistema sigue funcionando solo con la regla de tiempo.
     */
    private double predictReturnSafely(RoomId roomId, Instant now) {
        try {
            return occupancyPrediction.probabilityOfReturn(roomId, now);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }
}