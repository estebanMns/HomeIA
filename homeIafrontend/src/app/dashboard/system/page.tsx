'use client';

import { CheckCircle2, CircleAlert } from 'lucide-react';
import { api } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import type { SystemHealth, SystemMetrics } from '@/types';
import { Card, SectionTitle } from '@/components/ui/Primitives';
import { formatDateTime } from '@/components/ui/AlertCard';

interface Data {
  health: SystemHealth;
  metrics: SystemMetrics;
}

const componentLabels: Record<string, string> = {
  database: 'Base de datos',
  mqtt: 'Broker MQTT (dispositivos)',
  whatsapp: 'WhatsApp Cloud API',
  gemini: 'Gemini (predicción)',
};

function isOk(value: string) {
  return value === 'UP' || value === 'CONNECTED' || value === 'CONFIGURED';
}

export default function SystemPage() {
  const { data, error, loading } = useAsync<Data>(async () => {
    const [health, metrics] = await Promise.all([api.systemHealth(), api.systemMetrics()]);
    return { health, metrics };
  }, [], 30000);

  return (
    <div className="page-content">
      <SectionTitle title="Estado del sistema" subtitle="Conexiones con los servicios que usa HomeIA" />

      {error && <Card style={{ color: 'var(--red)', marginBottom: 16 }}>{error}</Card>}
      {loading && !data && <div className="muted">Consultando…</div>}

      {data && (
        <>
          <div className="service-grid">
            {Object.entries(componentLabels).map(([key, label]) => {
              const value = (data.health as unknown as Record<string, string>)[key];
              const ok = isOk(value);
              return (
                <Card key={key} className="service-card">
                  <strong>{label}</strong>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10 }}>
                    {ok ? <CheckCircle2 size={18} color="var(--green)" /> : <CircleAlert size={18} color="var(--amber)" />}
                    <span>{value}</span>
                  </div>
                </Card>
              );
            })}
          </div>

          <div className="kpi-grid" style={{ marginTop: 20 }}>
            <Card className="kpi-card"><div className="kpi-title">Dispositivos</div><div className="kpi-value">{data.metrics.devicesTotal}</div><div className="kpi-note">{data.metrics.devicesOnline} en línea · {data.metrics.devicesOn} encendidos</div></Card>
            <Card className="kpi-card"><div className="kpi-title">Habitaciones</div><div className="kpi-value">{data.metrics.roomsTotal}</div></Card>
            <Card className="kpi-card"><div className="kpi-title">Alertas abiertas</div><div className="kpi-value">{data.metrics.alertsOpen}</div></Card>
            <Card className="kpi-card"><div className="kpi-title">WhatsApp pendientes</div><div className="kpi-value">{data.metrics.whatsappPending}</div></Card>
          </div>

          <div className="muted" style={{ marginTop: 12 }}>
            Última comprobación: {formatDateTime(data.health.checkedAt)}
          </div>
        </>
      )}
    </div>
  );
}
