'use client';

import { FormEvent, useEffect, useState } from 'react';
import { Bot, Play } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { AutomationAction, AutomationSettings, AutomationStatus, Device, Room } from '@/types';
import { Badge, Button, Card, SectionTitle, Switch } from '@/components/ui/Primitives';
import { formatDateTime } from '@/components/ui/AlertCard';

interface Data {
  settings: AutomationSettings;
  status: AutomationStatus;
  actions: AutomationAction[];
  rooms: Room[];
  devices: Device[];
}

export default function AutomationPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data, error, loading, reload } = useAsync<Data>(async () => {
    const [settings, status, actions, rooms, devices] = await Promise.all([
      api.automationSettings(),
      api.automationStatus(),
      api.automationActions(0, 30),
      api.rooms(),
      api.devices(),
    ]);
    return { settings, status, actions: actions.content, rooms, devices };
  }, [], 30000);

  const [form, setForm] = useState<Omit<AutomationSettings, 'updatedAt'> | null>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [probabilities, setProbabilities] = useState<Record<string, number>>({});

  useEffect(() => {
    if (data) {
      const { updatedAt: _ignored, ...rest } = data.settings;
      setForm(rest);
    }
  }, [data]);

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

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!form) return;
    run(() => api.updateAutomationSettings(form), 'Configuración guardada');
  };

  const loadProbabilities = async () => {
    setBusy(true);
    try {
      const entries = await Promise.all(
        (data?.rooms ?? []).map(async (r) => [r.id, (await api.roomReturnProbability(r.id)).probabilityPercent] as const),
      );
      setProbabilities(Object.fromEntries(entries));
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : 'No se pudo calcular la probabilidad');
    } finally {
      setBusy(false);
    }
  };

  const roomName = new Map((data?.rooms ?? []).map((r) => [r.id, r.name]));
  const enabled = data?.settings.enabled ?? false;

  return (
    <div className="page-content narrow">
      {(error || message) && <Card style={{ marginBottom: 14, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>}

      {data && (
        <>
          <Card className="automation-toggle">
            <div className="ai-orb"><Bot size={22} /></div>
            <div>
              <strong>Automatización inteligente</strong>
              <span>Apaga luces y tomacorrientes no críticos cuando una habitación lleva tiempo vacía.</span>
            </div>
            <Badge tone={enabled ? 'green' : 'gray'}>{enabled ? 'Activa' : 'Inactiva'}</Badge>
            {isAdmin && (
              <Switch
                checked={enabled}
                disabled={busy}
                onChange={() => run(() => api.setAutomationEnabled(!enabled), enabled ? 'Automatización desactivada' : 'Automatización activada')}
              />
            )}
          </Card>

          <div className="kpi-grid">
            <Card className="kpi-card"><div className="kpi-title">Decisiones (24 h)</div><div className="kpi-value">{data.status.decisionsLast24h}</div></Card>
            <Card className="kpi-card"><div className="kpi-title">Acciones totales</div><div className="kpi-value">{data.status.actionsTotal}</div></Card>
            <Card className="kpi-card"><div className="kpi-title">Predicción</div><div className="kpi-value" style={{ fontSize: 18 }}>{data.status.predictionMode === 'GEMINI' ? 'Gemini' : 'Historial'}</div></Card>
            <Card className="kpi-card"><div className="kpi-title">Última decisión</div><div className="kpi-value" style={{ fontSize: 16 }}>{data.status.lastDecisionAt ? formatDateTime(data.status.lastDecisionAt) : '—'}</div></Card>
          </div>

          {isAdmin && form && (
            <Card style={{ marginBottom: 14 }}>
              <SectionTitle title="Reglas" subtitle="Define cuándo debe actuar la automatización" />
              <form onSubmit={save}>
                <div className="settings-grid">
                  <label className="field"><span>Minutos sin presencia antes de apagar</span><div><input type="number" min={1} max={1440} value={form.vacancyMinutes} onChange={(e) => setForm({ ...form, vacancyMinutes: Number(e.target.value) })} /></div></label>
                  <label className="field"><span>Sensibilidad de regreso (%)</span><div><input type="number" min={1} max={100} value={form.returnSensitivityPercent} onChange={(e) => setForm({ ...form, returnSensitivityPercent: Number(e.target.value) })} /></div></label>
                  <label className="field"><span>Umbral de consumo excesivo (W)</span><div><input type="number" min={1} value={form.consumptionAlertWatts} onChange={(e) => setForm({ ...form, consumptionAlertWatts: Number(e.target.value) })} /></div></label>
                  <label className="field"><span>Minutos sostenidos sobre el umbral</span><div><input type="number" min={1} max={240} value={form.consumptionSustainedMinutes} onChange={(e) => setForm({ ...form, consumptionSustainedMinutes: Number(e.target.value) })} /></div></label>
                  <label className="field"><span>Minutos de habitación vacía para revisar fugas</span><div><input type="number" min={1} max={1440} value={form.leakMinutes} onChange={(e) => setForm({ ...form, leakMinutes: Number(e.target.value) })} /></div></label>
                  <label className="field"><span>Minutos entre alertas repetidas</span><div><input type="number" min={1} max={1440} value={form.alertCooldownMinutes} onChange={(e) => setForm({ ...form, alertCooldownMinutes: Number(e.target.value) })} /></div></label>
                </div>
                <p className="muted" style={{ margin: '4px 0 12px' }}>
                  Una posible fuga se detecta cuando, con la habitación vacía, un dispositivo consume mucho más de lo habitual a esa hora.
                </p>
                <button type="submit" className="btn btn-primary btn-md" disabled={busy}>Guardar reglas</button>
              </form>
            </Card>
          )}

          <Card style={{ marginBottom: 14 }}>
            <SectionTitle
              title="Probabilidad de regreso por habitación"
              subtitle="Si supera la sensibilidad, la IA no apaga la habitación"
              action={<Button variant="outline" size="sm" disabled={busy || !data.rooms.length} onClick={loadProbabilities}>Calcular</Button>}
            />
            {data.rooms.length === 0 && <div className="muted">No hay habitaciones.</div>}
            {data.rooms.map((r) => (
              <div key={r.id} className="setting-list" style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
                <span>{r.name}</span>
                <strong>{probabilities[r.id] !== undefined ? `${probabilities[r.id]}%` : '—'}</strong>
              </div>
            ))}
          </Card>

          <Card>
            <SectionTitle
              title="Acciones recientes"
              subtitle="Cada decisión queda registrada"
              action={isAdmin ? (
                <Button variant="outline" size="sm" disabled={busy} onClick={() => run(async () => { await api.runAutomation(); }, 'Ciclo ejecutado')}>
                  <Play size={14} /> Ejecutar ahora
                </Button>
              ) : undefined}
            />
            {data.actions.length === 0 && <div className="muted">Todavía no hay acciones.</div>}
            {data.actions.map((a) => (
              <div key={a.id} className="protected-row">
                <div>
                  <strong>{a.description}</strong>
                  <span>{a.roomId ? roomName.get(a.roomId) ?? '' : ''} · {formatDateTime(a.createdAt)}</span>
                </div>
                <Badge tone={a.actionType === 'SWITCHED_OFF_VACANT_ROOM' ? 'blue' : 'green'}>{a.actionType.replaceAll('_', ' ').toLowerCase()}</Badge>
              </div>
            ))}
          </Card>
        </>
      )}
      {loading && !data && <div className="muted">Cargando…</div>}
    </div>
  );
}
