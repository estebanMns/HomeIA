-- Potencia nominal de lo conectado (p. ej. un bombillo LED de 9 W). Los interruptores sin medidor
-- (Tuya "kg", relés) no reportan vatios: con este valor se estima su consumo mientras están encendidos.
ALTER TABLE devices ADD COLUMN rated_watts DOUBLE PRECISION;
