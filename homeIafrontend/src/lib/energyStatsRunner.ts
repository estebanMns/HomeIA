import type { EnergyBucket } from '@/types';
import { computeEnergyStats, computeEnergyStatsInChunks, type EnergyStats } from '@/lib/energyStats';
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

function inWorker(buckets: EnergyBucket[], timeZone?: string): Promise<{ stats: EnergyStats; ms: number }> {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    // postMessage copia los datos (structured clone): el worker no comparte memoria con la página.
    getWorker().postMessage({ id, buckets, timeZone });
  });
}

/**
 * Calcula las estadísticas en el modo pedido y registra el tiempo en perfStore.
 * - 'worker': en el Web Worker; si el navegador no tiene workers, cae a 'main-chunked'.
 * - 'main': en el hilo principal de una sola vez (bloquea la interfaz mientras dura).
 * - 'main-chunked': en el hilo principal por bloques, cediendo el hilo entre bloques.
 */
export async function computeStats(
  buckets: EnergyBucket[],
  timeZone: string | undefined,
  mode: ComputeThread,
  label: string,
): Promise<StatsResult> {
  const thread: ComputeThread = mode === 'worker' && !workersSupported() ? 'main-chunked' : mode;
  let result: { stats: EnergyStats; ms: number };
  if (thread === 'worker') {
    result = await inWorker(buckets, timeZone);
  } else {
    const start = performance.now();
    const stats = thread === 'main'
      ? computeEnergyStats(buckets, timeZone)
      : await computeEnergyStatsInChunks(buckets, timeZone);
    result = { stats, ms: performance.now() - start };
  }
  perfStore.recordComputation(`${label} (${buckets.length.toLocaleString('es-CO')} puntos)`, thread, Math.round(result.ms));
  return { ...result, thread };
}
