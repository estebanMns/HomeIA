'use client';

import { useState } from 'react';
import { Card, SectionTitle, Button } from '@/components/ui/Primitives';
import BillingPanel from '@/components/billing/BillingPanel';
import { api } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useEnergyStats } from '@/hooks/useEnergyStats';
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
  /** Serie por hora del período, para las estadísticas que calcula el Web Worker. */
  hourly: EnergyBucket[];
  rooms: RoomEnergy[];
  timeZone: string;
}

const threadLabel = { worker: 'Web Worker', main: 'hilo principal', 'main-chunked': 'hilo principal por bloques' } as const;

export default function AnalyticsPage() {
  const [range, setRange] = useState<RangeKey>('24h');
  const cfg = RANGES[range];

  const { data, error, loading } = useAsync<Data>(async () => {
    const to = new Date();
    const from = new Date(to.getTime() - cfg.hours * 3600 * 1000);
    const [summary, series, hourly, rooms, home] = await Promise.all([
      api.energySummary(from.toISOString(), to.toISOString()),
      api.energySeries(from.toISOString(), to.toISOString(), cfg.bucket),
      cfg.bucket === 'hour' ? null : api.energySeries(from.toISOString(), to.toISOString(), 'hour'),
      api.energyByRoom(from.toISOString(), to.toISOString()),
      api.home(),
    ]);
    return { summary, series, hourly: hourly ?? series, rooms, timeZone: home.timezone };
  }, [range], 60000);

  const { result: stats, error: statsError } = useEnergyStats(data?.hourly, data?.timeZone);
  const maxHourWatts = Math.max(...(stats?.stats.hourlyProfile.map((h) => h.avgWatts) ?? [0]), 1);

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

          <div className="dashboard-grid" style={{ marginTop: 16 }}>
            <Card>
              <SectionTitle
                title="Perfil horario"
                subtitle={stats ? `Consumo promedio por hora del día · calculado en ${threadLabel[stats.thread]} en ${Math.round(stats.ms)} ms` : 'Calculando…'}
              />
              {statsError && <div style={{ color: 'var(--red)' }}>{statsError}</div>}
              {stats && stats.stats.points === 0 && <div className="muted">Sin lecturas en este periodo.</div>}
              {stats && stats.stats.points > 0 && (
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 160 }}>
                  {stats.stats.hourlyProfile.map((h) => (
                    <div key={h.hour} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, height: '100%', justifyContent: 'flex-end' }}>
                      <div
                        title={`${h.hour}:00 · ${h.avgWatts.toLocaleString('es-CO')} W promedio`}
                        style={{ width: '100%', height: `${Math.max(2, (h.avgWatts / maxHourWatts) * 100)}%`, background: 'var(--blue)', borderRadius: '3px 3px 0 0' }}
                      />
                      <small className="muted" style={{ fontSize: 9 }}>{h.hour % 3 === 0 ? h.hour : ''}</small>
                    </div>
                  ))}
                </div>
              )}
            </Card>

            <Card>
              <SectionTitle title="Horas pico y anomalías" subtitle="Horas con consumo fuera de lo normal (puntaje z > 2)" />
              {stats && stats.stats.points > 0 && (
                <>
                  <div className="cost-row">
                    <span>Tendencia del periodo</span>
                    <strong>{stats.stats.trendPercent === null ? '—' : `${stats.stats.trendPercent > 0 ? '+' : ''}${stats.stats.trendPercent}%`}</strong>
                  </div>
                  <div className="cost-row"><span>Promedio · desviación</span><strong>{stats.stats.meanWatts} W · {stats.stats.stdWatts} W</strong></div>
                  <div className="section-heading" style={{ marginTop: 12 }}>Horas pico</div>
                  {stats.stats.peakHours.map((p) => (
                    <div key={p.start} className="cost-row">
                      <span>{new Date(p.start).toLocaleString('es-CO', { timeZone: data.timeZone, dateStyle: 'short', timeStyle: 'short' })}</span>
                      <strong>{p.averageWatts.toLocaleString('es-CO')} W</strong>
                    </div>
                  ))}
                  <div className="section-heading" style={{ marginTop: 12 }}>Anomalías</div>
                  {stats.stats.anomalies.length === 0 && <div className="muted">No hay horas anómalas en el periodo.</div>}
                  {stats.stats.anomalies.map((a) => (
                    <div key={a.start} className="cost-row">
                      <span>{new Date(a.start).toLocaleString('es-CO', { timeZone: data.timeZone, dateStyle: 'short', timeStyle: 'short' })}</span>
                      <strong style={{ color: 'var(--amber)' }}>{a.averageWatts.toLocaleString('es-CO')} W · z {a.zScore}</strong>
                    </div>
                  ))}
                </>
              )}
            </Card>
          </div>
        </>
      )}

      <BillingPanel />
    </div>
  );
}
