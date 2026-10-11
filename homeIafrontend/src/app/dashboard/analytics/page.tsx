'use client';

import { useState } from 'react';
import { Card, SectionTitle, Button } from '@/components/ui/Primitives';
import BillingPanel from '@/components/billing/BillingPanel';
import { api } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import type { EnergyBucket, EnergySummary, RoomEnergy } from '@/types';

const RANGES = {
  '24h': { hours: 24, bucket: 'hour' as const, label: 'Últimas 24 h' },
  '7d': { hours: 24 * 7, bucket: 'day' as const, label: 'Últimos 7 días' },
  '30d': { hours: 24 * 30, bucket: 'day' as const, label: 'Últimos 30 días' },
};

type RangeKey = keyof typeof RANGES;

interface Data {
  summary: EnergySummary;
  series: EnergyBucket[];
  rooms: RoomEnergy[];
}

export default function AnalyticsPage() {
  const [range, setRange] = useState<RangeKey>('24h');
  const cfg = RANGES[range];

  const { data, error, loading } = useAsync<Data>(async () => {
    const to = new Date();
    const from = new Date(to.getTime() - cfg.hours * 3600 * 1000);
    const [summary, series, rooms] = await Promise.all([
      api.energySummary(from.toISOString(), to.toISOString()),
      api.energySeries(from.toISOString(), to.toISOString(), cfg.bucket),
      api.energyByRoom(from.toISOString(), to.toISOString()),
    ]);
    return { summary, series, rooms };
  }, [range], 60000);

  const money = (n: number) => `${n.toLocaleString('es-CO')} ${data?.summary.currency ?? ''}`.trim();
  const maxKwh = Math.max(...(data?.series.map((b) => b.kwh) ?? [0]), 0.001);

  return (
    <div className="page-content">
      <SectionTitle
        title="Consumo y ahorro"
        subtitle="Energía integrada a partir de las lecturas de cada dispositivo"
        action={
          <div className="quick-actions">
            {(Object.keys(RANGES) as RangeKey[]).map((key) => (
              <Button key={key} variant={range === key ? 'primary' : 'outline'} size="sm" onClick={() => setRange(key)}>
                {RANGES[key].label}
              </Button>
            ))}
          </div>
        }
      />

      {error && <Card style={{ marginBottom: 16, color: 'var(--red)' }}>{error}</Card>}
      {loading && !data && <div className="muted">Cargando…</div>}

      {data && (
        <>
          <div className="kpi-grid">
            <Card className="kpi-card">
              <div className="kpi-title">Energía consumida</div>
              <div className="kpi-value">{data.summary.totalKwh.toLocaleString('es-CO')} <span>kWh</span></div>
              <div className="kpi-note">{cfg.label}</div>
            </Card>
            <Card className="kpi-card">
              <div className="kpi-title">Costo estimado</div>
              <div className="kpi-value">{money(data.summary.cost)}</div>
              <div className="kpi-note">Tarifa {money(data.summary.pricePerKwh)} / kWh</div>
            </Card>
            <Card className="kpi-card">
              <div className="kpi-title">Pico de potencia</div>
              <div className="kpi-value">{Math.round(data.summary.peakWatts).toLocaleString('es-CO')} <span>W</span></div>
              <div className="kpi-note">Máximo registrado en el periodo</div>
            </Card>
            <Card className="kpi-card">
              <div className="kpi-title">Potencia que la IA evitó</div>
              <div className="kpi-value">{Math.round(data.summary.avoidedWattsEstimate).toLocaleString('es-CO')} <span>W</span></div>
              <div className="kpi-note">≈ {money(data.summary.avoidedCostPerHour)} por hora de apagado</div>
            </Card>
          </div>

          <div className="dashboard-grid">
            <Card>
              <SectionTitle title="Consumo por periodo" subtitle={cfg.bucket === 'hour' ? 'Por hora' : 'Por día'} />
              {data.series.length === 0 ? (
                <div className="muted">Sin lecturas en este periodo.</div>
              ) : (
                <div className="chart-wrap" style={{ height: 220, paddingLeft: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 180 }}>
                    {data.series.map((b) => (
                      <div
                        key={b.start}
                        title={`${new Date(b.start).toLocaleString('es-CO')}: ${b.kwh} kWh`}
                        style={{
                          flex: 1,
                          minWidth: 4,
                          height: `${Math.max(2, (b.kwh / maxKwh) * 100)}%`,
                          background: 'var(--green)',
                          borderRadius: '3px 3px 0 0',
                        }}
                      />
                    ))}
                  </div>
                </div>
              )}
            </Card>

            <Card>
              <SectionTitle title="Consumo por habitación" subtitle="Ranking del periodo" />
              {data.rooms.length === 0 ? (
                <div className="muted">Sin consumo asignado a habitaciones.</div>
              ) : (
                <div className="ranking">
                  {data.rooms.map((r, i) => (
                    <div key={r.roomId}>
                      <i>{i + 1}</i>
                      <div>
                        <strong>{r.roomName}</strong>
                        <span>{money(r.cost)}</span>
                      </div>
                      <div className="rank-value"><strong>{r.kwh.toLocaleString('es-CO')} kWh</strong></div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>
        </>
      )}

      <BillingPanel />
    </div>
  );
}
