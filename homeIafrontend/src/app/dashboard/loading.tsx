/**
 * Streaming con Suspense: Next.js envuelve cada página del dashboard en un <Suspense> con este
 * esqueleto. Al navegar se muestra al instante mientras llega la página, en vez de una pantalla
 * en blanco. Es un Server Component: no envía JavaScript.
 */
export default function DashboardLoading() {
  return (
    <div className="page-content" aria-busy="true" aria-label="Cargando">
      <div className="skeleton" style={{ height: 28, width: 220, marginBottom: 18 }} />
      <div className="skeleton-grid">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="skeleton" style={{ height: 96 }} />
        ))}
      </div>
      <div className="skeleton" style={{ height: 240 }} />
    </div>
  );
}
