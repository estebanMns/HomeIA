'use client';

import { FormEvent, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type { BillStatus, MeterReading, TariffInput } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';

const money = (n?: number) => (n === undefined ? '—' : `$${Math.round(n).toLocaleString('es-CO')}`);

const emptyTariff: TariffInput = {
  periodStart: '',
  periodEnd: '',
  unitPricePerKwh: 0,
  subsidyKwhLimit: 130,
  subsidyPercent: 0,
  publicLighting: 0,
  adjustment: 0,
};

interface Data {
  bills: BillStatus[];
  readings: MeterReading[];
}

/**
 * Facturación según el recibo: el costo sale de las lecturas del medidor y de la tarifa del período,
 * con el subsidio aplicado solo a los primeros kWh.
 */
export default function BillingPanel() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data, error, reload } = useAsync<Data>(async () => {
    const [bills, readings] = await Promise.all([api.billingBills(), api.meterReadings()]);
    return { bills, readings };
  }, []);

  const [tariff, setTariff] = useState<TariffInput>(emptyTariff);
  const [reading, setReading] = useState({ readingKwh: '', readAt: '' });
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const run = async (action: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    setMessage('');
    try {
      await action();
      setMessage(ok);
      await reload();
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : 'Error inesperado');
    } finally {
      setBusy(false);
    }
  };

  const saveTariff = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      await api.createTariff(tariff);
      setTariff(emptyTariff);
    }, 'Tarifa guardada');
  };

  const saveReading = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      await api.addMeterReading({ readingKwh: Number(reading.readingKwh), readAt: reading.readAt });
      setReading({ readingKwh: '', readAt: '' });
    }, 'Lectura guardada');
  };

  return (
    <div style={{ marginTop: 24 }}>
      <SectionTitle
        title="Facturación de energía"
        subtitle="Recibos calculados con las lecturas del medidor y la tarifa de cada período"
      />

      {(error || message) && <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>}

      <div className="dashboard-grid">
        <Card>
          <SectionTitle title="Recibos" subtitle="Consumo, subsidio y total por período" />
          {data?.bills.length === 0 && <div className="muted">Aún no hay tarifas cargadas.</div>}
          {data?.bills.map((b) => (
            <div key={b.tariff.id} className="protected-row" style={{ alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>
                <strong>{b.tariff.periodStart} → {b.tariff.periodEnd}</strong>
                {b.missing ? (
                  <span style={{ color: 'var(--amber)' }}>{b.missing}</span>
                ) : (
                  <span>
                    {b.consumptionKwh} kWh · bruto {money(b.energyGross)} · subsidio −{money(b.subsidy)} ·
                    alumbrado {money(b.publicLighting)}
                    {b.adjustment ? ` · ajuste ${money(b.adjustment)}` : ''}
                  </span>
                )}
              </div>
              {b.total !== undefined && <Badge tone="green">Total {money(b.total)}</Badge>}
              {isAdmin && (
                <Button variant="ghost" size="icon" disabled={busy} onClick={() => {
                  if (window.confirm('¿Eliminar esta tarifa?')) run(() => api.deleteTariff(b.tariff.id), 'Tarifa eliminada');
                }}>
                  <Trash2 size={16} />
                </Button>
              )}
            </div>
          ))}
        </Card>

        <Card>
          <SectionTitle title="Lecturas del medidor" subtitle="Valor acumulado que aparece en el recibo (kWh)" />
          {data?.readings.length === 0 && <div className="muted">Aún no hay lecturas.</div>}
          {data?.readings.map((r) => (
            <div key={r.id} className="protected-row">
              <div style={{ flex: 1 }}>
                <strong>{r.readingKwh.toLocaleString('es-CO')} kWh</strong>
                <span>{r.readAt}</span>
              </div>
              {isAdmin && (
                <Button variant="ghost" size="icon" disabled={busy} onClick={() => run(() => api.deleteMeterReading(r.id), 'Lectura eliminada')}>
                  <Trash2 size={16} />
                </Button>
              )}
            </div>
          ))}
          {isAdmin && (
            <form onSubmit={saveReading} style={{ marginTop: 14 }}>
              <div className="settings-grid">
                <label className="field"><span>Lectura (kWh)</span><div><input type="number" min="0" step="0.001" value={reading.readingKwh} onChange={(e) => setReading({ ...reading, readingKwh: e.target.value })} required /></div></label>
                <label className="field"><span>Fecha de lectura</span><div><input type="date" value={reading.readAt} onChange={(e) => setReading({ ...reading, readAt: e.target.value })} required /></div></label>
              </div>
              <button type="submit" className="btn btn-primary btn-md" disabled={busy}><Plus size={16} /> Guardar lectura</button>
            </form>
          )}
        </Card>
      </div>

      {isAdmin && (
        <Card style={{ marginTop: 16 }}>
          <SectionTitle title="Nuevo período de facturación" subtitle="Datos tomados del recibo: el período va de una lectura a la siguiente" />
          <form onSubmit={saveTariff}>
            <div className="settings-grid">
              <label className="field"><span>Lectura inicial (fecha)</span><div><input type="date" value={tariff.periodStart} onChange={(e) => setTariff({ ...tariff, periodStart: e.target.value })} required /></div></label>
              <label className="field"><span>Lectura final (fecha)</span><div><input type="date" value={tariff.periodEnd} onChange={(e) => setTariff({ ...tariff, periodEnd: e.target.value })} required /></div></label>
              <label className="field"><span>Costo unitario CUv ($/kWh)</span><div><input type="number" min="0.01" step="0.01" value={tariff.unitPricePerKwh || ''} onChange={(e) => setTariff({ ...tariff, unitPricePerKwh: Number(e.target.value) })} required /></div></label>
            </div>
            <div className="settings-grid">
              <label className="field"><span>Subsidio: primeros kWh</span><div><input type="number" min="0" step="0.01" value={tariff.subsidyKwhLimit} onChange={(e) => setTariff({ ...tariff, subsidyKwhLimit: Number(e.target.value) })} /></div></label>
              <label className="field"><span>Subsidio (%)</span><div><input type="number" min="0" max="100" step="0.001" value={tariff.subsidyPercent} onChange={(e) => setTariff({ ...tariff, subsidyPercent: Number(e.target.value) })} /></div></label>
              <label className="field"><span>Alumbrado público ($)</span><div><input type="number" min="0" step="1" value={tariff.publicLighting} onChange={(e) => setTariff({ ...tariff, publicLighting: Number(e.target.value) })} /></div></label>
            </div>
            <label className="field"><span>Ajuste monetario ($)</span><div><input type="number" step="1" value={tariff.adjustment} onChange={(e) => setTariff({ ...tariff, adjustment: Number(e.target.value) })} /></div></label>
            <button type="submit" className="btn btn-primary btn-md" disabled={busy}><Plus size={16} /> Guardar período</button>
          </form>
        </Card>
      )}
    </div>
  );
}
