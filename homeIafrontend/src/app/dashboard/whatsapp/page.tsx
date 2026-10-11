'use client';

import { FormEvent, useState } from 'react';
import { Check, Plus, Send, Trash2, RefreshCw } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { Page, WhatsAppMessage, WhatsAppRecipient, WhatsAppStatus } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';
import { formatDateTime } from '@/components/ui/AlertCard';

interface Data {
  status: WhatsAppStatus;
  recipients: WhatsAppRecipient[];
  messages: Page<WhatsAppMessage>;
}

const statusTone: Record<WhatsAppMessage['status'], 'green' | 'amber' | 'red'> = {
  SENT: 'green',
  PENDING: 'amber',
  FAILED: 'red',
};

export default function WhatsAppPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data, error, loading, reload } = useAsync<Data>(async () => {
    const [status, recipients, messages] = await Promise.all([
      api.whatsappStatus(),
      api.whatsappRecipients(),
      api.whatsappMessages(0, 30),
    ]);
    return { status, recipients, messages };
  }, [], 30000);

  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

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

  const addRecipient = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      await api.addWhatsappRecipient({ name, phone });
      setName('');
      setPhone('');
    }, 'Destinatario agregado');
  };

  return (
    <div className="page-content">
      <SectionTitle
        title="Notificaciones de WhatsApp"
        subtitle="Las alertas críticas y de advertencia se envían a los destinatarios activos"
      />

      {(error || message) && <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>}

      {data && !data.status.configured && (
        <Card style={{ marginBottom: 16, borderColor: 'var(--amber)' }}>
          <strong>WhatsApp no está configurado.</strong>
          <div className="muted">
            Define WHATSAPP_ACCESS_TOKEN y WHATSAPP_PHONE_NUMBER_ID en el archivo .env del backend. Mientras tanto,
            las alertas quedan registradas como mensajes fallidos y se pueden reenviar.
          </div>
        </Card>
      )}

      {data && (
        <div className="kpi-grid">
          <Card className="kpi-card"><div className="kpi-title">Destinatarios activos</div><div className="kpi-value">{data.status.activeRecipients}</div></Card>
          <Card className="kpi-card"><div className="kpi-title">Pendientes</div><div className="kpi-value">{data.status.pendingMessages}</div></Card>
          <Card className="kpi-card"><div className="kpi-title">Fallidos</div><div className="kpi-value">{data.status.failedMessages}</div></Card>
          <Card className="kpi-card">
            <div className="kpi-title">Servicio</div>
            <div className="kpi-value" style={{ fontSize: 18 }}>{data.status.configured ? 'Configurado' : 'Sin configurar'}</div>
          </Card>
        </div>
      )}

      <div className="dashboard-grid">
        <Card>
          <SectionTitle
            title="Destinatarios"
            subtitle="Número en formato internacional, p. ej. +573001234567"
            action={isAdmin && data ? (
              <Button variant="outline" size="sm" disabled={busy || !data.recipients.some((r) => r.active)} onClick={() => run(() => api.sendWhatsappTest(), 'Mensaje de prueba enviado')}>
                <Send size={14} /> Probar
              </Button>
            ) : undefined}
          />
          {isAdmin && (
            <form onSubmit={addRecipient} className="filter-row" style={{ flexWrap: 'wrap' }}>
              <div className="search grow"><input placeholder="Nombre" value={name} onChange={(e) => setName(e.target.value)} required /></div>
              <div className="search grow"><input placeholder="+573001234567" value={phone} onChange={(e) => setPhone(e.target.value)} required /></div>
              <button type="submit" className="btn btn-primary btn-md" disabled={busy}><Plus size={16} /> Agregar</button>
            </form>
          )}
          {data?.recipients.length === 0 && <div className="muted">No hay destinatarios.</div>}
          {data?.recipients.map((r) => (
            <div key={r.id} className="device-row">
              <div className="device-copy">
                <strong>{r.name}</strong>
                <span>{r.phone}</span>
              </div>
              {isAdmin ? (
                <>
                  <Button variant="outline" size="sm" disabled={busy} onClick={() => run(() => api.updateWhatsappRecipient(r.id, { active: !r.active }), 'Destinatario actualizado')}>
                    {r.active ? <><Check size={14} /> Activo</> : 'Inactivo'}
                  </Button>
                  <Button variant="ghost" size="icon" disabled={busy} onClick={() => {
                    if (window.confirm(`¿Eliminar a ${r.name}?`)) run(() => api.deleteWhatsappRecipient(r.id), 'Destinatario eliminado');
                  }}>
                    <Trash2 size={16} />
                  </Button>
                </>
              ) : (
                <Badge tone={r.active ? 'green' : 'gray'}>{r.active ? 'Activo' : 'Inactivo'}</Badge>
              )}
            </div>
          ))}
        </Card>

        <Card>
          <SectionTitle title="Mensajes" subtitle="Historial de envíos" />
          {data?.messages.content.length === 0 && <div className="muted">Aún no se han enviado mensajes.</div>}
          {data?.messages.content.map((m) => (
            <div key={m.id} className="device-row" style={{ alignItems: 'flex-start', padding: '10px 0' }}>
              <div className="device-copy">
                <strong>{m.recipientPhone}</strong>
                <span>{m.body}</span>
                {m.lastError && <span style={{ color: 'var(--red)' }}>{m.lastError}</span>}
                <span>{formatDateTime(m.createdAt)} · intentos {m.attempts}</span>
              </div>
              <Badge tone={statusTone[m.status]}>{m.status}</Badge>
              {isAdmin && m.status === 'FAILED' && (
                <Button variant="ghost" size="icon" disabled={busy} onClick={() => run(() => api.retryWhatsappMessage(m.id), 'Mensaje reenviado')}>
                  <RefreshCw size={16} />
                </Button>
              )}
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}
