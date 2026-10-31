/**
 * Métricas de rendimiento de esta pestaña: Web Vitals, latencia de la API, long tasks, retraso del
 * event loop y tiempos de cálculo. Vive en memoria; la página Rendimiento lo lee con
 * useSyncExternalStore y PerformanceReporter envía al backend lo pendiente. Ver docs/RENDIMIENTO.md.
 */

export type VitalName = 'LCP' | 'INP' | 'CLS' | 'FCP' | 'TTFB';
export type MetricName = VitalName | 'API_LATENCY';
export type Rating = 'good' | 'needs-improvement' | 'poor';

export const VITAL_NAMES: VitalName[] = ['LCP', 'INP', 'CLS', 'FCP', 'TTFB'];

/** Umbrales oficiales de web.dev: hasta el primero es "bueno", después del segundo es "malo". */
export const THRESHOLDS: Record<MetricName, [number, number]> = {
  LCP: [2500, 4000],
  INP: [200, 500],
  CLS: [0.1, 0.25],
  FCP: [1800, 3000],
  TTFB: [800, 1800],
  // Propio de HomeIA: una respuesta de la API bajo 300 ms se siente instantánea.
  API_LATENCY: [300, 1000],
};

export function rate(name: MetricName, value: number): Rating {
  const [good, poor] = THRESHOLDS[name];
  return value <= good ? 'good' : value <= poor ? 'needs-improvement' : 'poor';
}

export interface MetricSample {
  name: MetricName;
  value: number;
  page: string;
}

export interface VitalValue {
  value: number;
  rating: Rating;
  page: string;
}

export interface ApiEndpointStat {
  endpoint: string;
  count: number;
  errors: number;
  avgMs: number;
  p95Ms: number;
}

/** Dónde corrió un cálculo: Web Worker, hilo principal de una vez, o hilo principal por bloques. */
export type ComputeThread = 'worker' | 'main' | 'main-chunked';

export interface Computation {
  label: string;
  thread: ComputeThread;
  ms: number;
  at: number;
}

export interface PerfSnapshot {
  vitals: Partial<Record<VitalName, VitalValue>>;
  api: ApiEndpointStat[];
  longTasks: { count: number; totalMs: number; maxMs: number };
  loopLag: { currentMs: number; maxMs: number };
  computations: Computation[];
}

const MAX_SAMPLES_PER_ENDPOINT = 200;
const MAX_PENDING = 300;
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;

const vitals: Partial<Record<VitalName, VitalValue>> = {};
const endpoints = new Map<string, { durations: number[]; count: number; errors: number }>();
const longTasks = { count: 0, totalMs: 0, maxMs: 0 };
const loopLag = { currentMs: 0, maxMs: 0 };
let computations: Computation[] = [];
let pending: MetricSample[] = [];

const listeners = new Set<() => void>();
let snapshot: PerfSnapshot = buildSnapshot();

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

function buildSnapshot(): PerfSnapshot {
  const api = [...endpoints.entries()]
    .map(([endpoint, e]) => {
      const sorted = [...e.durations].sort((a, b) => a - b);
      const avg = sorted.reduce((sum, d) => sum + d, 0) / (sorted.length || 1);
      return { endpoint, count: e.count, errors: e.errors, avgMs: Math.round(avg), p95Ms: Math.round(percentile(sorted, 95)) };
    })
    .sort((a, b) => b.p95Ms - a.p95Ms);
  return { vitals: { ...vitals }, api, longTasks: { ...longTasks }, loopLag: { ...loopLag }, computations };
}

function emit() {
  snapshot = buildSnapshot();
  listeners.forEach((listener) => listener());
}

function queue(sample: MetricSample) {
  pending.push(sample);
  if (pending.length > MAX_PENDING) pending = pending.slice(-MAX_PENDING);
}

/** "/devices/2f1c…/commands?x=1" → "/devices/{id}/commands": agrupa las llamadas al mismo endpoint. */
export function normalizeEndpoint(method: string, path: string): string {
  return `${method} ${path.split('?')[0].replace(UUID, '{id}')}`;
}

export const perfStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  getSnapshot: () => snapshot,

  recordVital(name: VitalName, value: number, page: string) {
    vitals[name] = { value, rating: rate(name, value), page };
    queue({ name, value, page });
    emit();
  },

  recordApiCall(method: string, path: string, ms: number, ok: boolean) {
    const key = normalizeEndpoint(method, path);
    const entry = endpoints.get(key) ?? { durations: [], count: 0, errors: 0 };
    entry.count++;
    if (!ok) entry.errors++;
    entry.durations.push(ms);
    if (entry.durations.length > MAX_SAMPLES_PER_ENDPOINT) entry.durations.shift();
    endpoints.set(key, entry);
    queue({ name: 'API_LATENCY', value: ms, page: key });
    emit();
  },

  recordLongTask(ms: number) {
    longTasks.count++;
    longTasks.totalMs += ms;
    longTasks.maxMs = Math.max(longTasks.maxMs, ms);
    emit();
  },

  recordLoopLag(ms: number) {
    loopLag.currentMs = ms;
    loopLag.maxMs = Math.max(loopLag.maxMs, ms);
    emit();
  },

  recordComputation(label: string, thread: Computation['thread'], ms: number) {
    computations = [{ label, thread, ms, at: Date.now() }, ...computations].slice(0, 10);
    emit();
  },

  /** Saca las muestras pendientes de enviar al backend. */
  takePending(): MetricSample[] {
    const out = pending;
    pending = [];
    return out;
  },

  /** Devuelve muestras que no se pudieron enviar, para reintentarlas en el próximo envío. */
  requeue(samples: MetricSample[]) {
    pending = [...samples, ...pending].slice(-MAX_PENDING);
  },
};
