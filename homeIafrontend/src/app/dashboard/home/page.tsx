'use client';

import { FormEvent, useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { Home, User, UserRole } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';
import { formatDateTime } from '@/components/ui/AlertCard';
import ChangePasswordCard from '@/components/account/ChangePasswordCard';
import NewPasswordFields, { isNewPasswordValid } from '@/components/auth/NewPasswordFields';

interface Data {
  home: Home;
  users: User[];
}

export default function HomePage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data, error, loading, reload } = useAsync<Data>(async () => {
    const [home, users] = await Promise.all([api.home(), api.users()]);
    return { home, users };
  }, []);

  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [homeForm, setHomeForm] = useState({ name: '', currency: '', electricityPricePerKwh: '', timezone: '' });
  const [newUser, setNewUser] = useState({ fullName: '', email: '', password: '', role: 'MEMBER' as UserRole });

  useEffect(() => {
    if (data) {
      setHomeForm({
        name: data.home.name,
        currency: data.home.currency,
        electricityPricePerKwh: String(data.home.electricityPricePerKwh),
        timezone: data.home.timezone,
      });
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

  const saveHome = (e: FormEvent) => {
    e.preventDefault();
    run(() => api.updateHome({
      name: homeForm.name,
      currency: homeForm.currency,
      electricityPricePerKwh: Number(homeForm.electricityPricePerKwh),
      timezone: homeForm.timezone,
    }), 'Datos de la casa actualizados');
  };

  const [newUserConfirm, setNewUserConfirm] = useState('');
  const newUserPasswordOk = isNewPasswordValid(newUser.password, newUserConfirm);

  const createUser = (e: FormEvent) => {
    e.preventDefault();
    if (!newUserPasswordOk) return;
    run(async () => {
      await api.createUser(newUser);
      setNewUser({ fullName: '', email: '', password: '', role: 'MEMBER' });
      setNewUserConfirm('');
    }, 'Usuario creado');
  };

  return (
    <div className="page-content">
      <SectionTitle title="Usuarios y casa" subtitle="Datos de la casa, tarifa de energía y miembros con acceso" />

      {(error || message) && <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>}
      {loading && !data && <div className="muted">Cargando…</div>}

      {data && (
        <div className="two-col">
          <Card>
            <SectionTitle title="La casa" subtitle={isAdmin ? 'Solo administradores pueden editar' : 'Vista de lectura'} />
            <form onSubmit={saveHome}>
              <label className="field"><span>Nombre</span><div><input value={homeForm.name} disabled={!isAdmin} onChange={(e) => setHomeForm({ ...homeForm, name: e.target.value })} /></div></label>
              <div className="settings-grid">
                <label className="field"><span>Moneda</span><div><input value={homeForm.currency} disabled={!isAdmin} maxLength={3} onChange={(e) => setHomeForm({ ...homeForm, currency: e.target.value })} /></div></label>
                <label className="field"><span>Tarifa / kWh</span><div><input type="number" min="0" step="0.01" value={homeForm.electricityPricePerKwh} disabled={!isAdmin} onChange={(e) => setHomeForm({ ...homeForm, electricityPricePerKwh: e.target.value })} /></div></label>
              </div>
              <label className="field"><span>Zona horaria</span><div><input value={homeForm.timezone} disabled={!isAdmin} onChange={(e) => setHomeForm({ ...homeForm, timezone: e.target.value })} /></div></label>
              {isAdmin && <button type="submit" className="btn btn-primary btn-md" disabled={busy}>Guardar casa</button>}
            </form>
          </Card>

          <Card>
            <SectionTitle title="Miembros" subtitle={`${data.users.length} usuarios con acceso`} />
            {data.users.map((u) => (
              <div key={u.id} className="user-row">
                <div className="avatar">{u.fullName.charAt(0).toUpperCase()}</div>
                <div>
                  <strong>{u.fullName} {u.id === user?.id && <small>tú</small>}</strong>
                  <span>{u.email} · último acceso {u.lastLoginAt ? formatDateTime(u.lastLoginAt) : 'nunca'}</span>
                </div>
                <Badge tone={u.role === 'ADMIN' ? 'purple' : 'gray'}>{u.role === 'ADMIN' ? 'Admin' : 'Miembro'}</Badge>
                {isAdmin && u.id !== user?.id && (
                  <>
                    <Button variant="outline" size="sm" disabled={busy} onClick={() => run(() => api.updateUser(u.id, { active: !u.active }), u.active ? 'Usuario desactivado' : 'Usuario activado')}>
                      {u.active ? 'Desactivar' : 'Activar'}
                    </Button>
                    <Button variant="ghost" size="icon" disabled={busy} onClick={() => {
                      if (window.confirm(`¿Eliminar a ${u.fullName}?`)) run(() => api.deleteUser(u.id), 'Usuario eliminado');
                    }}>
                      <Trash2 size={16} />
                    </Button>
                  </>
                )}
              </div>
            ))}

            {isAdmin && (
              <form onSubmit={createUser} style={{ marginTop: 18 }}>
                <div className="section-heading">Agregar miembro</div>
                <div className="settings-grid">
                  <label className="field"><span>Nombre</span><div><input value={newUser.fullName} onChange={(e) => setNewUser({ ...newUser, fullName: e.target.value })} required /></div></label>
                  <label className="field"><span>Correo</span><div><input type="email" value={newUser.email} onChange={(e) => setNewUser({ ...newUser, email: e.target.value })} required /></div></label>
                </div>
                <div style={{ maxWidth: 460 }}>
                  <NewPasswordFields
                    password={newUser.password}
                    confirm={newUserConfirm}
                    onPasswordChange={(password) => setNewUser({ ...newUser, password })}
                    onConfirmChange={setNewUserConfirm}
                    label="Contraseña inicial"
                  />
                </div>
                <label className="field">
                  <span>Rol</span>
                  <div>
                    <select value={newUser.role} onChange={(e) => setNewUser({ ...newUser, role: e.target.value as UserRole })} style={{ width: '100%', border: 0, background: 'transparent', color: 'var(--text)' }}>
                      <option value="MEMBER">Miembro</option>
                      <option value="ADMIN">Administrador</option>
                    </select>
                  </div>
                </label>
                <button type="submit" className="btn btn-primary btn-md" disabled={busy || !newUserPasswordOk}><Plus size={16} /> Agregar</button>
              </form>
            )}
          </Card>
        </div>
      )}

      <ChangePasswordCard />
    </div>
  );
}
