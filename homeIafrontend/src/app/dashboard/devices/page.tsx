'use client';

import { FormEvent, useMemo, useState } from 'react';
import { Lightbulb, Plug, Plus, Radio, Search, ShieldCheck, Trash2, Wifi, WifiOff } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { Device, DeviceType, Room } from '@/types';
import { Badge, Button, Card, SectionTitle, Switch } from '@/components/ui/Primitives';

const typeLabel: Record<DeviceType, string> = {
  LIGHT: 'Luz',
  SMART_OUTLET: 'Tomacorriente',
  PRESENCE_SENSOR: 'Sensor de presencia',
};

interface Data {
  devices: Device[];
  rooms: Room[];
}

export default function DevicesPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data, error, loading, reload } = useAsync<Data>(async () => {
    const [devices, rooms] = await Promise.all([api.devices(), api.rooms()]);
    return { devices, rooms };
  }, [], 30000);

  const [query, setQuery] = useState('');
  const [roomFilter, setRoomFilter] = useState('');
  const [message, setMessage] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [form, setForm] = useState({ name: '', type: 'LIGHT' as DeviceType, roomId: '', critical: false });

  const roomName = useMemo(() => new Map((data?.rooms ?? []).map((r) => [r.id, r.name])), [data]);

  const filtered = (data?.devices ?? []).filter(
    (d) =>
      d.name.toLowerCase().includes(query.toLowerCase()) &&
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
        roomId: form.roomId || undefined,
        critical: form.critical,
      });
      setForm({ ...form, name: '' });
    }, 'Dispositivo registrado');
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
          <select value={form.roomId} onChange={(e) => setForm({ ...form, roomId: e.target.value })} className="btn btn-outline btn-md">
            <option value="">Sin habitación</option>
            {data?.rooms.map((r) => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
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
              const controllable = d.type !== 'PRESENCE_SENSOR';
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
                      {d.critical && (
                        <span className="critical-mini"><ShieldCheck size={11} /> Crítico</span>
                      )}
                    </div>
                  </div>
                  <span>{typeLabel[d.type]}</span>
                  <span>{d.roomId ? roomName.get(d.roomId) ?? '—' : 'Sin habitación'}</span>
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
                  <strong>{d.consumptionWatts.toLocaleString('es-CO')} W</strong>
                  {d.type === 'LIGHT' && online ? (
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
                  ) : (
                    <span />
                  )}
                </div>
              );
            })
          )}
        </div>
      </Card>
    </div>
  );
}
