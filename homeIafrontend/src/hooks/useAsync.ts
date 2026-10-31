'use client';

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';

interface AsyncState<T> {
  data: T | null;
  error: string;
  loading: boolean;
  reload: () => Promise<void>;
}

/**
 * Carga datos al montar, cuando cambia algún valor de `deps` y, si se indica, cada `intervalMs`.
 * Devuelve el error del backend tal cual.
 *
 * Sobre el event loop:
 * - El refresco no usa setInterval: la siguiente consulta se programa cuando termina la anterior,
 *   así un backend lento (Gemini tarda decenas de segundos) no acumula peticiones en cola.
 * - Con la pestaña oculta no se consulta; al volver se recarga de inmediato.
 * - Si llegan respuestas fuera de orden (un filtro viejo después de uno nuevo), solo cuenta la última.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = [], intervalMs?: number): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  // El loader se redefine en cada render; se guarda el último para que reload sea estable.
  const loaderRef = useRef(loader);
  useLayoutEffect(() => {
    loaderRef.current = loader;
  });

  // Cada petición recibe un número; solo la más reciente puede actualizar el estado.
  const latestRequest = useRef(0);

  const reload = useCallback(async () => {
    const id = ++latestRequest.current;
    try {
      const result = await loaderRef.current();
      if (id !== latestRequest.current) return;
      setData(result);
      setError('');
    } catch (e) {
      if (id !== latestRequest.current) return;
      setError(e instanceof Error ? e.message : 'Error desconocido');
    } finally {
      if (id === latestRequest.current) setLoading(false);
    }
  }, []);

  // Los filtros llegan como valores simples u objetos planos: compararlos serializados basta.
  const depsKey = JSON.stringify(deps);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const schedule = () => {
      if (!intervalMs || cancelled || document.hidden) return;
      timer = setTimeout(tick, intervalMs);
    };
    async function tick() {
      await reload();
      schedule();
    }
    const onVisibilityChange = () => {
      clearTimeout(timer);
      if (!document.hidden) tick();
    };

    tick();
    if (intervalMs) document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      cancelled = true;
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [reload, intervalMs, depsKey]);

  return { data, error, loading, reload };
}
