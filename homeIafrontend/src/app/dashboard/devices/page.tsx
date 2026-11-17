'use client';

import { FormEvent, useDeferredValue, useMemo, useState } from 'react';
import { History, KeyRound, Lightbulb, Pencil, Plug, Plus, Radio, Search, ShieldCheck, Trash2, Wifi, WifiOff } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { Device, DeviceCommandRecord, DeviceEvent, DeviceType, Home, Room } from '@/types';
import { canDim, canSwitch, effectiveCapabilities, providerLabel, telemetrySummary, usesIpAddress } from '@/lib/capabilities';
import LiveUsage from '@/components/devices/LiveUsage';
import RoomField from '@/components/devices/RoomField';
import { resolveRoomId } from '@/lib/rooms';
import { formatDateTime } from '@/components/ui/AlertCard';
import { Badge, Button, Card, SectionTitle, Switch } from '@/components/ui/Primitives';

const typeLabel: Record<DeviceType, string> = {
  LIGHT: 'Luz',
  SMART_OUTLET: 'Tomacorriente',
  PRESENCE_SENSOR: 'Sensor de presencia',
};

interface Data {
  devices: Device[];
  rooms: Room[];
  home: Home | null;
}

interface EditDraft {
  id: string;
  name: string;
  /** Nombre de la habitación: si no existe, se crea al guardar. Vacío = sin habitación. */
  roomName: string;
  critical: boolean;
  /** Vacío = usar el umbral de la casa. */
  alertWatts: string;
  /** Vacío = sin estimar. Solo para dispositivos sin medidor. */
  ratedWatts: string;
  metered: boolean;
  type: DeviceType;
  manufacturer: string;
  model: string;
  /** IPv4 LAN (Shelly, Tasmota, ESPHome, WLED) o friendly_name (Zigbee). */
  address: string;
  provider: Device['provider'];
}

interface CredentialsDraft {
  device: Device;
  configured: boolean;
  username: string;
  password: string;
  encryptionKey: string;
}

interface HistoryView {
  device: Device;
  events: DeviceEvent[];
  commands: DeviceCommandRecord[];
}

export default function DevicesPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data, error, loading, reload } = useAsync<Data>(async () => {
    const [devices, rooms, home] = await Promise.all([api.devices(), api.rooms(), api.home().catch(() => null)]);
    return { devices, rooms, home };
  }, [], 30000);

  const [query, setQuery] = useState('');
  const [roomFilter, setRoomFilter] = useState('');
  const [message, setMessage] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [form, setForm] = useState({ name: '', type: 'LIGHT' as DeviceType, roomName: '', critical: false });
  const [editing, setEditing] = useState<EditDraft | null>(null);
  const [credentials, setCredentials] = useState<CredentialsDraft | null>(null);
  const [history, setHistory] = useState<HistoryView | null>(null);

  const roomById = useMemo(() => new Map((data?.rooms ?? []).map((r) => [r.id, r])), [data]);

  // La búsqueda se filtra con un valor diferido: React atiende primero cada tecla (urgente) y
  // recalcula la tabla después, sin bloquear la escritura.
  const deferredQuery = useDeferredValue(query);
  const filtered = (data?.devices ?? []).filter(
    (d) =>
      d.name.toLowerCase().includes(deferredQuery.toLowerCase()) &&
      (roomFilter === '' || d.roomId === roomFilter),
  );

  const run = async (id: string | null, action: () => Promise<unknown>, ok: string) => {
    setBusyId(id);
    setMessage('');
    try {
      await action();
      setMessage(ok);
      await reload();
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : 'Error inesperado');
    } finally {
      setBusyId(null);
    }
  };

  /** Reglas del backend: solo el admin apaga tomacorrientes y apagar un crítico requiere confirmación. */
  const togglePower = (d: Device) => {
    const turningOff = d.powerState === 'ON';
    if (!turningOff) {
      run(d.id, () => api.deviceCommand(d.id, 'ON'), 'Comando de encendido enviado');
      return;
    }
    if (d.critical && !window.confirm(
      `${d.name} es un dispositivo crítico y está protegido. Si lo apagas podrías interrumpir un servicio esencial de la casa. ¿Apagarlo de todas formas?`,
    )) {
      return;
    }
    run(d.id, () => api.deviceCommand(d.id, 'OFF', undefined, d.critical), 'Comando de apagado enviado');
  };

  const create = (e: FormEvent) => {
    e.preventDefault();
    run(null, async () => {
      await api.createDevice({
        name: form.name,
        type: form.type,
        roomId: await resolveRoomId(form.roomName, data?.rooms ?? []),
        critical: form.critical,
      });
      setForm({ ...form, name: '' });
    }, 'Dispositivo registrado');
  };

  const startEdit = (d: Device) =>
    setEditing({
      id: d.id,
      name: d.name,
      roomName: d.roomId ? roomById.get(d.roomId)?.name ?? '' : '',
      critical: d.critical,
      alertWatts: d.consumptionAlertWatts ? String(d.consumptionAlertWatts) : '',
      ratedWatts: d.ratedWatts ? String(d.ratedWatts) : '',
      metered: effectiveCapabilities(d).has('ENERGY'),
      type: d.type,
      manufacturer: d.manufacturer ?? '',
      model: d.model ?? '',
      address: d.provider === 'ZIGBEE' ? d.externalId ?? '' : d.networkAddress ?? '',
      provider: d.provider,
    });

  const openCredentials = (d: Device) =>
    run(d.id, async () => {
      const { configured } = await api.deviceCredentialsStatus(d.id);
      setCredentials({ device: d, configured, username: '', password: '', encryptionKey: '' });
    }, '');

  const saveCredentials = (e: FormEvent, clear = false) => {
    e.preventDefault();
    if (!credentials) return;
    const c = credentials;
    run(c.device.id, async () => {
      await api.setDeviceCredentials(c.device.id, clear ? {} : {
        username: c.username || undefined,
        password: c.password || undefined,
        encryptionKey: c.encryptionKey || undefined,
      });
      setCredentials(null);
    }, clear ? 'Credenciales eliminadas' : 'Credenciales guardadas cifradas');
  };

  const openHistory = (d: Device) =>
    run(d.id, async () => {
      const [events, commands] = await Promise.all([api.deviceEvents(d.id, 100), api.deviceCommands(d.id, 30)]);
      setHistory({ device: d, events, commands });
    }, '');

  /** Sin habitación se envía clearRoom; un umbral vacío (0) vuelve al de la casa. */
  const saveEdit = (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    run(editing.id, async () => {
      await api.updateDevice(editing.id, {
        name: editing.name,
        roomId: await resolveRoomId(editing.roomName, data?.rooms ?? []),
        clearRoom: editing.roomName.trim() === '',
        critical: editing.critical,
        consumptionAlertWatts: editing.alertWatts === '' ? 0 : Number(editing.alertWatts),
        // Vacío (0) quita la estimación; solo aplica a dispositivos sin medidor.
        ratedWatts: editing.metered ? undefined : editing.ratedWatts === '' ? 0 : Number(editing.ratedWatts),
        type: editing.type,
        manufacturer: editing.manufacturer,
        model: editing.model,
        networkAddress: usesIpAddress(editing.provider) && editing.address ? editing.address : undefined,
        externalId: editing.provider === 'ZIGBEE' && editing.address ? editing.address : undefined,
      });
      setEditing(null);
    }, 'Dispositivo actualizado');
  };

  return (
    <div className="page-content">
      <SectionTitle
        title={`${data?.devices.length ?? 0} dispositivos`}
        subtitle="Administra y controla los equipos de tu hogar"
      />

      {(error || message) && (
        <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>
      )}

      {isAdmin && (
      <Card style={{ marginBottom: 16 }}>
        <form onSubmit={create} className="filter-row" style={{ marginBottom: 0, flexWrap: 'wrap' }}>
          <div className="search grow">
            <input placeholder="Nombre del dispositivo" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </div>
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as DeviceType })} className="btn btn-outline btn-md">
            <option value="LIGHT">Luz</option>
            <option value="SMART_OUTLET">Tomacorriente</option>
            <option value="PRESENCE_SENSOR">Sensor de presencia</option>
          </select>
          <div className="search">
            <RoomField id="new-device" value={form.roomName} rooms={data?.rooms ?? []} onChange={(roomName) => setForm({ ...form, roomName })} />
          </div>
          <label className="badge badge-gray" style={{ height: 38, padding: '0 12px', cursor: 'pointer' }}>
            <input type="checkbox" checked={form.critical} onChange={(e) => setForm({ ...form, critical: e.target.checked })} /> Crítico
          </label>
          <button type="submit" className="btn btn-primary btn-md"><Plus size={16} /> Registrar</button>
        </form>
      </Card>
      )}

      <Card className="filter-card">
        <div className="search grow">
          <Search size={17} />
          <input placeholder="Buscar por nombre..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <select value={roomFilter} onChange={(e) => setRoomFilter(e.target.value)} className="btn btn-outline btn-md">
          <option value="">Todas las habitaciones</option>
          {data?.rooms.map((r) => (
            <option key={r.id} value={r.id}>{r.name}</option>
          ))}
        </select>
      </Card>

      <Card className="table-card">
        <div className="data-table">
          <div className="table-head">
            <span>DISPOSITIVO</span>
            <span>TIPO</span>
            <span>HABITACIÓN</span>
            <span>ESTADO</span>
            <span>CONEXIÓN</span>
            <span>CONSUMO</span>
            <span>BRILLO</span>
            <span />
          </div>

          {loading && !data ? (
            <div className="table-row"><span>Cargando dispositivos…</span></div>
          ) : filtered.length === 0 ? (
            <div className="table-row"><span>No hay dispositivos que coincidan.</span></div>
          ) : (
            filtered.map((d) => {
              const online = d.connectionStatus === 'ONLINE';
              const on = d.powerState === 'ON';
              const controllable = canSwitch(d);
              const dimmable = canDim(d);
              const telemetry = telemetrySummary(d);
              // Un miembro puede encender un tomacorriente, pero no deshabilitarlo.
              const blockedForMember = !isAdmin && d.type === 'SMART_OUTLET' && on;
              return (
                <div className={`table-row ${!online ? 'offline' : ''}`} key={d.id}>
                  <div className="device-name">
                    <div className={`device-icon ${on ? 'on' : ''}`}>
                      {d.type === 'LIGHT' ? <Lightbulb size={18} /> : d.type === 'PRESENCE_SENSOR' ? <Radio size={18} /> : <Plug size={18} />}
                    </div>
                    <div>
                      <strong>{d.name}</strong>
                      <span className="device-provider">
                        {providerLabel[d.provider]}{d.manufacturer ? ` · ${d.manufacturer}` : ''}{d.model ? ` ${d.model}` : ''}
                      </span>
                      {telemetry.length > 0 && <span className="device-provider">{telemetry.join(' · ')}</span>}
                      {d.critical && (
                        <span className="critical-mini"><ShieldCheck size={11} /> Crítico</span>
                      )}
                    </div>
                  </div>
                  <span>{typeLabel[d.type]}</span>
                  <RoomCell room={d.roomId ? roomById.get(d.roomId) : undefined} canEdit={isAdmin} busy={busyId === d.id}
                    onPresence={(room, present) => run(d.id, () => api.roomPresence(room.id, present),
                      present ? `Presencia registrada en ${room.name}` : `${room.name} marcada como vacía`)} />
                  <div>
                    <span title={blockedForMember ? 'Solo el administrador puede deshabilitar tomacorrientes' : undefined}>
                      <Switch
                        checked={on}
                        disabled={!online || !controllable || blockedForMember || busyId === d.id}
                        onChange={() => togglePower(d)}
                      />
                    </span>
                  </div>
                  <Badge tone={online ? 'green' : 'gray'}>
                    {online ? <Wifi size={12} /> : <WifiOff size={12} />} {online ? 'En línea' : 'Sin conexión'}
                  </Badge>
                  <LiveUsage device={d} pricePerKwh={data?.home?.electricityPricePerKwh} currency={data?.home?.currency} />
                  {dimmable && online ? (
                    <input
                      type="range"
                      min={0}
                      max={100}
                      defaultValue={d.brightness ?? 100}
                      onMouseUp={(e) => run(d.id, () => api.deviceCommand(d.id, 'BRIGHTNESS', Number((e.target as HTMLInputElement).value)), 'Brillo enviado')}
                      onTouchEnd={(e) => run(d.id, () => api.deviceCommand(d.id, 'BRIGHTNESS', Number((e.target as HTMLInputElement).value)), 'Brillo enviado')}
                      style={{ width: 90 }}
                    />
                  ) : (
                    <span className="muted">—</span>
                  )}
                  {isAdmin ? (
                    <div style={{ display: 'flex' }}>
                      <Button variant="ghost" size="icon" disabled={busyId === d.id} onClick={() => openHistory(d)}>
                        <History size={16} />
                      </Button>
                      {usesIpAddress(d.provider) && (
                        <Button variant="ghost" size="icon" disabled={busyId === d.id} onClick={() => openCredentials(d)}>
                          <KeyRound size={16} />
                        </Button>
                      )}
                      <Button variant="ghost" size="icon" disabled={busyId === d.id} onClick={() => startEdit(d)}>
                        <Pencil size={16} />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        disabled={busyId === d.id}
                        onClick={() => {
                          if (window.confirm(`¿Eliminar ${d.name}?`)) run(d.id, () => api.deleteDevice(d.id), 'Dispositivo eliminado');
                        }}
                      >
                        <Trash2 size={16} />
                      </Button>
                    </div>
                  ) : (
                    <Button variant="ghost" size="icon" disabled={busyId === d.id} onClick={() => openHistory(d)}>
                      <History size={16} />
                    </Button>
                  )}
                </div>
              );
            })
          )}
        </div>
      </Card>

      {editing && (
        <div className="modal-backdrop" onClick={() => setEditing(null)}>
          <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={saveEdit}>
            <div className="modal-title">Editar dispositivo</div>
            <label className="field">
              <span>Nombre</span>
              <div><input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} required /></div>
            </label>
            <label className="field">
              <span>Habitación</span>
              <div>
                <RoomField id="edit-device" value={editing.roomName} rooms={data?.rooms ?? []} onChange={(roomName) => setEditing({ ...editing, roomName })} />
              </div>
            </label>
            <label className="field">
              <span>Tipo</span>
              <div>
                <select value={editing.type} onChange={(e) => setEditing({ ...editing, type: e.target.value as DeviceType })} style={{ width: '100%', border: 0, background: 'transparent', color: 'var(--text)' }}>
                  <option value="LIGHT">Luz</option>
                  <option value="SMART_OUTLET">Tomacorriente</option>
                  <option value="PRESENCE_SENSOR">Sensor</option>
                </select>
              </div>
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <label className="field">
                <span>Fabricante</span>
                <div><input value={editing.manufacturer} maxLength={100} onChange={(e) => setEditing({ ...editing, manufacturer: e.target.value })} /></div>
              </label>
              <label className="field">
                <span>Modelo</span>
                <div><input value={editing.model} maxLength={120} onChange={(e) => setEditing({ ...editing, model: e.target.value })} /></div>
              </label>
            </div>
            {(usesIpAddress(editing.provider) || editing.provider === 'ZIGBEE') && (
              <label className="field">
                <span>{editing.provider === 'ZIGBEE' ? 'friendly_name en Zigbee2MQTT' : 'Dirección IP en la LAN'}</span>
                <div><input value={editing.address} maxLength={64} onChange={(e) => setEditing({ ...editing, address: e.target.value })}
                  placeholder={editing.provider === 'ZIGBEE' ? 'lampara_sala' : '192.168.1.40'} /></div>
              </label>
            )}
            <label className="field">
              <span>Umbral de consumo excesivo (W)</span>
              <div><input type="number" min={1} placeholder="Vacío = el de la casa" value={editing.alertWatts} onChange={(e) => setEditing({ ...editing, alertWatts: e.target.value })} /></div>
            </label>
            {!editing.metered && editing.type !== 'PRESENCE_SENSOR' && (
              <label className="field">
                <span>Potencia de lo conectado (W)</span>
                <div><input type="number" min={1} max={20000} step="0.1" placeholder="Ej.: 9 para un bombillo LED" value={editing.ratedWatts} onChange={(e) => setEditing({ ...editing, ratedWatts: e.target.value })} /></div>
                <small className="muted">
                  Este dispositivo no mide energía. Con la potencia (está en la etiqueta del bombillo o aparato) HomeIA estima el
                  consumo mientras está encendido. Vacío = sin estimar.
                </small>
              </label>
            )}
            <label className="badge badge-gray" style={{ height: 38, padding: '0 12px', cursor: 'pointer', marginBottom: 12 }}>
              <input type="checkbox" checked={editing.critical} onChange={(e) => setEditing({ ...editing, critical: e.target.checked })} /> Crítico (la automatización nunca lo apaga)
            </label>
            <div className="modal-actions">
              <Button variant="outline" onClick={() => setEditing(null)}>Cancelar</Button>
              <button type="submit" className="btn btn-primary btn-md" disabled={busyId === editing.id}>Guardar</button>
            </div>
          </form>
        </div>
      )}

      {credentials && (
        <div className="modal-backdrop" onClick={() => setCredentials(null)}>
          <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={(e) => saveCredentials(e)}>
            <div className="modal-title">Credenciales locales · {credentials.device.name}</div>
            <p className="muted">
              Se guardan cifradas (AES-256-GCM) y solo viajan al HomeIA Edge cifradas con su clave. Nunca se vuelven a mostrar.
              {credentials.configured ? ' Este dispositivo ya tiene credenciales: lo que guardes las reemplaza.' : ''}
            </p>
            {credentials.device.provider !== 'ESPHOME' && (
              <label className="field">
                <span>Usuario {credentials.device.provider === 'SHELLY' ? '(Shelly usa "admin")' : ''}</span>
                <div><input autoComplete="off" value={credentials.username} maxLength={64} onChange={(e) => setCredentials({ ...credentials, username: e.target.value })} placeholder="admin" /></div>
              </label>
            )}
            <label className="field">
              <span>{credentials.device.provider === 'ESPHOME' ? 'Contraseña de la API (heredada, opcional)' : 'Contraseña'}</span>
              <div><input type="password" autoComplete="new-password" value={credentials.password} maxLength={128} onChange={(e) => setCredentials({ ...credentials, password: e.target.value })} /></div>
            </label>
            {credentials.device.provider === 'ESPHOME' && (
              <label className="field">
                <span>Clave de cifrado (api: encryption: key)</span>
                <div><input type="password" autoComplete="off" value={credentials.encryptionKey} maxLength={128} onChange={(e) => setCredentials({ ...credentials, encryptionKey: e.target.value })} /></div>
              </label>
            )}
            <div className="modal-actions">
              {credentials.configured && <Button variant="danger" onClick={() => saveCredentials({ preventDefault() {} } as FormEvent, true)}>Eliminar</Button>}
              <Button variant="outline" onClick={() => setCredentials(null)}>Cancelar</Button>
              <button type="submit" className="btn btn-primary btn-md" disabled={busyId === credentials.device.id}>Guardar</button>
            </div>
          </form>
        </div>
      )}

      {history && (
        <div className="modal-backdrop" onClick={() => setHistory(null)}>
          <div className="modal" style={{ maxWidth: 720, width: '95%' }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-title">Historial · {history.device.name}</div>
            <strong>Órdenes recientes</strong>
            {history.commands.length === 0 ? <p className="muted">Sin órdenes registradas (solo se rastrean las enviadas por HomeIA Edge).</p> : (
              <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 12 }}>
                {history.commands.map((c) => (
                  <div key={c.id} style={{ display: 'flex', gap: 10, padding: '4px 0', borderBottom: '1px solid var(--border)', fontSize: 13, flexWrap: 'wrap' }}>
                    <span className="muted" style={{ minWidth: 110 }}>{formatDateTime(c.createdAt)}</span>
                    <strong>{c.command}{c.brightness != null ? ` ${c.brightness}%` : ''}</strong>
                    <Badge tone={c.status === 'CONFIRMED' ? 'green' : c.status === 'FAILED' ? 'red' : c.status === 'EXPIRED' ? 'amber' : 'blue'}>{c.status}</Badge>
                    <span className="muted">{c.attempts} intento(s) · {c.requestedBy}</span>
                    {c.error && <span style={{ color: 'var(--red)' }}>{c.error}</span>}
                  </div>
                ))}
              </div>
            )}
            <strong>Eventos</strong>
            {history.events.length === 0 ? <p className="muted">Sin eventos.</p> : (
              <div style={{ maxHeight: 280, overflowY: 'auto' }}>
                {history.events.map((e) => (
                  <div key={e.id} style={{ display: 'flex', gap: 10, padding: '4px 0', borderBottom: '1px solid var(--border)', fontSize: 13 }}>
                    <span className="muted" style={{ minWidth: 110 }}>{formatDateTime(e.createdAt)}</span>
                    <Badge tone={['ERROR', 'COMMAND_FAILED', 'OFFLINE'].includes(e.type) ? 'red' : e.type === 'COMMAND_EXPIRED' ? 'amber' : 'gray'}>{e.type}</Badge>
                    <span>{e.details}</span>
                  </div>
                ))}
              </div>
            )}
            <div className="modal-actions">
              <Button variant="outline" onClick={() => openHistory(history.device)}>Actualizar</Button>
              <Button onClick={() => setHistory(null)}>Cerrar</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Habitación del dispositivo y si hay alguien en ella. La automatización solo actúa con datos de presencia:
 * sin sensor, el administrador la marca a mano desde aquí.
 */
function RoomCell({ room, canEdit, busy, onPresence }: {
  room?: Room;
  canEdit: boolean;
  busy: boolean;
  onPresence: (room: Room, present: boolean) => void;
}) {
  if (!room) return <span className="muted">Sin habitación</span>;
  const occupied = room.occupied === true;
  const label = room.occupied == null ? 'Sin datos de presencia' : occupied ? 'Hay alguien' : 'Vacía';
  return (
    <div>
      <span>{room.name}</span>
      {canEdit ? (
        <button
          type="button"
          className={`badge ${occupied ? 'badge-green' : 'badge-gray'}`}
          style={{ display: 'flex', marginTop: 4, border: 0, cursor: 'pointer' }}
          disabled={busy}
          title={occupied ? 'Marcar la habitación como vacía' : 'Marcar que hay alguien en la habitación'}
          onClick={() => onPresence(room, !occupied)}
        >
          <span className="status-dot" /> {label}
        </button>
      ) : (
        <span className={`badge ${occupied ? 'badge-green' : 'badge-gray'}`} style={{ display: 'flex', marginTop: 4 }}>
          <span className="status-dot" /> {label}
        </span>
      )}
    </div>
  );
}
