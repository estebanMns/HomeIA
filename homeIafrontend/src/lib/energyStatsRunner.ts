import type { EnergyBucket } from '@/types';
import {
  bucketsFromSeries,
  bucketsFromSeriesInChunks,
  computeEnergyStats,
  computeEnergyStatsInChunks,
  type EnergySeries,
  type EnergyStats,
} from '@/lib/energyStats';
import { perfStore, type ComputeThread } from '@/lib/perfStore';

export interface StatsResult {
  stats: EnergyStats;
  ms: number;
  thread: ComputeThread;
}

type Pending = { resolve: (r: { stats: EnergyStats; ms: number }) => void; reject: (e: Error) => void };

let worker: Worker | null = null;
let nextId = 0;
const pending = new Map<number, Pending>();

export function workersSupported(): boolean {
  return typeof Worker !== 'undefined';
}

/** Un solo worker para toda la app, creado al primer uso. Si falla, se descarta y se crea otro. */
function getWorker(): Worker {
  if (worker) return worker;
  const created = new Worker(new URL('../workers/energyStats.worker.ts', import.meta.url));
  created.onmessage = (event: MessageEvent<{ id: number; stats?: EnergyStats; ms?: number; error?: string }>) => {
    const { id, stats, ms, error } = event.data;
    const request = pending.get(id);
    if (!request) return;
    pending.delete(id);
    if (error || !stats) request.reject(new Error(error ?? 'El worker no devolvió resultados'));
    else request.resolve({ stats, ms: ms ?? 0 });
  };
  created.onerror = (event) => {
    pending.forEach((request) => request.reject(new Error(event.message || 'Error en el Web Worker')));
    pending.clear();
    created.terminate();
    worker = null;
  };
  worker = created;
  return created;
}

function inWorker(data: EnergyBucket[] | EnergySeries, timeZone?: string): Promise<{ stats: EnergyStats; ms: number }> {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    if (Array.isArray(data)) {
      // Los objetos se copian (structured clone): barato para series pequeñas como las de la app.
      getWorker().postMessage({ id, buckets: data, timeZone });
    } else {
      // Los Float64Array se transfieren: la memoria pasa al worker sin copiarse y aquí queda vacía.
      getWorker().postMessage({ id, series: data, timeZone }, [data.starts.buffer, data.watts.buffer]);
    }
  });
}

/**
 * Calcula las estadísticas en el modo pedido y registra el tiempo en perfStore.
 * - 'worker': en el Web Worker; si el navegador no tiene workers, cae a 'main-chunked'.
 * - 'main': en el hilo principal de una sola vez (bloquea la interfaz mientras dura).
 * - 'main-chunked': en el hilo principal por bloques, cediendo el hilo entre bloques.
 */
export async function computeStats(
  data: EnergyBucket[] | EnergySeries,
  timeZone: string | undefined,
  mode: ComputeThread,
  label: string,
): Promise<StatsResult> {
  const thread: ComputeThread = mode === 'worker' && !workersSupported() ? 'main-chunked' : mode;
  const points = Array.isArray(data) ? data.length : data.starts.length;
  let result: { stats: EnergyStats; ms: number };
  if (thread === 'worker') {
    result = await inWorker(data, timeZone);
  } else {
    const start = performance.now();
    let stats: EnergyStats;
    if (thread === 'main') {
      stats = computeEnergyStats(Array.isArray(data) ? data : bucketsFromSeries(data), timeZone);
    } else {
      const buckets = Array.isArray(data) ? data : await bucketsFromSeriesInChunks(data);
      stats = await computeEnergyStatsInChunks(buckets, timeZone);
    }
    result = { stats, ms: performance.now() - start };
  }
  perfStore.recordComputation(`${label} (${points.toLocaleString('es-CO')} puntos)`, thread, Math.round(result.ms));
  return { ...result, thread };
}
