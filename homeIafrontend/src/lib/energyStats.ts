import type { EnergyBucket } from '@/types';
import { processInChunks } from '@/lib/scheduling';

/**
 * Estadísticas de consumo calculadas en el navegador a partir de la serie por hora.
 * Son funciones puras: las usa el Web Worker (workers/energyStats.worker.ts) y, como respaldo o para
 * la demostración de la página Rendimiento, el hilo principal. Ver docs/RENDIMIENTO.md.
 */

export interface HourProfile {
  hour: number;
  avgWatts: number;
  samples: number;
}

export interface EnergyPoint {
  start: string;
  averageWatts: number;
}

export interface EnergyStats {
  points: number;
  meanWatts: number;
  stdWatts: number;
  /** Consumo promedio por hora del día (0–23), en la zona horaria de la casa. */
  hourlyProfile: HourProfile[];
  /** Las 5 horas con mayor potencia promedio. */
  peakHours: EnergyPoint[];
  /** Horas con puntaje z > 2: consumo muy por encima de lo normal (máximo 10). */
  anomalies: (EnergyPoint & { zScore: number })[];
  /** Media móvil de 24 h (la misma cantidad de puntos que la serie). */
  movingAverage: number[];
  /** Variación de la segunda mitad del período frente a la primera, en %. null si no hay datos. */
  trendPercent: number | null;
}

const ANOMALY_Z = 2;
const MOVING_WINDOW = 24;

function hourFormatter(timeZone?: string): Intl.DateTimeFormat {
  try {
    return new Intl.DateTimeFormat('en-US', { hour: 'numeric', hourCycle: 'h23', timeZone });
  } catch {
    return new Intl.DateTimeFormat('en-US', { hour: 'numeric', hourCycle: 'h23' });
  }
}

/** Hora local de un instante. Es la parte costosa: Intl resuelve la zona horaria de cada punto. */
function hourOf(formatter: Intl.DateTimeFormat, iso: string): number {
  const part = formatter.formatToParts(new Date(iso)).find((p) => p.type === 'hour');
  return part ? Number(part.value) % 24 : 0;
}

/** Fase 2: con la hora de cada punto ya resuelta, calcula todas las estadísticas. */
function summarize(buckets: readonly EnergyBucket[], hours: readonly number[]): EnergyStats {
  const n = buckets.length;
  const watts = buckets.map((b) => b.averageWatts);
  const mean = n ? watts.reduce((s, w) => s + w, 0) / n : 0;
  const std = n ? Math.sqrt(watts.reduce((s, w) => s + (w - mean) ** 2, 0) / n) : 0;

  const sums = new Array<number>(24).fill(0);
  const counts = new Array<number>(24).fill(0);
  hours.forEach((h, i) => {
    sums[h] += watts[i];
    counts[h]++;
  });
  const hourlyProfile = sums.map((sum, hour) => ({
    hour,
    avgWatts: counts[hour] ? Math.round(sum / counts[hour]) : 0,
    samples: counts[hour],
  }));

  const byWatts = buckets
    .map((b) => ({ start: b.start, averageWatts: Math.round(b.averageWatts) }))
    .sort((a, b) => b.averageWatts - a.averageWatts);

  const anomalies = std > 0
    ? byWatts
      .map((p) => ({ ...p, zScore: Math.round(((p.averageWatts - mean) / std) * 10) / 10 }))
      .filter((p) => p.zScore > ANOMALY_Z)
      .slice(0, 10)
    : [];

  const movingAverage: number[] = [];
  let windowSum = 0;
  watts.forEach((w, i) => {
    windowSum += w;
    if (i >= MOVING_WINDOW) windowSum -= watts[i - MOVING_WINDOW];
    movingAverage.push(Math.round(windowSum / Math.min(i + 1, MOVING_WINDOW)));
  });

  let trendPercent: number | null = null;
  if (n >= 2) {
    const half = Math.floor(n / 2);
    const first = watts.slice(0, half).reduce((s, w) => s + w, 0) / half;
    const second = watts.slice(half).reduce((s, w) => s + w, 0) / (n - half);
    trendPercent = first > 0 ? Math.round(((second - first) / first) * 1000) / 10 : null;
  }

  return {
    points: n,
    meanWatts: Math.round(mean),
    stdWatts: Math.round(std),
    hourlyProfile,
    peakHours: byWatts.slice(0, 5),
    anomalies,
    movingAverage,
    trendPercent,
  };
}

/** Cálculo completo de una sola vez. En el hilo principal bloquea mientras dura. */
export function computeEnergyStats(buckets: readonly EnergyBucket[], timeZone?: string): EnergyStats {
  const formatter = hourFormatter(timeZone);
  return summarize(buckets, buckets.map((b) => hourOf(formatter, b.start)));
}

/**
 * Mismo cálculo en el hilo principal, pero la parte costosa va en bloques que ceden el hilo entre sí
 * (yieldToMain): tarda un poco más en total, pero la interfaz sigue respondiendo.
 */
export async function computeEnergyStatsInChunks(buckets: readonly EnergyBucket[], timeZone?: string): Promise<EnergyStats> {
  const formatter = hourFormatter(timeZone);
  const hours = new Array<number>(buckets.length);
  await processInChunks(buckets, (b, i) => {
    hours[i] = hourOf(formatter, b.start);
  }, 2000);
  return summarize(buckets, hours);
}

/** Serie sintética por hora para la demostración: patrón diario con ruido y algunos picos. */
export function syntheticBuckets(count: number): EnergyBucket[] {
  const start = Date.now() - count * 3600_000;
  return Array.from({ length: count }, (_, i) => {
    const hour = new Date(start + i * 3600_000).getHours();
    const daily = 300 + 900 * Math.max(0, Math.sin(((hour - 6) / 24) * 2 * Math.PI));
    const spike = i % 997 === 0 ? 2500 : 0;
    const averageWatts = Math.max(0, daily + spike + ((i * 7919) % 200) - 100);
    return { start: new Date(start + i * 3600_000).toISOString(), kwh: averageWatts / 1000, cost: 0, averageWatts };
  });
}
