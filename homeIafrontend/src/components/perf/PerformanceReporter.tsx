'use client';

import { useEffect } from 'react';
import { useReportWebVitals } from 'next/web-vitals';
import { perfStore, VITAL_NAMES, type VitalName } from '@/lib/perfStore';
import { whenIdle } from '@/lib/scheduling';
import { sendPerformanceMetrics } from '@/services/api';

/** Lote máximo por envío: el backend acepta hasta 50 y keepalive limita el cuerpo a 64 KB. */
const BATCH_SIZE = 50;
const FLUSH_EVERY_MS = 15000;
const LAG_PROBE_MS = 500;

/**
 * Recolecta las métricas de rendimiento de toda la app. No pinta nada.
 * - Web Vitals (LCP, INP, CLS, FCP, TTFB) con useReportWebVitals.
 * - Long tasks: tareas de más de 50 ms en el hilo principal (PerformanceObserver).
 * - Retraso del event loop: cuánto se atrasa un temporizador de 500 ms.
 * Cada 15 s, cuando el navegador está libre, y al ocultar la pestaña, envía lo pendiente al backend.
 */
export default function PerformanceReporter() {
  useReportWebVitals((metric) => {
    if ((VITAL_NAMES as string[]).includes(metric.name)) {
      perfStore.recordVital(metric.name as VitalName, metric.value, window.location.pathname);
    }
  });

  useEffect(() => {
    const stopLongTasks = observeLongTasks();
    const stopLagProbe = probeLoopLag();

    let cancelIdle = () => {};
    const flush = async () => {
      const samples = perfStore.takePending();
      for (let i = 0; i < samples.length; i += BATCH_SIZE) {
        const batch = samples.slice(i, i + BATCH_SIZE);
        if (!(await sendPerformanceMetrics(batch))) {
          perfStore.requeue(samples.slice(i)); // sin sesión o sin conexión: se reintenta después
          return;
        }
      }
    };
    const timer = setInterval(() => {
      cancelIdle();
      cancelIdle = whenIdle(flush);
    }, FLUSH_EVERY_MS);
    // Al ocultar la pestaña puede que no haya otra oportunidad: se envía sin esperar.
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') flush();
    };
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      stopLongTasks();
      stopLagProbe();
      cancelIdle();
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, []);

  return null;
}

function observeLongTasks(): () => void {
  if (typeof PerformanceObserver === 'undefined' || !PerformanceObserver.supportedEntryTypes?.includes('longtask')) {
    return () => {}; // Firefox y Safari no exponen long tasks
  }
  const observer = new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) perfStore.recordLongTask(entry.duration);
  });
  observer.observe({ type: 'longtask', buffered: true });
  return () => observer.disconnect();
}

/** Si el hilo está ocupado, el temporizador se ejecuta tarde: ese atraso es el retraso del event loop. */
function probeLoopLag(): () => void {
  let timer: ReturnType<typeof setTimeout>;
  let expected = performance.now() + LAG_PROBE_MS;
  const tick = () => {
    const now = performance.now();
    // Con la pestaña oculta el navegador frena los temporizadores a propósito: no es retraso real.
    if (!document.hidden) perfStore.recordLoopLag(Math.max(0, Math.round(now - expected)));
    expected = performance.now() + LAG_PROBE_MS;
    timer = setTimeout(tick, LAG_PROBE_MS);
  };
  timer = setTimeout(tick, LAG_PROBE_MS);
  return () => clearTimeout(timer);
}
