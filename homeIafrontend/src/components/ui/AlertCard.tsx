'use client';

import { AlertTriangle, Clock3, ShieldCheck } from 'lucide-react';
import { Alert, AlertSeverity, AlertType } from '@/types';
import { Badge, BadgeTone } from './Primitives';

const severityLabel: Record<AlertSeverity, string> = {
  CRITICAL: 'Crítica',
  WARNING: 'Advertencia',
  INFO: 'Info',
};

const severityTone: Record<AlertSeverity, BadgeTone> = {
  CRITICAL: 'red',
  WARNING: 'amber',
  INFO: 'blue',
};

export const alertTypeLabel: Record<AlertType, string> = {
  EXCESSIVE_CONSUMPTION: 'Consumo excesivo',
  POSSIBLE_ENERGY_LEAK: 'Posible fuga',
  DEVICE_OFFLINE: 'Sin conexión',
  AUTOMATIC_SHUTDOWN: 'Apagado automático',
  MANUAL: 'Manual',
};

export function formatDateTime(iso?: string) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export default function AlertCard({ alert }: { alert: Alert }) {
  return (
    <div className={`alert-row ${alert.severity === 'CRITICAL' ? 'alert-red' : ''}`}>
      <div className={`alert-icon ${alert.severity === 'CRITICAL' ? 'red' : ''}`}>
        <AlertTriangle size={18} />
      </div>
      <div className="alert-copy">
        <div className="alert-head">
          <strong>{alert.title}</strong>
          <Badge tone={severityTone[alert.severity]}>{severityLabel[alert.severity]}</Badge>
        </div>
        <span>{alertTypeLabel[alert.type]}</span>
        <p>{alert.message}</p>
        <div className="alert-meta">
          <span>
            <Clock3 size={13} />
            {formatDateTime(alert.createdAt)}
          </span>
          {alert.acknowledged && (
            <span>
              <ShieldCheck size={13} />
              Revisada {alert.acknowledgedBy ? `por ${alert.acknowledgedBy}` : ''}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
