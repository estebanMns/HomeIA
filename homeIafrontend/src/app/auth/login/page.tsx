import type { Metadata } from 'next';
import { Suspense } from 'react';
import LoginBrand from '@/components/auth/LoginBrand';
import LoginForm from '@/components/auth/LoginForm';

export const metadata: Metadata = { title: 'Ingresar · HomeIA' };

/**
 * Server Component: el panel de marca se renderiza en el servidor y llega como HTML estático (SSG),
 * sin JavaScript. Solo el formulario es un Client Component; el Suspense es necesario porque lee
 * los parámetros de la URL, que solo existen en el navegador.
 */
export default function LoginPage() {
  return (
    <div className="login-page">
      <LoginBrand variant="login" />
      <Suspense fallback={<div className="login-form-wrap" />}>
        <LoginForm />
      </Suspense>
    </div>
  );
}
