'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, ApiError } from '@/services/api';
import { Logo } from '@/components/ui/Primitives';

export default function SetupPage() {
  const router = useRouter();
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ adminFullName: '', adminEmail: '', adminPassword: '' });

  useEffect(() => {
    api
      .setupStatus()
      .then((s) => {
        if (!s.needsSetup) router.replace('/auth/login');
        else setChecking(false);
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudo consultar el estado'));
  }, [router]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSaving(true);
    try {
      await api.setup(form);
      router.replace('/auth/login');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo crear la cuenta');
    } finally {
      setSaving(false);
    }
  };

  if (checking && !error) {
    return <div className="login-page" />;
  }

  return (
    <div className="login-page">
      <div className="login-brand">
        <Logo />
        <div className="login-message">
          <div>
            Crea tu
            <br />
            cuenta <em>de administrador.</em>
          </div>
          <p>Solo se pide lo necesario. La casa y las reglas de ahorro se pueden ajustar después.</p>
        </div>
      </div>

      <div className="login-form-wrap">
        <form className="login-form" onSubmit={submit}>
          <div className="detail-title">Crear cuenta</div>
          <p>Esta pantalla solo está disponible mientras no exista ninguna cuenta.</p>

          {error && (
            <div className="card" style={{ margin: '14px 0', color: 'var(--red)', borderColor: 'var(--red)' }}>
              {error}
            </div>
          )}

          <label className="field">
            <span>Nombre completo</span>
            <div>
              <input value={form.adminFullName} onChange={(e) => setForm({ ...form, adminFullName: e.target.value })} autoComplete="name" required />
            </div>
          </label>
          <label className="field">
            <span>Correo electrónico</span>
            <div>
              <input type="email" value={form.adminEmail} onChange={(e) => setForm({ ...form, adminEmail: e.target.value })} autoComplete="email" required />
            </div>
          </label>
          <label className="field">
            <span>Contraseña (mínimo 10 caracteres)</span>
            <div>
              <input type="password" value={form.adminPassword} onChange={(e) => setForm({ ...form, adminPassword: e.target.value })} minLength={10} autoComplete="new-password" required />
            </div>
          </label>

          <button type="submit" className="btn btn-primary btn-md login-button" disabled={saving}>
            {saving ? 'Creando…' : 'Crear cuenta'}
          </button>
        </form>
      </div>
    </div>
  );
}
