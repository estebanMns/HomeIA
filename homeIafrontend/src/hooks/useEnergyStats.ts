'use client';

import { useEffect, useState } from 'react';
import type { EnergyBucket } from '@/types';
import { computeStats, type StatsResult } from '@/lib/energyStatsRunner';

/** Estadísticas de la serie por hora, calculadas en el Web Worker cada vez que cambian los datos. */
export function useEnergyStats(buckets: EnergyBucket[] | undefined, timeZone?: string) {
  const [result, setResult] = useState<StatsResult | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!buckets) return;
    let cancelled = false;
    computeStats(buckets, timeZone, 'worker', 'Consumo y ahorro')
      .then((r) => {
        if (!cancelled) {
          setResult(r);
          setError('');
        }
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message);
      });
    return () => {
      cancelled = true;
    };
  }, [buckets, timeZone]);

  return { result, error };
}
