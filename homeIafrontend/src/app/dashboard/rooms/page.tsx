'use client';

import { FormEvent, useState } from 'react';
import { DoorOpen, Pencil, Plus, Trash2 } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import type { Device, Room, RoomEnergy } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';

interface Data {
  rooms: Room[];
  devices: Device[];
  energy: RoomEnergy[];
}

export default function RoomsPage() {
  const { data, error, loading, reload } = useAsync<Data>(async () => {
    const to = new Date();
    const from = new Date(to.getTime() - 24 * 3600 * 1000);
    const [rooms, devices, energy] = await Promise.all([
      api.rooms(),
      api.devices(),
      api.energyByRoom(from.toISOString(), to.toISOString()),
    ]);
    return { rooms, devices, energy };
  }, [], 30000);

  const [name, setName] = useState('');
  const [location, setLocation] = useState('');
  const [editing, setEditing] = useState<Room | null>(null);
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

  const create = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      await api.createRoom({ name, location: location || undefined });
      setName('');
      setLocation('');
    }, 'Habitación creada');
  };

  const saveEdit = (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    run(async () => {
      await api.updateRoom(editing.id, { name: editing.name, location: editing.location ?? '' });
      setEditing(null);
    }, 'Habitación actualizada');
  };

  const energyFor = (roomId: string) => data?.energy.find((e) => e.roomId === roomId);

  return (
    <div className="page-content">
      <SectionTitle
        title={`${data?.rooms.length ?? 0} habitaciones`}
        subtitle="Organiza los dispositivos por espacio: la automatización decide por habitación"
      />

      {(error || message) && (
        <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>
      )}

      <Card style={{ marginBottom: 16 }}>
        <form onSubmit={create} className="filter-row" style={{ marginBottom: 0 }}>
          <div className="search grow">
            <DoorOpen size={17} />
            <input placeholder="Nombre (p. ej. Sala)" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div className="search grow">
            <input placeholder="Ubicación (opcional)" value={location} onChange={(e) => setLocation(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary btn-md" disabled={busy}>
            <Plus size={16} /> Agregar
          </button>
        </form>
      </Card>

      {loading && !data ? (
        <div className="muted">Cargando…</div>
      ) : data?.rooms.length === 0 ? (
        <Card className="muted">No hay habitaciones todavía.</Card>
      ) : (
        <div className="rooms-grid">
          {data?.rooms.map((room) => {
            const devices = data.devices.filter((d) => d.roomId === room.id);
            const energy = energyFor(room.id);
            const presence = devices.some((d) => d.presence === true);
            return (
              <Card key={room.id} className="room-card">
                <div className="room-main">
                  <div className="room-top">
                    <div className="room-icon">{presence ? '🛋' : '🚪'}</div>
                    <Badge tone={presence ? 'green' : 'gray'}>
                      <span className="status-dot" /> {presence ? 'Presencia' : 'Sin presencia'}
                    </Badge>
                  </div>
                  <div className="room-name">{room.name}</div>
                  <div className="muted">{room.location ?? 'Sin ubicación'} · {devices.length} dispositivos</div>
                  <div className="room-stats">
                    <span>{energy ? `${energy.kwh.toLocaleString('es-CO')} kWh` : 'Sin consumo'}</span>
                    <strong>{energy ? `${energy.cost.toLocaleString('es-CO')}` : ''}</strong>
                  </div>
                  <div className="quick-actions" style={{ marginTop: 12 }}>
                    <Button variant="outline" size="sm" disabled={busy} onClick={() => run(() => api.roomPresence(room.id, true), 'Presencia registrada')}>
                      Registrar presencia
                    </Button>
                    <Button variant="outline" size="sm" disabled={busy} onClick={() => run(() => api.roomPresence(room.id, false), 'Habitación vacía registrada')}>
                      Marcar vacía
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => setEditing(room)} disabled={busy}>
                      <Pencil size={16} />
                    </Button>
                    <Button variant="ghost" size="icon" disabled={busy} onClick={() => {
                      if (window.confirm(`¿Eliminar ${room.name}? Sus dispositivos quedarán sin habitación.`)) {
                        run(() => api.deleteRoom(room.id), 'Habitación eliminada');
                      }
                    }}>
                      <Trash2 size={16} />
                    </Button>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {editing && (
        <div className="modal-backdrop" onClick={() => setEditing(null)}>
          <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={saveEdit}>
            <div className="modal-title">Editar habitación</div>
            <label className="field">
              <span>Nombre</span>
              <div><input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} required /></div>
            </label>
            <label className="field">
              <span>Ubicación</span>
              <div><input value={editing.location ?? ''} onChange={(e) => setEditing({ ...editing, location: e.target.value })} /></div>
            </label>
            <div className="modal-actions">
              <Button variant="outline" onClick={() => setEditing(null)}>Cancelar</Button>
              <button type="submit" className="btn btn-primary btn-md" disabled={busy}>Guardar</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
