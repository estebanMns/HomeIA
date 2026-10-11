'use client';

import { ReactNode, useMemo } from 'react';
import Link from 'next/link';
import { ChevronRight, Gauge, Lightbulb, Plug, Sparkles, Wifi, Zap } from 'lucide-react';
import { api } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import type { Alert, AutomationAction, Device, EnergyBucket, Room } from '@/types';
import { Badge, Card, SectionTitle } from '@/components/ui/Primitives';
import AlertCard from '@/components/ui/AlertCard';

interface Overview {
  devices: Device[];
  rooms: Room[];
  alerts: Alert[];
  metrics: Awaited<ReturnType<typeof api.systemMetrics>> | null;
  automation: Awaited<ReturnType<typeof api.automationStatus>> | null;
  energy: Awaited<ReturnType<typeof api.energySummary>> | null;
  series: EnergyBucket[];
  actions: AutomationAction[];
}

const settled = <T,>(r: PromiseSettledResult<T>): T | null => (r.status === 'fulfilled' ? r.value : null);

export default function DashboardPage() {
  const { data, error, loading } = useAsync<Overview>(async () => {
    const to = new Date();
    const from = new Date(to.getTime() - 24 * 3600 * 1000);
    const [devices, rooms, alerts, metrics, automation, energy, series, actions] = await Promise.allSettled([
      api.devices(),
      api.rooms(),
      api.alerts({ acknowledged: false, size: 3 }),
      api.systemMetrics(),
      api.automationStatus(),
      api.energySummary(from.toISOString(), to.toISOString()),
      api.energySeries(from.toISOString(), to.toISOString(), 'hour'),
      api.automationActions(0, 4),
    ]);
    return {
      devices: settled(devices) ?? [],
      rooms: settled(rooms) ?? [],
      alerts: settled(alerts)?.content ?? [],
      metrics: settled(metrics),
      automation: settled(automation),
      energy: settled(energy),
      series: settled(series) ?? [],
      actions: settled(actions)?.content ?? [],
    };
  }, [], 30000);

  const roomCards = useMemo(() => {
    if (!data) return [];
    return data.rooms.map((room) => {
      const list = data.devices.filter((d) => d.roomId === room.id);
      return {
        room,
        lights: list.filter((d) => d.type === 'LIGHT' && d.powerState === 'ON').length,
        plugs: list.filter((d) => d.type === 'SMART_OUTLET' && d.powerState === 'ON').length,
        presence: list.some((d) => d.presence === true),
        watts: Math.round(list.reduce((sum, d) => sum + d.consumptionWatts, 0)),
      };
    });
  }, [data]);

  if (loading && !data) {
    return <div className="page-content"><div className="muted">Cargando resumen…</div></div>;
  }

  const devicesOn = data?.devices.filter((d) => d.powerState === 'ON').length ?? 0;
  const devicesTotal = data?.devices.length ?? 0;

  return (
    <div className="page-content">
      {error && (
        <Card style={{ marginBottom: 16, color: 'var(--red)', borderColor: 'var(--red)' }}>{error}</Card>
      )}

      <div className="ai-status">
        <div className="ai-orb">
          <Sparkles size={18} />
        </div>
        <div>
          <strong>{data?.automation?.enabled ? 'Automatización activa' : 'Automatización en pausa'}</strong>
          <span>
            {data?.automation
              ? `${data.automation.decisionsLast24h} decisiones en 24 h · predicción ${data.automation.predictionMode === 'GEMINI' ? 'con Gemini' : 'por historial'}`
              : 'Sin datos de automatización'}
          </span>
        </div>
        <Badge tone={data?.automation?.enabled ? 'green' : 'gray'}>
          <span className="pulse-dot" /> {data?.automation?.enabled ? 'Activa' : 'Pausada'}
        </Badge>
      </div>

      <div className="kpi-grid">
        <KpiCard
          title="Consumo actual"
          value={data?.energy ? String(Math.round(data.energy.currentWatts)) : '—'}
          unit="W"
          icon={<Zap size={20} />}
          tone="green"
          note={<><span className="live-text">● EN VIVO</span> · {devicesOn} de {devicesTotal} encendidos</>}
        />
        <KpiCard
          title="Energía últimas 24 h"
          value={data?.energy ? data.energy.totalKwh.toLocaleString('es-CO') : '—'}
          unit="kWh"
          icon={<Gauge size={20} />}
          tone="blue"
          note={data?.energy ? `Costo ${data.energy.cost.toLocaleString('es-CO')} ${data.energy.currency}` : 'Sin lecturas'}
        />
        <KpiCard
          title="Conexiones MQTT"
          value={data?.metrics ? (data.metrics.mqttConnected ? 'Conectado' : 'Sin conexión') : '—'}
          icon={<Wifi size={20} />}
          tone="purple"
          note={data?.metrics ? `${data.metrics.devicesOnline} dispositivos en línea` : ''}
        />
        <KpiCard
          title="Alertas abiertas"
          value={data?.metrics ? String(data.metrics.alertsOpen) : '—'}
          icon={<Sparkles size={20} />}
          tone="teal"
          note={data?.metrics ? `${data.metrics.roomsTotal} habitaciones` : ''}
        />
      </div>

      <div className="dashboard-grid">
        <Card className="chart-card">
          <SectionTitle title="Consumo de energía" subtitle="Potencia promedio por hora · últimas 24 h" />
          <ConsumptionChart buckets={data?.series ?? []} />
        </Card>

        <Card className="actions-card">
          <SectionTitle
            title="Acciones de la IA"
            subtitle="Decisiones automáticas recientes"
            action={<Link href="/dashboard/automation" className="btn btn-ghost btn-sm">Ver todas</Link>}
          />
          {data?.actions.length ? (
            <div className="timeline">
              {data.actions.map((a) => (
                <div className="timeline-item" key={a.id}>
                  <div className={`timeline-icon ${a.actionType === 'SWITCHED_OFF_VACANT_ROOM' ? '' : 'protected'}`}>
                    <Sparkles size={15} />
                  </div>
                  <div>
                    <strong>{a.description}</strong>
                    <div>
                      <small>{new Date(a.createdAt).toLocaleString('es-CO')}</small>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="muted">Todavía no hay acciones registradas.</div>
          )}
        </Card>
      </div>

      <SectionTitle
        title="Tu hogar, habitación por habitación"
        subtitle="Dispositivos agrupados por habitación"
        action={<Link href="/dashboard/rooms" className="btn btn-outline btn-sm">Ver habitaciones <ChevronRight size={15} /></Link>}
      />
      {roomCards.length === 0 ? (
        <Card className="muted">Aún no hay habitaciones. Créalas en la sección Habitaciones.</Card>
      ) : (
        <div className="rooms-grid">
          {roomCards.map((r) => (
            <Card key={r.room.id} className="room-card">
              <div className="room-main">
                <div className="room-top">
                  <div className="room-icon">{r.presence ? '🛋' : '🚪'}</div>
                  <Badge tone={r.presence ? 'green' : 'gray'}>
                    <span className="status-dot" />
                    {r.presence ? 'Presencia' : 'Sin presencia'}
                  </Badge>
                </div>
                <div className="room-name">{r.room.name}</div>
                <div className="muted">{r.room.location ?? 'Sin ubicación'}</div>
                <div className="room-stats">
                  <span><Lightbulb size={15} />{r.lights} enc.</span>
                  <span><Plug size={15} />{r.plugs} enc.</span>
                  <strong>{r.watts.toLocaleString('es-CO')} W</strong>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Card className="alerts-panel">
        <SectionTitle
          title="Alertas activas"
          subtitle={`${data?.metrics?.alertsOpen ?? 0} sin atender`}
          action={<Link href="/dashboard/alerts" className="btn btn-ghost btn-sm">Ver todas <ChevronRight size={15} /></Link>}
        />
        {data?.alerts.length ? (
          <div className="alerts-list">
            {data.alerts.map((a) => (
              <AlertCard key={a.id} alert={a} />
            ))}
          </div>
        ) : (
          <div className="muted">No hay alertas abiertas.</div>
        )}
      </Card>
    </div>
  );
}

function KpiCard({
  title,
  value,
  unit,
  note,
  icon,
  tone,
}: {
  title: string;
  value: string;
  unit?: string;
  note: ReactNode;
  icon: ReactNode;
  tone: string;
}) {
  return (
    <Card className="kpi-card">
      <div className="kpi-top">
        <div className={`kpi-icon ${tone}`}>{icon}</div>
      </div>
      <div className="kpi-title">{title}</div>
      <div className="kpi-value">
        {value} <span>{unit}</span>
      </div>
      <div className="kpi-note">{note}</div>
    </Card>
  );
}

function ConsumptionChart({ buckets }: { buckets: EnergyBucket[] }) {
  if (buckets.length < 2) {
    return <div className="muted" style={{ padding: '40px 0' }}>Aún no hay suficientes lecturas para graficar.</div>;
  }
  const max = Math.max(...buckets.map((b) => b.averageWatts), 1);
  const width = 620;
  const height = 130;
  const points = buckets.map((b, i) => ({
    x: (i / (buckets.length - 1)) * width,
    y: height - (b.averageWatts / max) * (height - 10),
  }));
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
  const area = `${line} L${width} ${height} L0 ${height} Z`;

  return (
    <div className="chart-wrap">
      <div className="chart-y">
        <span>{Math.round(max)} W</span>
        <span>{Math.round(max / 2)} W</span>
        <span>0 W</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="line-chart">
        <defs>
          <linearGradient id="area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="var(--green)" stopOpacity=".22" />
            <stop offset="1" stopColor="var(--green)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[20, 52, 84, 116].map((y) => (
          <line key={y} x1="0" y1={y} x2={width} y2={y} stroke="var(--line)" strokeWidth="1" />
        ))}
        <path d={area} fill="url(#area)" />
        <path d={line} fill="none" stroke="var(--green)" strokeWidth="3" strokeLinecap="round" />
      </svg>
      <div className="chart-x">
        <span>{new Date(buckets[0].start).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}</span>
        <span>{new Date(buckets[buckets.length - 1].start).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
    </div>
  );
}
