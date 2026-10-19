'use client';

import { FormEvent, Suspense, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { api, ApiError } from '@/services/api';
import { Logo } from '@/components/ui/Primitives';
import NewPasswordFields, { isNewPasswordValid } from '@/components/auth/NewPasswordFields';

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div className="login-page" />}>
      <ResetForm />
    </Suspense>
  );
}

function ResetForm() {
  const router = useRouter();
  const token = useSearchParams().get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!isNewPasswordValid(password, confirm)) {
      setError('Revisa la contraseña: debe cumplir todas las reglas y coincidir con la confirmación');
      return;
    }
    setBusy(true);
    try {
      await api.resetPassword(token, password);
      setDone(true);
      setTimeout(() => router.replace('/auth/login'), 2500);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo cambiar la contraseña');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-brand">
        <Logo />
        <div className="login-message">
          <div>
            Elige una
            <br />
            nueva <em>contraseña.</em>
          </div>
          <p>El enlace solo funciona una vez y caduca a los 30 minutos.</p>
        </div>
      </div>

      <div className="login-form-wrap">
        <form className="login-form" onSubmit={submit}>
          <div className="detail-title">Nueva contraseña</div>

          {!token && <div className="card" style={{ margin: '14px 0', color: 'var(--red)' }}>El enlace no tiene token.</div>}

          {done ? (
            <div className="card" style={{ margin: '14px 0' }}>Contraseña cambiada. Te llevamos al inicio de sesión…</div>
          ) : (
            <>
              {error && <div className="card" style={{ margin: '14px 0', color: 'var(--red)', borderColor: 'var(--red)' }}>{error}</div>}
              <NewPasswordFields
                password={password}
                confirm={confirm}
                onPasswordChange={setPassword}
                onConfirmChange={setConfirm}
                label="Nueva contraseña"
                confirmLabel="Confirmar contraseña"
              />
              <button type="submit" className="btn btn-primary btn-md login-button" disabled={busy || !token || !isNewPasswordValid(password, confirm)}>
                {busy ? 'Guardando…' : 'Cambiar contraseña'}
              </button>
            </>
          )}

          <div className="login-help"><Link href="/auth/login">Volver a iniciar sesión</Link></div>
        </form>
      </div>
    </div>
  );
}
