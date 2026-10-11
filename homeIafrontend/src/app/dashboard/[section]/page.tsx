import { notFound } from 'next/navigation';

// Ninguna sección del menú usa esta ruta: cualquier otra dirección bajo /dashboard es inexistente.
export default function UnknownSectionPage() {
  notFound();
}
