package com.home.ia.infrastructure.adapter.persistence;

import com.home.ia.application.port.out.DeviceRepositoryPort;
import com.home.ia.domain.model.device.*;
import com.home.ia.domain.model.home.RoomId;
import com.home.ia.infrastructure.persistence.entity.DeviceEntity;
import com.home.ia.infrastructure.persistence.repository.DeviceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeviceRepositoryAdapter implements DeviceRepositoryPort {

    private final DeviceJpaRepository jpaRepository;

    @Override
    public List<PresenceSensor> findAllPresenceSensors() {
        return jpaRepository.findAll().stream()
                .filter(entity -> entity.getType() == DeviceType.PRESENCE_SENSOR)
                .map(this::toPresenceSensor)
                .toList();
    }

    @Override
    public List<SwitchableDevice> findSwitchableByRoom(RoomId roomId) {
        return jpaRepository.findByRoomId(roomId.value().toString()).stream()
                .filter(entity -> entity.getType() == DeviceType.LIGHT ||
                                  entity.getType() == DeviceType.SMART_OUTLET)
                .map(this::toSwitchableDevice)
                .toList();
    }

    @Override
    public void save(Device device) {
        DeviceEntity entity = toEntity(device);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Device> findById(DeviceId deviceId) {
        return jpaRepository.findById(deviceId.value().toString())
                .map(entity -> {
                    return switch (entity.getType()) {
                        case LIGHT -> toLight(entity);
                        case SMART_OUTLET -> toSmartOutlet(entity);
                        case PRESENCE_SENSOR -> toPresenceSensor(entity);
                        default -> throw new IllegalArgumentException("Tipo no soportado");
                    };
                });
    }

    private PresenceSensor toPresenceSensor(DeviceEntity entity) {
        return new PresenceSensor(
                new DeviceId(UUID.fromString(entity.getId())),
                entity.getName(),
                new RoomId(UUID.fromString(entity.getRoomId())),
                entity.getConnectionStatus(),
                entity.getPresence() != null && entity.getPresence(),
                entity.getLastPresenceDetected()
        );
    }

    private Light toLight(DeviceEntity entity) {
        return new Light(
                new DeviceId(UUID.fromString(entity.getId())),
                entity.getName(),
                new RoomId(UUID.fromString(entity.getRoomId())),
                entity.getConnectionStatus(),
                entity.getPowerState(),
                entity.getConsumption() != null ? entity.getConsumption() : 100.0
        );
    }

    private SmartOutlet toSmartOutlet(DeviceEntity entity) {
        return new SmartOutlet(
                new DeviceId(UUID.fromString(entity.getId())),
                entity.getName(),
                new RoomId(UUID.fromString(entity.getRoomId())),
                entity.getConnectionStatus(),
                entity.getPowerState(),
                entity.getConsumption() != null ? entity.getConsumption() : 0.0,
                entity.getCritical() != null && entity.getCritical()
        );
    }

    private SwitchableDevice toSwitchableDevice(DeviceEntity entity) {
        return switch (entity.getType()) {
            case LIGHT -> toLight(entity);
            case SMART_OUTLET -> toSmartOutlet(entity);
            default -> throw new IllegalArgumentException("Tipo de dispositivo no soportado");
        };
    }

    private DeviceEntity toEntity(Device device) {
        DeviceEntity.DeviceEntityBuilder builder = DeviceEntity.builder()
                .id(device.getId().value().toString())
                .name(device.getName())
                .roomId(device.getRoomId().value().toString())
                .type(device.getType())
                .connectionStatus(device.getConnectionStatus());

        if (device instanceof Light light) {
            builder.powerState(light.getPowerState())
                    .consumption(light.getRatedPowerWatts());
        } else if (device instanceof SmartOutlet outlet) {
            builder.powerState(outlet.getPowerState())
                    .consumption(outlet.getRatedPowerWatts())
                    .critical(outlet.isCritical());
        } else if (device instanceof PresenceSensor sensor) {
            builder.presence(sensor.isOccupied())
                    .lastPresenceDetected(sensor.getLastChangeAt());
        }

        return builder.build();
    }
}
