package com.homeia.service;

import com.homeia.domain.DeviceEventType;
import com.homeia.persistence.DeviceEventEntity;
import com.homeia.persistence.DeviceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Historial por dispositivo: descubrimiento, importación, conexión, órdenes y errores. */
@Service
@RequiredArgsConstructor
public class DeviceEventService {

    private final DeviceEventRepository repository;

    @Transactional
    public void record(UUID homeId, UUID deviceId, UUID edgeId, DeviceEventType type, String details) {
        repository.save(new DeviceEventEntity(homeId, deviceId, edgeId, type, details));
    }

    @Transactional(readOnly = true)
    public List<DeviceEventEntity> forDevice(UUID homeId, UUID deviceId, int limit) {
        return repository.findByDeviceIdAndHomeIdOrderByCreatedAtDesc(deviceId, homeId,
                PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    @Transactional(readOnly = true)
    public List<DeviceEventEntity> forHome(UUID homeId, int limit) {
        return repository.findByHomeIdOrderByCreatedAtDesc(homeId, PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }
}
