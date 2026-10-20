'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { Cloud, CloudOff, Lightbulb, Plug, Plus, RefreshCw, Router, Wifi, WifiOff } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { DeviceType, Room, TuyaCloudDevice, WifiStatus } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';

/** Categorías de Tuya más comunes: cz/pc/kg = enchufes e interruptores; dj/dd/xdd/fwd = iluminación. */
const LIGHT_CATEGORIES = ['dj', 'dd', 'xdd', 'fwd', 'dc', 'tgq', 'tgkg'];
const categoryLabel = (category: string) =>
  LIGHT_CATEGORIES.includes(category) ? 'Iluminación'
    : ['cz', 'pc'].includes(category) ? 'Enchufe'
      : category === 'kg' ? 'Interruptor'
        : 'Otro';
const suggestedType = (category: string): DeviceType => (LIGHT_CATEGORIES.includes(category) ? 'LIGHT' : 'SMART_OUTLET');

interface Draft {
  type: DeviceType;
  roomId: string;
  critical: boolean;
}

export default function WifiPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const status = useAsync<WifiStatus>(() => api.wifiStatus(), []);
  const rooms = useAsync<Room[]>(() => api.rooms(), []);
  const tuyaReady = !!status.data?.tuyaConfigured && isAdmin;
  const cloud = useAsync<TuyaCloudDevice[]>(
    async () => (tuyaReady ? api.tuyaDevices() : []),
    [tuyaReady],
  );

  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);

  const devices = useMemo(() => cloud.data ?? [], [cloud.data]);
  const pending = useMemo(() => devices.filter((d) => !d.importedAs), [devices]);
  const added = useMemo(() => devices.filter((d) => d.importedAs), [devices]);

  const draftFor = (d: TuyaCloudDevice): Draft =>
    drafts[d.id] ?? { type: suggestedType(d.category), roomId: '', critical: false };
  const setDraft = (d: TuyaCloudDevice, patch: Partial<Draft>) =>
    setDrafts((all) => ({ ...all, [d.id]: { ...draftFor(d), ...patch } }));

  const run = async (key: string, action: () => Promise<unknown>, ok: string) => {
    setBusy(key);
    setMessage(null);
    try {
      await action();
      setMessage({ text: ok, ok: true });
      await cloud.reload();
    } catch (e) {
      setMessage({ text: e instanceof ApiError ? e.message : 'Error inesperado', ok: false });
    } finally {
      setBusy(null);
    }
  };

  const importDevice = (d: TuyaCloudDevice) => {
    const draft = draftFor(d);
    run(d.id, () => api.importTuyaDevice({
      externalId: d.id,
      name: d.name,
      type: draft.type,
      roomId: draft.roomId || undefined,
      critical: draft.critical,
    }), `${d.name} se agregó a la casa`);
  };

  return (
    <div className="page-content">
      <SectionTitle
        title="Dispositivos por WiFi"
        subtitle="Enchufes, interruptores y bombillos Tuya / Smart Life, y dispositivos propios por MQTT"
      />

      {message && (
        <div role="status">
          <Card style={{ marginBottom: 16, color: message.ok ? 'var(--green-dark)' : 'var(--red)' }}>{message.text}</Card>
        </div>
      )}
      {status.error && <Card style={{ marginBottom: 16, color: 'var(--red)' }}>{status.error}</Card>}

      <div className="wifi-status">
        <Card className="wifi-status-card">
          <div className={`kpi-icon ${status.data?.tuyaConfigured ? 'green' : 'gray'}`}>
            {status.data?.tuyaConfigured ? <Cloud size={20} /> : <CloudOff size={20} />}
          </div>
          <div>
            <strong>Tuya / Smart Life</strong>
            <span>
              {!status.data ? 'Consultando…'
                : status.data.tuyaConfigured ? `Conectado a la nube de Tuya · región ${status.data.tuyaRegion}`
                  : 'Sin configurar'}
            </span>
          </div>
          <Badge tone={status.data?.tuyaConfigured ? 'green' : 'gray'}>
            {status.data?.tuyaConfigured ? 'Listo' : 'Pendiente'}
          </Badge>
        </Card>
        <Card className="wifi-status-card">
          <div className={`kpi-icon ${status.data?.mqttConnected ? 'green' : 'gray'}`}>
            <Router size={20} />
          </div>
          <div>
            <strong>Dispositivos propios (MQTT)</strong>
            <span>
              {!status.data ? 'Consultando…'
                : !status.data.mqttEnabled ? 'Desactivado (MQTT_ENABLED=false)'
                  : status.data.mqttConnected ? 'Broker conectado' : 'Broker sin conexión'}
            </span>
          </div>
          <Badge tone={status.data?.mqttConnected ? 'green' : 'gray'}>
            {status.data?.mqttConnected ? 'Conectado' : 'Inactivo'}
          </Badge>
        </Card>
      </div>

      {status.data && !status.data.tuyaConfigured && (
        <Card style={{ marginTop: 16 }}>
          <SectionTitle title="Conectar tu cuenta Smart Life" subtitle="Se hace una sola vez. Los dispositivos siguen en tu WiFi de siempre." />
          {isAdmin ? (
            <ol className="wifi-steps">
              <li>
                Crea una cuenta en <strong>platform.tuya.com</strong> y entra a <strong>Cloud → Development → Create Cloud Project</strong>.
                Elige <em>Smart Home</em> y el centro de datos <strong>Western America</strong> (el de Colombia).
              </li>
              <li>Autoriza el servicio <strong>IoT Core</strong> cuando lo pida el asistente.</li>
              <li>
                En el proyecto, ve a <strong>Devices → Link App Account → Add App Account</strong> y escanea el código QR
                con la app <strong>Smart Life</strong> (Yo → ícono de escanear).
              </li>
              <li>
                Copia el <strong>Access ID</strong> y el <strong>Access Secret</strong> de la pestaña <strong>Overview</strong> y agrégalos
                al <code>.env</code> del backend:
                <pre className="wifi-code">{'TUYA_ACCESS_ID=tu_access_id\nTUYA_ACCESS_SECRET=tu_access_secret\nTUYA_REGION=us'}</pre>
              </li>
              <li>Reinicia el backend y vuelve a esta página: aparecerán tus dispositivos.</li>
            </ol>
          ) : (
            <p className="muted">Pídele al administrador de la casa que conecte la cuenta de Smart Life.</p>
          )}
        </Card>
      )}

      {tuyaReady && (
        <Card style={{ marginTop: 16 }}>
          <SectionTitle
            title="Dispositivos de tu cuenta Smart Life"
            subtitle="Agrega a la casa los que quieres que HomeIA controle y vigile"
            action={
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <Button variant="outline" size="sm" disabled={busy !== null} onClick={() => cloud.reload()}>
                  <RefreshCw size={14} /> Buscar de nuevo
                </Button>
                <Button variant="outline" size="sm" disabled={busy !== null || added.length === 0}
                  onClick={() => run('sync', () => api.syncTuya(), 'Estado actualizado desde Tuya')}>
                  <Wifi size={14} /> Leer estado ahora
                </Button>
              </div>
            }
          />

          {cloud.loading && !cloud.data && <div className="muted">Buscando dispositivos en Tuya…</div>}
          {cloud.error && <div style={{ color: 'var(--red)' }}>{cloud.error}</div>}
          {cloud.data && devices.length === 0 && (
            <p className="muted">
              No hay dispositivos en el proyecto. Revisa que la cuenta Smart Life esté vinculada (Devices → Link App Account)
              y que el centro de datos del proyecto sea el mismo de tu cuenta.
            </p>
          )}

          <div className="wifi-list">
            {pending.map((d) => {
              const draft = draftFor(d);
              const isLight = draft.type === 'LIGHT';
              return (
                <div className="wifi-row" key={d.id}>
                  <div className={`device-icon ${d.online ? 'on' : ''}`}>{isLight ? <Lightbulb size={18} /> : <Plug size={18} />}</div>
                  <div className="wifi-row-name">
                    <strong>{d.name}</strong>
                    <span>{d.productName || 'Dispositivo Tuya'} · {categoryLabel(d.category)}</span>
                  </div>
                  <Badge tone={d.online ? 'green' : 'gray'}>
                    {d.online ? <Wifi size={12} /> : <WifiOff size={12} />} {d.online ? 'En línea' : 'Sin conexión'}
                  </Badge>
                  <div className="wifi-row-form">
                    <select aria-label={`Tipo de ${d.name}`} className="btn btn-outline btn-md" value={draft.type}
                      onChange={(e) => setDraft(d, { type: e.target.value as DeviceType })}>
                      <option value="SMART_OUTLET">Tomacorriente</option>
                      <option value="LIGHT">Luz</option>
                    </select>
                    <select aria-label={`Habitación de ${d.name}`} className="btn btn-outline btn-md" value={draft.roomId}
                      onChange={(e) => setDraft(d, { roomId: e.target.value })}>
                      <option value="">Sin habitación</option>
                      {(rooms.data ?? []).map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                    </select>
                    <label className="badge badge-gray wifi-critical">
                      <input type="checkbox" checked={draft.critical} onChange={(e) => setDraft(d, { critical: e.target.checked })} /> Crítico
                    </label>
                    <Button size="md" disabled={busy !== null} onClick={() => importDevice(d)}>
                      <Plus size={15} /> {busy === d.id ? 'Agregando…' : 'Agregar'}
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>

          {added.length > 0 && (
            <>
              <div className="mini-label" style={{ margin: '18px 0 8px' }}>YA ESTÁN EN LA CASA</div>
              <div className="wifi-list">
                {added.map((d) => (
                  <div className="wifi-row" key={d.id}>
                    <div className={`device-icon ${d.online ? 'on' : ''}`}>
                      {LIGHT_CATEGORIES.includes(d.category) ? <Lightbulb size={18} /> : <Plug size={18} />}
                    </div>
                    <div className="wifi-row-name">
                      <strong>{d.name}</strong>
                      <span>{d.productName || 'Dispositivo Tuya'} · {categoryLabel(d.category)}</span>
                    </div>
                    <Badge tone={d.online ? 'green' : 'gray'}>{d.online ? 'En línea' : 'Sin conexión'}</Badge>
                    <Link href="/dashboard/devices" className="btn btn-ghost btn-sm">Controlar en Dispositivos</Link>
                  </div>
                ))}
              </div>
            </>
          )}
          {pending.length > 0 && (
            <p className="muted" style={{ marginTop: 12 }}>
              Marca como <strong>crítico</strong> lo que nunca debe apagarse solo (nevera, router, equipos médicos).
            </p>
          )}
        </Card>
      )}
    </div>
  );
}
