package com.home.ia.application.service;

import com.home.ia.application.port.in.AutomationResult;
import com.home.ia.application.port.out.DeviceCommandPort;
import com.home.ia.application.port.out.DeviceRepositoryPort;
import com.home.ia.application.port.out.DomainEventPublisherPort;
import com.home.ia.application.port.out.OccupancyPredictionPort;
import com.home.ia.domain.event.DevicesSwitchedOffAutomaticallyEvent;
import com.home.ia.domain.model.device.ConnectionStatus;
import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.device.Light;
import com.home.ia.domain.model.device.PowerState;
import com.home.ia.domain.model.device.PresenceSensor;
import com.home.ia.domain.model.device.SmartOutlet;
import com.home.ia.domain.model.home.RoomId;
import com.home.ia.domain.policy.OccupancyPolicy;
import com.home.ia.domain.policy.OutletProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SwitchOffVacantRoomsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");

    private final RoomId room = RoomId.generate();

    private DeviceRepositoryPort repository;
    private DeviceCommandPort commandPort;
    private OccupancyPredictionPort predictionPort;
    private DomainEventPublisherPort eventPublisher;
    private SwitchOffVacantRoomsService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeviceRepositoryPort.class);
        commandPort = mock(DeviceCommandPort.class);
        predictionPort = mock(OccupancyPredictionPort.class);
        eventPublisher = mock(DomainEventPublisherPort.class);

        service = new SwitchOffVacantRoomsService(
                repository, commandPort, predictionPort, eventPublisher,
                new OccupancyPolicy(Duration.ofMinutes(15), 0.7),
                new OutletProtectionPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void apagaLasLucesCuandoLaHabitacionLlevaVaciaMasDelTiempoLimite() {
        Light light = lightOn();
        givenSensor(vacantSensor(Duration.ofMinutes(20)));
        givenDevicesInRoom(light);
        when(predictionPort.probabilityOfReturn(room, NOW)).thenReturn(0.1);

        AutomationResult result = service.execute();

        verify(commandPort).sendPowerCommand(light.getId(), PowerState.OFF);
        verify(repository).save(light);
        verify(eventPublisher).publish(any(DevicesSwitchedOffAutomaticallyEvent.class));
        assertFalse(light.isOn());
        assertEquals(List.of(light.getId()), result.switchedOffDevices());
    }

    @Test
    void noApagaNadaSiLaHabitacionEstaOcupada() {
        givenSensor(occupiedSensor());
        givenDevicesInRoom(lightOn());

        AutomationResult result = service.execute();

        verify(commandPort, never()).sendPowerCommand(any(), any());
        assertTrue(result.switchedOffDevices().isEmpty());
    }

    @Test
    void noApagaUnTomacorrienteCritico() {
        Light light = lightOn();
        SmartOutlet fridge = outletOn(true);
        SmartOutlet tv = outletOn(false);
        givenSensor(vacantSensor(Duration.ofMinutes(20)));
        givenDevicesInRoom(light, fridge, tv);

        service.execute();

        verify(commandPort, never()).sendPowerCommand(fridge.getId(), PowerState.OFF);
        verify(commandPort).sendPowerCommand(tv.getId(), PowerState.OFF);
        assertTrue(fridge.isOn());
        assertFalse(tv.isOn());
    }

    @Test
    void siLaIaFallaIgualApagaUsandoLaReglaDeTiempo() {
        Light light = lightOn();
        givenSensor(vacantSensor(Duration.ofMinutes(20)));
        givenDevicesInRoom(light);
        when(predictionPort.probabilityOfReturn(any(), any()))
                .thenThrow(new RuntimeException("Modelo no disponible"));

        service.execute();

        verify(commandPort).sendPowerCommand(light.getId(), PowerState.OFF);
    }

    @Test
    void noApagaSiLaIaPrediceQueLaPersonaVuelvePronto() {
        givenSensor(vacantSensor(Duration.ofMinutes(20)));
        givenDevicesInRoom(lightOn());
        when(predictionPort.probabilityOfReturn(room, NOW)).thenReturn(0.9);

        service.execute();

        verify(commandPort, never()).sendPowerCommand(any(), any());
    }

    // ---------- Datos de prueba ----------

    private void givenSensor(PresenceSensor sensor) {
        when(repository.findAllPresenceSensors()).thenReturn(List.of(sensor));
    }

    private void givenDevicesInRoom(com.home.ia.domain.model.device.SwitchableDevice... devices) {
        when(repository.findSwitchableByRoom(room)).thenReturn(List.of(devices));
    }

    private PresenceSensor vacantSensor(Duration vacantFor) {
        return new PresenceSensor(DeviceId.generate(), "Sensor sala", room,
                ConnectionStatus.ONLINE, false, NOW.minus(vacantFor));
    }

    private PresenceSensor occupiedSensor() {
        return new PresenceSensor(DeviceId.generate(), "Sensor sala", room,
                ConnectionStatus.ONLINE, true, NOW.minusSeconds(60));
    }

    private Light lightOn() {
        return new Light(DeviceId.generate(), "Luz sala", room,
                ConnectionStatus.ONLINE, PowerState.ON, 12);
    }

    private SmartOutlet outletOn(boolean critical) {
        return new SmartOutlet(DeviceId.generate(), critical ? "Nevera" : "TV", room,
                ConnectionStatus.ONLINE, PowerState.ON, 150, critical);
    }
}
