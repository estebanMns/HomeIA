/**
 * Web Worker: calcula las estadísticas de energía en un hilo aparte, así el hilo principal
 * (render, clics, scroll) no se bloquea aunque la serie sea grande. Se comunica solo por mensajes:
 * recibe { id, buckets | series, timeZone } y responde { id, stats, ms } o { id, error }.
 */
import { bucketsFromSeries, computeEnergyStats, type EnergySeries } from '@/lib/energyStats';
import type { EnergyBucket } from '@/types';

/** Llegan buckets (series pequeñas) o una serie en columnas transferida sin copiar (series grandes). */
interface Request {
  id: number;
  buckets?: EnergyBucket[];
  series?: EnergySeries;
  timeZone?: string;
}

self.onmessage = (event: MessageEvent<Request>) => {
  const { id, buckets, series, timeZone } = event.data;
  const start = performance.now();
  try {
    const stats = computeEnergyStats(buckets ?? bucketsFromSeries(series!), timeZone);
    self.postMessage({ id, stats, ms: performance.now() - start });
  } catch (e) {
    self.postMessage({ id, error: e instanceof Error ? e.message : 'Error en el worker' });
  }
};
