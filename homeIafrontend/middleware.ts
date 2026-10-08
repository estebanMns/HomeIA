import { NextRequest, NextResponse } from 'next/server';

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Rutas públicas
  const publicRoutes = ['/auth/login', '/'];

  // Verificar si es ruta pública
  if (publicRoutes.includes(pathname)) {
    return NextResponse.next();
  }

  // Verificar token para rutas protegidas
  if (pathname.startsWith('/dashboard')) {
    const token = request.cookies.get('accessToken')?.value;

    if (!token) {
      // Si no hay token en cookie, intentar de localStorage (será en el cliente)
      // Redirigir al login
      return NextResponse.redirect(new URL('/auth/login', request.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next|.*\\..*|api).*)'],
};
