package com.home.ia.application.port.out;

import com.home.ia.domain.model.home.RoomId;

import java.time.Instant;

public interface OccupancyPredictionPort {
    /**
     * Probabilidad (0 a 1) de que alguien vuelva pronto a la habitación.
     */
    double probabilityOfReturn(RoomId roomId, Instant now);
}