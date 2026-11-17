'use client';

import { useEffect, useState } from 'react';
import type { Device } from '@/types';
import { effectiveCapabilities } from '@/lib/capabilities';

/** "3 h 12 min", "12 min 05 s"… */
function formatElapsed(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h} h ${String(m).padStart(2, '0')} min`;
  return `${m} min ${String(s).padStart(2, '0')} s`;
}

/**
 * Consumo de la sesión actual: desde que el dispositivo se encendió hasta ahora, actualizado cada segundo.
 * Con medidor (ENERGY) usa los vatios que reporta; sin medidor, la potencia nominal que definió el usuario.
 * El costo es aproximado con la tarifa base de la casa; el valor exacto (con tramos) está en Consumo y ahorro.
 */
export default function LiveUsage({ device, pricePerKwh, currency }: { device: Device; pricePerKwh?: number; currency?: string }) {
  const metered = effectiveCapabilities(device).has('ENERGY');
  const on = device.powerState === 'ON' && device.connectionStatus === 'ONLINE';
  const watts = device.consumptionWatts;
  const since = device.lastStatusChangeAt ? Date.parse(device.lastStatusChangeAt) : NaN;
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!on || !(watts > 0) || Number.isNaN(since)) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [on, watts, since]);

  if (!metered && device.ratedWatts == null && device.type !== 'PRESENCE_SENSOR') {
    return (
      <div>
        <strong>—</strong>
        <span className="device-provider">Sin medidor: define la potencia en Editar</span>
      </div>
    );
  }

  const label = `${watts.toLocaleString('es-CO', { maximumFractionDigits: 1 })} W${metered ? '' : ' (estimado)'}`;
  if (!on || !(watts > 0) || Number.isNaN(since)) {
    return <strong>{label}</strong>;
  }

  const elapsed = now - since;
  const kwh = (watts * elapsed) / 3_600_000 / 1000;
  const cost = pricePerKwh ? kwh * pricePerKwh : null;
  return (
    <div>
      <strong>{label}</strong>
      <span className="device-provider" title="Desde que se encendió; se reinicia al apagarlo">
        Encendido hace {formatElapsed(elapsed)}
      </span>
      <span className="device-provider live-usage">
        {kwh.toLocaleString('es-CO', { maximumFractionDigits: 4, minimumFractionDigits: 4 })} kWh
        {cost != null && ` · ${cost.toLocaleString('es-CO', { style: 'currency', currency: currency || 'COP', maximumFractionDigits: 2 })}`}
      </span>
    </div>
  );
}
