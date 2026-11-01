'use client';

import { ReactNode, Suspense, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import AppShell from '@/components/layout/AppShell';
import DashboardLoading from './loading';

/**
 * La página se renderiza siempre, sin esperar a que se confirme la sesión: así la verificación
 * (GET /users/me) y la carga de datos de la página corren en paralelo, sin cascada. Quien entra sin
 * cookie de sesión ya fue redirigido en el servidor (src/proxy.ts); si el token guardado no sirve,
 * la primera llamada a la API cierra la sesión y aquí se redirige al login.
 * AppShell lee la ruta actual (usePathname), que en rutas dinámicas solo se conoce al pedir la
 * página: el Suspense deja prerenderizar el esqueleto y completar el resto al llegar la petición.
 */
export default function DashboardLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/auth/login');
    }
  }, [isAuthenticated, isLoading, router]);

  return (
    <Suspense fallback={<DashboardLoading />}>
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
