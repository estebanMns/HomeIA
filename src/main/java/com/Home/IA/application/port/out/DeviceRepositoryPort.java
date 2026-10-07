package com.home.ia.application.port.out;

import com.home.ia.domain.model.device.Device;
import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.device.PresenceSensor;
import com.home.ia.domain.model.device.SwitchableDevice;
import com.home.ia.domain.model.home.RoomId;

import java.util.List;
import java.util.Optional;

public interface DeviceRepositoryPort {
    Optional<Device> findById(DeviceId id);

    List<PresenceSensor> findAllPresenceSensors();

    List<SwitchableDevice> findSwitchableByRoom(RoomId roomId);

    void save(Device device);
}