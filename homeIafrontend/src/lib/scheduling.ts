/**
 * Utilidades para trabajar con el event loop del navegador sin congelar la interfaz.
 * El hilo principal ejecuta una tarea a la vez; el navegador solo pinta y atiende clics entre tareas.
 * Una tarea de más de 50 ms es una "long task": el usuario la nota. Ver docs/RENDIMIENTO.md.
 */

interface SchedulerWithYield {
  yield: () => Promise<void>;
}

/**
 * Cede el hilo principal para que el navegador pinte y procese eventos pendientes, y luego continúa.
 * scheduler.yield() reanuda con prioridad; si no existe, un MessageChannel encola una tarea nueva
 * sin el retraso mínimo de ~4 ms que aplica setTimeout(0) cuando se anida.
 */
export function yieldToMain(): Promise<void> {
  const scheduler = (globalThis as { scheduler?: SchedulerWithYield }).scheduler;
  if (scheduler && typeof scheduler.yield === 'function') {
    return scheduler.yield();
  }
  if (typeof MessageChannel !== 'undefined') {
    return new Promise((resolve) => {
      const channel = new MessageChannel();
      channel.port1.onmessage = () => {
        channel.port1.close();
        resolve();
      };
      channel.port2.postMessage(null);
    });
  }
  return new Promise((resolve) => setTimeout(resolve, 0));
}

/**
 * Ejecuta trabajo no urgente cuando el navegador está libre (requestIdleCallback).
 * timeoutMs garantiza que se ejecute aunque el navegador nunca quede libre.
 * Devuelve una función para cancelarlo.
 */
export function whenIdle(callback: () => void, timeoutMs = 2000): () => void {
  if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
    const id = window.requestIdleCallback(callback, { timeout: timeoutMs });
    return () => window.cancelIdleCallback(id);
  }
  const id = setTimeout(callback, 1);
  return () => clearTimeout(id);
}

/**
 * Procesa una lista grande en bloques y cede el hilo entre bloques, para que ninguna tarea
 * pase de ~50 ms y la interfaz siga respondiendo.
 */
export async function processInChunks<T>(items: readonly T[], fn: (item: T, index: number) => void, chunkSize = 2000) {
  for (let start = 0; start < items.length; start += chunkSize) {
    const end = Math.min(start + chunkSize, items.length);
    for (let i = start; i < end; i++) fn(items[i], i);
    if (end < items.length) await yieldToMain();
  }
}
