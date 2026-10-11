'use client';

import { FormEvent, useState } from 'react';
import { api } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import type { AuditEntry, Page } from '@/types';
import { Button, Card, SectionTitle } from '@/components/ui/Primitives';
import { formatDateTime } from '@/components/ui/AlertCard';

const PAGE_SIZE = 30;

export default function HistoryPage() {
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [page, setPage] = useState(0);
  const [applied, setApplied] = useState({ action: '', entityType: '' });

  const { data, error, loading } = useAsync<Page<AuditEntry>>(
    () => api.audit({ action: applied.action || undefined, entityType: applied.entityType || undefined, page, size: PAGE_SIZE }),
    [applied, page],
  );

  const apply = (e: FormEvent) => {
    e.preventDefault();
    setPage(0);
    setApplied({ action: action.trim(), entityType: entityType.trim() });
  };

  return (
    <div className="page-content">
      <SectionTitle title="Historial y auditoría" subtitle="Quién hizo qué y cuándo en la casa" />

      <Card className="filter-card">
        <form onSubmit={apply} className="filter-row" style={{ marginBottom: 0, flexWrap: 'wrap' }}>
          <div className="search grow"><input placeholder="Acción (p. ej. LOGIN, DEVICE_COMMAND)" value={action} onChange={(e) => setAction(e.target.value)} /></div>
          <div className="search grow"><input placeholder="Tipo de entidad (p. ej. DEVICE, ALERT)" value={entityType} onChange={(e) => setEntityType(e.target.value)} /></div>
          <button type="submit" className="btn btn-primary btn-md">Filtrar</button>
        </form>
      </Card>

      {error && <Card style={{ color: 'var(--red)', marginBottom: 16 }}>{error}</Card>}

      <Card className="table-card">
        <div className="audit-list">
          <div className="audit-head">
            <span>FECHA</span><span>ACTOR</span><span>ACCIÓN</span><span>ENTIDAD</span><span>DETALLE</span>
          </div>
          {loading && !data && <div className="audit-row"><span>Cargando…</span></div>}
          {data?.content.length === 0 && <div className="audit-row"><span>No hay registros.</span></div>}
          {data?.content.map((e) => (
            <div className="audit-row" key={e.id}>
              <span>{formatDateTime(e.createdAt)}</span>
              <span>{e.actor}</span>
              <strong>{e.action}</strong>
              <span>{e.entityType}</span>
              <span>{e.details ?? '—'}</span>
            </div>
          ))}
        </div>
      </Card>

      {data && data.totalPages > 1 && (
        <div className="quick-actions" style={{ marginTop: 16, justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</Button>
          <span className="muted">Página {page + 1} de {data.totalPages}</span>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Siguiente</Button>
        </div>
      )}
    </div>
  );
}
