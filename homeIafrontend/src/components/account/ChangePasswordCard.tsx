'use client';

import { FormEvent, useState } from 'react';
import { api, ApiError } from '@/services/api';
import { Button, Card, SectionTitle } from '@/components/ui/Primitives';
import { useAuth } from '@/contexts/AuthContext';
import NewPasswordFields, { isNewPasswordValid } from '@/components/auth/NewPasswordFields';

export default function ChangePasswordCard() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const { user } = useAuth();
  const personalData = [user?.fullName, user?.email];
  const passwordOk = isNewPasswordValid(form.newPassword, form.confirm, personalData);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setMessage('');
    setError('');
    if (!passwordOk) {
      setError('Revisa la nueva contraseña: debe cumplir todas las reglas y coincidir con la confirmación');
      return;
    }
    setBusy(true);
    try {
      await api.changeMyPassword(form.currentPassword, form.newPassword);
      setForm({ currentPassword: '', newPassword: '', confirm: '' });
      setMessage('Contraseña cambiada. Las demás sesiones se cerraron.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo cambiar la contraseña');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={{ marginTop: 16 }}>
      <SectionTitle title="Mi contraseña" subtitle="Al cambiarla se cierran tus sesiones en otros dispositivos" />
      {error && <div style={{ color: 'var(--red)', marginBottom: 10 }}>{error}</div>}
      {message && <div style={{ color: 'var(--green)', marginBottom: 10 }}>{message}</div>}
      <form onSubmit={submit}>
        <label className="field"><span>Contraseña actual</span><div><input type="password" autoComplete="current-password" value={form.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} required /></div></label>
        <div style={{ maxWidth: 460 }}>
          <NewPasswordFields
            password={form.newPassword}
            confirm={form.confirm}
            onPasswordChange={(newPassword) => setForm({ ...form, newPassword })}
            onConfirmChange={(confirm) => setForm({ ...form, confirm })}
            personalData={personalData}
            label="Nueva contraseña"
            confirmLabel="Confirmar nueva contraseña"
          />
        </div>
        <Button variant="primary" disabled={busy || !passwordOk}>{busy ? 'Guardando…' : 'Cambiar contraseña'}</Button>
      </form>
    </Card>
  );
}
