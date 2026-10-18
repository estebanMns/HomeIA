'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ChevronRight, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiError } from '@/services/api';
import { Logo } from '@/components/ui/Primitives';

export default function RegisterPage() {
  const router = useRouter();
  const { register } = useAuth();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (form.password !== form.confirm) {
      setError('Las contraseñas no coinciden');
      return;
    }
    setSubmitting(true);
    try {
      await register(form.fullName.trim(), form.email.trim(), form.password);
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo crear la cuenta. Revisa tu conexión.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-brand">
        <Logo />
        <div className="login-message">
          <div>
            Crea tu
            <br />
            cuenta <em>en HomeIA.</em>
          </div>
          <p>Tu casa queda lista con valores por defecto. Después puedes ajustar habitaciones, dispositivos y tarifa.</p>
        </div>
      </div>

      <div className="login-form-wrap">
        <form className="login-form" onSubmit={handleSubmit}>
          <div className="mobile-logo">
            <Logo />
          </div>
          <div className="detail-title">Crear cuenta</div>
          <p>Usa tu nombre y un correo que consultes a menudo.</p>

          {error && (
            <div className="card" style={{ margin: '14px 0', color: 'var(--red)', borderColor: 'var(--red)' }}>
              {error}
            </div>
          )}

          <label className="field">
            <span>Nombre completo</span>
            <div>
              <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} autoComplete="name" required />
            </div>
          </label>
          <label className="field">
            <span>Correo electrónico</span>
            <div>
              <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} autoComplete="email" required />
            </div>
          </label>
          <label className="field">
            <span>Contraseña (mínimo 10 caracteres)</span>
            <div>
              <input
                type="password"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                minLength={10}
                autoComplete="new-password"
                required
              />
            </div>
          </label>
          <label className="field">
            <span>Repite la contraseña</span>
            <div>
              <input
                type="password"
                value={form.confirm}
                onChange={(e) => setForm({ ...form, confirm: e.target.value })}
                minLength={10}
                autoComplete="new-password"
                required
              />
            </div>
          </label>

          <button type="submit" className="btn btn-primary btn-md login-button" disabled={submitting}>
            {submitting ? 'Creando cuenta…' : 'Crear cuenta'} {!submitting && <ChevronRight size={17} />}
          </button>

          <div className="login-help">
            <ShieldCheck size={15} /> ¿Ya tienes cuenta? <Link href="/auth/login">Ingresa aquí</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
