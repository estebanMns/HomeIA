-- Facturación de energía: lecturas del medidor (kWh acumulados, como en el recibo)
-- y tarifas por período con subsidio por tramo.

CREATE TABLE meter_readings (
    id           UUID PRIMARY KEY,
    home_id      UUID           NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    reading_kwh  NUMERIC(14, 3) NOT NULL,
    read_at      DATE           NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_meter_readings_home_date UNIQUE (home_id, read_at)
);

CREATE TABLE tariff_periods (
    id                    UUID PRIMARY KEY,
    home_id               UUID           NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    period_start          DATE           NOT NULL,
    period_end            DATE           NOT NULL,
    unit_price_per_kwh    NUMERIC(12, 2) NOT NULL,
    subsidy_kwh_limit     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    subsidy_percent       NUMERIC(6, 3)  NOT NULL DEFAULT 0,
    public_lighting       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    adjustment            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_tariff_period_order CHECK (period_end > period_start),
    CONSTRAINT uk_tariff_home_start UNIQUE (home_id, period_start)
);
