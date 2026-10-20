import type { NextConfig } from "next";
import { networkInterfaces } from "node:os";

/**
 * Orígenes desde los que se puede abrir el servidor de desarrollo además de localhost.
 * Next.js 16 bloquea por defecto la conexión de desarrollo (/_next/hmr) desde otras direcciones,
 * y sin ella la página no se activa: se ve el formulario, pero no responde y el botón queda deshabilitado.
 * Se permiten automáticamente las IPs de este equipo (que cambian según la red) y las que se agreguen
 * en NEXT_DEV_ALLOWED_ORIGINS, separadas por comas (p. ej. "192.168.1.50,mi-pc.local").
 */
function devOrigins(): string[] {
  const ownIps = Object.values(networkInterfaces())
    .flat()
    .filter((net) => net && net.family === "IPv4" && !net.internal)
    .map((net) => net!.address);
  const extra = (process.env.NEXT_DEV_ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
  return [...new Set([...ownIps, ...extra])];
}

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  // Permite abrir el frontend en desarrollo por la IP del equipo o desde otros de la red local.
  allowedDevOrigins: devOrigins(),
  partialPrefetching: true,
  // La raíz redirige al login en la configuración (no en la página), para no renderizar nada en "/".
  async redirects() {
    return [{ source: "/", destination: "/auth/login", permanent: false }];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
