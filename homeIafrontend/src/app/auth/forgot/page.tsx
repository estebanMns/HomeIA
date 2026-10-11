'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import { api, ApiError } from '@/services/api';
import { Logo } from '@/components/ui/Primitives';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await api.forgotPassword(email);
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo enviar la solicitud');
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
            ¿Olvidaste
            <br />
            tu <em>contraseña?</em>
          </div>
          <p>Te enviaremos un enlace a tu correo para elegir una nueva contraseña.</p>
        </div>
      </div>

      <div className="login-form-wrap">
        <form className="login-form" onSubmit={submit}>
          <div className="detail-title">Restablecer contraseña</div>

          {sent ? (
            <div className="card" style={{ margin: '14px 0' }}>
              Si el correo está registrado, recibirás un enlace en unos minutos. Revisa también la carpeta de spam.
            </div>
          ) : (
            <>
              <p>Escribe el correo con el que te registraste.</p>
              {error && (
                <div className="card" style={{ margin: '14px 0', color: 'var(--red)', borderColor: 'var(--red)' }}>{error}</div>
              )}
              <label className="field">
                <span>Correo electrónico</span>
                <div><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required /></div>
              </label>
              <button type="submit" className="btn btn-primary btn-md login-button" disabled={busy}>
                {busy ? 'Enviando…' : 'Enviar enlace'}
              </button>
            </>
          )}

          <div className="login-help"><Link href="/auth/login">Volver a iniciar sesión</Link></div>
        </form>
      </div>
    </div>
  );
}
