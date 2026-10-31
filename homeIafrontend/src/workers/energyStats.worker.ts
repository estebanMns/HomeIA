/**
 * Web Worker: calcula las estadísticas de energía en un hilo aparte, así el hilo principal
 * (render, clics, scroll) no se bloquea aunque la serie sea grande. Se comunica solo por mensajes:
 * recibe { id, buckets, timeZone } y responde { id, stats, ms } o { id, error }.
 */
import { computeEnergyStats } from '@/lib/energyStats';
import type { EnergyBucket } from '@/types';

interface Request {
  id: number;
  buckets: EnergyBucket[];
  timeZone?: string;
}

self.onmessage = (event: MessageEvent<Request>) => {
  const { id, buckets, timeZone } = event.data;
  const start = performance.now();
  try {
    const stats = computeEnergyStats(buckets, timeZone);
    self.postMessage({ id, stats, ms: performance.now() - start });
  } catch (e) {
    self.postMessage({ id, error: e instanceof Error ? e.message : 'Error en el worker' });
  }
};
