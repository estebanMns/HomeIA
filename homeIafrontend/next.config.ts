import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  // Permite abrir el frontend en desarrollo desde otros equipos de la red local (HMR y hidratación).
  allowedDevOrigins: ["192.168.1.106"],
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
