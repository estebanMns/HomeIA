'use client';

import { FormEvent, useState } from 'react';
import { Check, Plus, RefreshCw } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { Alert, AlertSeverity, Page } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';
import AlertCard, { alertTypeLabel, formatDateTime } from '@/components/ui/AlertCard';

export default function AlertsPage() {
  const { user } = useAuth();
  const [filter, setFilter] = useState<'open' | 'all'>('open');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState({ severity: 'INFO' as AlertSeverity, title: '', message: '' });

  const { data, error, loading, reload } = useAsync<Page<Alert>>(
    () => api.alerts({ acknowledged: filter === 'open' ? false : undefined, size: 50 }),
    [filter],
    30000,
  );

  const alerts = data?.content ?? [];
  const selected = alerts.find((a) => a.id === selectedId) ?? alerts[0] ?? null;

  const run = async (action: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    setMessage('');
    try {
      await action();
      setMessage(ok);
      await reload();
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : 'Error inesperado');
    } finally {
      setBusy(false);
    }
  };

  const create = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      await api.createAlert(draft);
      setDraft({ severity: 'INFO', title: '', message: '' });
    }, 'Alerta creada');
  };

  return (
    <div className="page-content">
      <SectionTitle
        title={`${data?.totalElements ?? 0} alertas`}
        subtitle="Anomalías y avisos de la casa. Las de severidad advertencia o crítica se envían por WhatsApp."
        action={
          <div className="quick-actions">
            <Button variant={filter === 'open' ? 'primary' : 'outline'} size="sm" onClick={() => setFilter('open')}>Abiertas</Button>
            <Button variant={filter === 'all' ? 'primary' : 'outline'} size="sm" onClick={() => setFilter('all')}>Todas</Button>
          </div>
        }
      />

      {(error || message) && <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>}

      <Card style={{ marginBottom: 16 }}>
        <form onSubmit={create} className="filter-row" style={{ marginBottom: 0, flexWrap: 'wrap' }}>
          <select value={draft.severity} onChange={(e) => setDraft({ ...draft, severity: e.target.value as AlertSeverity })} className="btn btn-outline btn-md">
            <option value="INFO">Info</option>
            <option value="WARNING">Advertencia</option>
            <option value="CRITICAL">Crítica</option>
          </select>
          <div className="search grow"><input placeholder="Título" value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} required /></div>
          <div className="search grow"><input placeholder="Mensaje" value={draft.message} onChange={(e) => setDraft({ ...draft, message: e.target.value })} required /></div>
          <button type="submit" className="btn btn-primary btn-md" disabled={busy}><Plus size={16} /> Crear alerta</button>
        </form>
      </Card>

      {loading && !data ? (
        <div className="muted">Cargando…</div>
      ) : alerts.length === 0 ? (
        <Card className="muted">No hay alertas {filter === 'open' ? 'abiertas' : ''}.</Card>
      ) : (
        <div className="alerts-layout">
          <div className="alert-selector">
            {alerts.map((a) => (
              <button key={a.id} onClick={() => setSelectedId(a.id)} className={selected?.id === a.id ? 'selected' : ''}>
                <AlertCard alert={a} />
              </button>
            ))}
          </div>

          {selected && (
            <Card className="alert-detail">
              <div className="detail-alert-top">
                <div className={`alert-icon ${selected.severity === 'CRITICAL' ? 'red' : ''}`} />
                <div>
                  <strong>{selected.title}</strong>
                  <span>{alertTypeLabel[selected.type]}</span>
                </div>
                <Badge tone={selected.severity === 'CRITICAL' ? 'red' : selected.severity === 'WARNING' ? 'amber' : 'blue'}>
                  {selected.severity}
                </Badge>
              </div>
              <p>{selected.message}</p>
              <div className="cost-row">
                <span>Creada</span>
                <strong>{formatDateTime(selected.createdAt)}</strong>
              </div>
              {selected.acknowledged && (
                <div className="cost-row">
                  <span>Revisada</span>
                  <strong>{formatDateTime(selected.acknowledgedAt)} {selected.acknowledgedBy ? `· ${selected.acknowledgedBy}` : ''}</strong>
                </div>
              )}
              <div className="detail-actions" style={{ marginTop: 20 }}>
                {!selected.acknowledged && (
                  <Button variant="outline" disabled={busy} onClick={() => run(() => api.acknowledgeAlert(selected.id), 'Alerta marcada como revisada')}>
                    <Check size={16} /> Marcar como revisada
                  </Button>
                )}
                {user?.role === 'ADMIN' && selected.severity !== 'INFO' && (
                  <Button variant="ghost" disabled={busy} onClick={() => run(() => api.retryAlertNotifications(selected.id), 'Notificaciones reenviadas')}>
                    <RefreshCw size={16} /> Reenviar WhatsApp
                  </Button>
                )}
              </div>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
