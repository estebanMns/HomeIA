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

  const reload = useCallback(async () => {
    try {
      setData(await loaderRef.current());
      setError('');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error desconocido');
    } finally {
      setLoading(false);
    }
  }, []);

  // Los filtros llegan como valores simples u objetos planos: compararlos serializados basta.
  const depsKey = JSON.stringify(deps);

  useEffect(() => {
    reload();
    if (!intervalMs) return;
    const id = setInterval(reload, intervalMs);
    return () => clearInterval(id);
  }, [reload, intervalMs, depsKey]);

  return { data, error, loading, reload };
}
