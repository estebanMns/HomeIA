'use client';

import { FormEvent, useState } from 'react';
import { api, ApiError } from '@/services/api';
import { Button, Card, SectionTitle } from '@/components/ui/Primitives';

export default function ChangePasswordCard() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setMessage('');
    setError('');
    if (form.newPassword !== form.confirm) {
      setError('La confirmación no coincide con la nueva contraseña');
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
      <SectionTitle title="Mi contraseña" subtitle="Mínimo 10 caracteres" />
      {error && <div style={{ color: 'var(--red)', marginBottom: 10 }}>{error}</div>}
      {message && <div style={{ color: 'var(--green)', marginBottom: 10 }}>{message}</div>}
      <form onSubmit={submit}>
        <label className="field"><span>Contraseña actual</span><div><input type="password" autoComplete="current-password" value={form.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} required /></div></label>
        <div className="settings-grid">
          <label className="field"><span>Nueva contraseña</span><div><input type="password" minLength={10} autoComplete="new-password" value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} required /></div></label>
          <label className="field"><span>Confirmar</span><div><input type="password" minLength={10} autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} required /></div></label>
        </div>
        <Button variant="primary" disabled={busy}>{busy ? 'Guardando…' : 'Cambiar contraseña'}</Button>
      </form>
    </Card>
  );
}
