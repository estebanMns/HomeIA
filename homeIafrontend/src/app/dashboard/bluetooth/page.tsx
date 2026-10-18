'use client';

import { FormEvent, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { BluetoothConnected, Power, PowerOff, Search, Unplug } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import {
  BleSession,
  connectDevice,
  discoverDevice,
  disconnectDevice,
  forgetDevice,
  getBleSessions,
  isBluetoothSupported,
  sendBleCommand,
  subscribeBle,
} from '@/lib/bluetooth';
import type { Device, DeviceType, Room } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';

const typeLabel: Record<DeviceType, string> = {
  LIGHT: 'Luz',
  SMART_OUTLET: 'Tomacorriente',
  PRESENCE_SENSOR: 'Sensor de presencia',
};

export default function BluetoothPage() {
  const { user } = useAuth();
  const supported = isBluetoothSupported();
  const sessions = useSyncExternalStore(subscribeBle, getBleSessions, getBleSessions);
  const { data, error, reload } = useAsync<{ devices: Device[]; rooms: Room[] }>(async () => {
    const [devices, rooms] = await Promise.all([api.devices(), api.rooms()]);
    return { devices, rooms };
  }, [], 30000);

  const [acceptAll, setAcceptAll] = useState(false);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState('');

  const search = async () => {
    setMessage('');
    setSearching(true);
    try {
      const device = await discoverDevice(acceptAll);
      await connectDevice(device);
      setMessage(`Conectado a ${device.name ?? 'el dispositivo'}`);
    } catch (e) {
      // El usuario cerró el selector: no es un error.
      if (e instanceof DOMException && e.name === 'NotFoundError') {
        setMessage('Búsqueda cancelada');
      } else {
        setMessage(e instanceof Error ? e.message : 'No se pudo conectar');
      }
    } finally {
      setSearching(false);
    }
  };

  const registeredFor = (bluetoothId: string) => data?.devices.find((d) => d.bluetoothId === bluetoothId);

  // Vincular dispositivos y reportar su estado alimenta las decisiones de la IA: solo el administrador.
  if (user?.role !== 'ADMIN') {
    return (
      <div className="page-content">
        <SectionTitle title="Dispositivos Bluetooth" subtitle="Vinculación de dispositivos" />
        <Card className="muted">Solo el administrador de la casa puede vincular dispositivos por Bluetooth.</Card>
      </div>
    );
  }

  return (
    <div className="page-content">
      <SectionTitle
        title="Dispositivos Bluetooth"
        subtitle="Busca dispositivos cercanos, conéctate y controla su estado desde la app"
      />

      {!supported && (
        <Card style={{ marginBottom: 16, borderColor: 'var(--amber)' }}>
          <strong>Este navegador no soporta Bluetooth.</strong>
          <div className="muted">
            Usa Chrome o Edge en un equipo o Android con HTTPS (o localhost). Safari y Firefox no implementan Web Bluetooth.
          </div>
        </Card>
      )}

      {(error || message) && (
        <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>
      )}

      <Card style={{ marginBottom: 16 }}>
        <div className="filter-row" style={{ marginBottom: 0, flexWrap: 'wrap' }}>
          <Button variant="primary" disabled={!supported || searching} onClick={search}>
            <Search size={16} /> {searching ? 'Buscando…' : 'Buscar dispositivos'}
          </Button>
          <label className="muted" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <input type="checkbox" checked={acceptAll} onChange={(e) => setAcceptAll(e.target.checked)} />
            Mostrar todos los dispositivos BLE (no solo HomeIA)
          </label>
        </div>
        <div className="muted" style={{ marginTop: 10 }}>
          El navegador muestra el selector de dispositivos cercanos; solo puedes conectarte a los que elijas.
          Los equipos HomeIA anuncian el servicio y un nombre que empieza por “HomeIA”.
        </div>
      </Card>

      <SectionTitle title={`Conectados en esta sesión (${sessions.length})`} />
      {sessions.length === 0 && <Card className="muted">No hay dispositivos conectados. Pulsa “Buscar dispositivos”.</Card>}

      <div className="rooms-grid">
        {sessions.map((session) => (
          <BleCard
            key={session.bluetoothId}
            session={session}
            registered={registeredFor(session.bluetoothId)}
            rooms={data?.rooms ?? []}
            onRegistered={reload}
            onMessage={setMessage}
          />
        ))}
      </div>
    </div>
  );
}

function BleCard({
  session,
  registered,
  rooms,
  onRegistered,
  onMessage,
}: {
  session: BleSession;
  registered?: Device;
  rooms: Room[];
  onRegistered: () => Promise<void>;
  onMessage: (m: string) => void;
}) {
  const [form, setForm] = useState({ name: session.name, type: 'LIGHT' as DeviceType, roomId: '', critical: false });
  const [busy, setBusy] = useState(false);
  const connected = session.status === 'connected';
  const on = session.state.powerState === 'ON';

  const command = async (cmd: 'ON' | 'OFF' | 'STATUS') => {
    setBusy(true);
    try {
      await sendBleCommand(session.bluetoothId, cmd);
    } catch (e) {
      onMessage(e instanceof Error ? e.message : 'No se pudo enviar el comando');
    } finally {
      setBusy(false);
    }
  };

  const register = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.registerBluetoothDevice({
        bluetoothId: session.bluetoothId,
        name: form.name,
        type: form.type,
        roomId: form.roomId || undefined,
        critical: form.critical,
      });
      onMessage(`${form.name} registrado en HomeIA`);
      await onRegistered();
    } catch (err) {
      onMessage(err instanceof ApiError ? err.message : 'No se pudo registrar el dispositivo');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      {registered && <StateReporter session={session} deviceId={registered.id} />}
      <div className="room-top">
        <div className="room-icon"><BluetoothConnected size={18} /></div>
        <Badge tone={connected ? 'green' : session.status === 'error' ? 'red' : 'gray'}>
          {statusLabel(session.status)}
        </Badge>
      </div>
      <div className="room-name">{session.name}</div>
      <div className="muted">{registered ? `Registrado: ${registered.name}` : 'Sin registrar en HomeIA'}</div>

      {session.error && <div className="muted" style={{ color: 'var(--red)', marginTop: 8 }}>{session.error}</div>}

      {connected && (
        <div className="room-stats" style={{ marginTop: 12 }}>
          <span>{on ? 'Encendido' : session.state.powerState === 'OFF' ? 'Apagado' : 'Estado desconocido'}</span>
          <strong>{session.state.consumptionWatts !== undefined ? `${session.state.consumptionWatts} W` : '—'}</strong>
        </div>
      )}

      {connected && (
        <div className="quick-actions" style={{ marginTop: 12 }}>
          <Button variant="outline" size="sm" disabled={busy} onClick={() => command('ON')}><Power size={14} /> Encender</Button>
          <Button variant="outline" size="sm" disabled={busy} onClick={() => command('OFF')}><PowerOff size={14} /> Apagar</Button>
          <Button variant="ghost" size="sm" onClick={() => disconnectDevice(session.bluetoothId)}><Unplug size={14} /> Desconectar</Button>
        </div>
      )}

      {!connected && session.status !== 'connecting' && (
        <div style={{ marginTop: 10 }}>
          <Button variant="ghost" size="sm" onClick={() => forgetDevice(session.bluetoothId)}>Quitar de la lista</Button>
        </div>
      )}

      {connected && !registered && (
        <form onSubmit={register} style={{ marginTop: 16 }}>
          <div className="section-heading">Registrar en HomeIA</div>
          <label className="field"><span>Nombre</span><div><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></div></label>
          <div className="settings-grid">
            <label className="field">
              <span>Tipo</span>
              <div>
                <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as DeviceType })} style={{ width: '100%', border: 0, background: 'transparent', color: 'var(--text)' }}>
                  {(Object.keys(typeLabel) as DeviceType[]).map((t) => <option key={t} value={t}>{typeLabel[t]}</option>)}
                </select>
              </div>
            </label>
            <label className="field">
              <span>Habitación</span>
              <div>
                <select value={form.roomId} onChange={(e) => setForm({ ...form, roomId: e.target.value })} style={{ width: '100%', border: 0, background: 'transparent', color: 'var(--text)' }}>
                  <option value="">Sin habitación</option>
                  {rooms.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
              </div>
            </label>
          </div>
          <label className="muted" style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 10 }}>
            <input type="checkbox" checked={form.critical} onChange={(e) => setForm({ ...form, critical: e.target.checked })} /> Crítico
          </label>
          <button type="submit" className="btn btn-primary btn-md" disabled={busy}>Registrar</button>
        </form>
      )}
    </Card>
  );
}

/**
 * Envía el estado del dispositivo al backend cada vez que llega una notificación nueva,
 * con un máximo de un envío cada 3 segundos para no saturar la API.
 */
function StateReporter({ session, deviceId }: { session: BleSession; deviceId: string }) {
  const lastSent = useRef(0);

  useEffect(() => {
    if (session.lastUpdate === undefined || session.status !== 'connected') return;
    const { powerState, consumptionWatts, brightness, presence } = session.state;
    if (powerState === undefined && consumptionWatts === undefined) return;
    const send = () => {
      lastSent.current = Date.now();
      api.reportDeviceState(deviceId, { powerState, consumptionWatts, brightness, presence }).catch(() => undefined);
    };
    // Si llega muy seguido, se envía el último estado al terminar la ventana (no se descarta).
    const wait = 3000 - (Date.now() - lastSent.current);
    if (wait <= 0) {
      send();
      return;
    }
    const timer = setTimeout(send, wait);
    return () => clearTimeout(timer);
  }, [session.lastUpdate, session.status, session.state, deviceId]);

  return null;
}

function statusLabel(status: BleSession['status']) {
  switch (status) {
    case 'connected': return 'Conectado';
    case 'connecting': return 'Conectando…';
    case 'error': return 'Error';
    default: return 'Desconectado';
  }
}
