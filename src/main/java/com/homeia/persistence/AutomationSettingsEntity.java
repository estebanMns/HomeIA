package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_settings")
@Getter
@Setter
@NoArgsConstructor
public class AutomationSettingsEntity {

    @Id
    @Column(name = "home_id")
    private UUID homeId;

    @Column(nullable = false)
    private boolean enabled;

    /** Minutos sin presencia antes de apagar dispositivos no críticos. */
    @Column(name = "vacancy_minutes", nullable = false)
    private int vacancyMinutes;

    /** Probabilidad (0-100) a partir de la cual se considera que alguien regresará y no se apaga. */
    @Column(name = "return_sensitivity_percent", nullable = false)
    private int returnSensitivityPercent;

    /** Consumo por encima del cual se genera alerta de consumo excesivo. */
    @Column(name = "consumption_alert_watts", nullable = false)
    private double consumptionAlertWatts;

    /** Minutos encendido sin presencia antes de sospechar fuga de energía. */
    @Column(name = "leak_minutes", nullable = false)
    private int leakMinutes;

    /** Minutos que el consumo debe mantenerse sobre el umbral para alertar consumo excesivo. */
    @Column(name = "consumption_sustained_minutes", nullable = false)
    private int consumptionSustainedMinutes = 10;

    /** Minutos sin repetir la misma alerta del mismo dispositivo, aunque ya se haya revisado. */
    @Column(name = "alert_cooldown_minutes", nullable = false)
    private int alertCooldownMinutes = 30;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AutomationSettingsEntity(UUID homeId, boolean enabled, int vacancyMinutes,
                                    int returnSensitivityPercent, double consumptionAlertWatts, int leakMinutes) {
        this.homeId = homeId;
        this.enabled = enabled;
        this.vacancyMinutes = vacancyMinutes;
        this.returnSensitivityPercent = returnSensitivityPercent;
        this.consumptionAlertWatts = consumptionAlertWatts;
        this.leakMinutes = leakMinutes;
    }
}
