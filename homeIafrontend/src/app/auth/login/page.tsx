'use client';

import { FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ChevronRight, ShieldCheck, Sparkles, TrendingDown } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { api, ApiError } from '@/services/api';
import { Badge, Logo } from '@/components/ui/Primitives';

export default function LoginPage() {
  const router = useRouter();
  const { login, isAuthenticated, isLoading } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace('/dashboard');
      return;
    }
    api
      .setupStatus()
      .then((s) => {
        if (s.needsSetup) router.replace('/setup');
      })
      .catch(() => setError('No se pudo conectar con el servidor de HomeIA'));
  }, [isAuthenticated, isLoading, router]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login(email, password);
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo conectar con el servidor de HomeIA');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-brand">
        <Logo />
        <div className="login-message">
          <Badge tone="blue">
            <Sparkles size={12} /> Energía inteligente
          </Badge>
          <div>
            Tu casa ahorra
            <br />
            energía <em>sola.</em>
          </div>
          <p>HomeIA entiende cómo vives, optimiza cada espacio y reduce tu consumo sin que tengas que pensarlo.</p>
        </div>
        <div className="login-stat">
          <TrendingDown size={24} />
          <div>
            <strong>Automatización activa</strong>
            <span>apagado inteligente de habitaciones vacías</span>
          </div>
        </div>
      </div>

      <div className="login-form-wrap">
        <form className="login-form" onSubmit={handleSubmit}>
          <div className="mobile-logo">
            <Logo />
          </div>
          <div className="detail-title">Bienvenido de nuevo</div>
          <p>Ingresa para ver cómo está ahorrando tu hogar.</p>

          {error && (
            <div className="card" style={{ margin: '14px 0', color: 'var(--red)', borderColor: 'var(--red)' }}>
              {error}
            </div>
          )}

          <label className="field">
            <span>Correo electrónico</span>
            <div>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
            </div>
          </label>
          <label className="field">
            <span>Contraseña</span>
            <div>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
          </label>

          <button type="submit" className="btn btn-primary btn-md login-button" disabled={submitting}>
            {submitting ? 'Ingresando…' : 'Ingresar a HomeIA'} {!submitting && <ChevronRight size={17} />}
          </button>

          <div className="login-help">
            <ShieldCheck size={15} /> Conexión segura y protegida · <Link href="/auth/forgot">¿Olvidaste tu contraseña?</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
