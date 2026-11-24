import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';

/** Carga datos asíncronos y re-ejecuta cuando cambian las dependencias. Ignora respuestas obsoletas. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const seq = useRef(0);

  const run = useCallback(() => {
    const id = ++seq.current;
    setLoading(true);
    setError(null);
    fn()
      .then((d) => { if (id === seq.current) setData(d); })
      .catch((e: unknown) => {
        if (id === seq.current) setError(e instanceof ApiError ? e.message : 'Error inesperado');
      })
      .finally(() => { if (id === seq.current) setLoading(false); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { run(); }, [run]);

  return { data, error, loading, reload: run, setData };
}

/** Retrasa la actualización de un valor (para búsquedas mientras se escribe). */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}
