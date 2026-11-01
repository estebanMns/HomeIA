'use client';

import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Cpu, Gauge, Layers, Timer } from 'lucide-react';
import { api } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { perfStore, rate, THRESHOLDS, VITAL_NAMES, type ComputeThread, type Rating, type VitalName } from '@/lib/perfStore';
import { syntheticSeries, type EnergySeries } from '@/lib/energyStats';
import { computeStats, workersSupported } from '@/lib/energyStatsRunner';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';

const DEMO_POINTS = 200_000;

const VITAL_INFO: Record<VitalName, string> = {
  LCP: 'Largest Contentful Paint: cuándo se pinta el elemento principal',
  INP: 'Interaction to Next Paint: cuánto tarda la página en responder a un clic o tecla',
  CLS: 'Cumulative Layout Shift: cuánto se mueve el contenido mientras carga',
  FCP: 'First Contentful Paint: cuándo aparece el primer contenido',
  TTFB: 'Time to First Byte: cuánto tarda el servidor en empezar a responder',
};

const THREAD_LABEL: Record<ComputeThread, string> = {
  worker: 'Web Worker',
  main: 'Hilo principal (de una vez)',
  'main-chunked': 'Hilo principal (por bloques)',
};

/** Patrón de rendering de cada ruta, según la salida de `next build` (ver docs/RENDIMIENTO.md). */
const RENDERING: { route: string; pattern: string; why: string }[] = [
  { route: '/auth/login, /auth/register', pattern: 'SSG + Server Component + isla de cliente', why: 'El panel de marca es estático y se genera en el build; solo el formulario envía JavaScript.' },
  { route: '/auth/forgot, /auth/reset, /setup', pattern: 'SSG (shell estático) + CSR', why: 'El HTML se genera en el build; el formulario se activa en el navegador.' },
  { route: '/dashboard/*', pattern: 'Shell estático + CSR + streaming (loading.tsx)', why: 'Los datos son privados y el token vive en el navegador: se piden desde el cliente. El esqueleto llega al instante.' },
  { route: '/dashboard/[section]', pattern: 'Partial Prerender (PPR)', why: 'Ruta dinámica: el esqueleto es estático y el resto se completa al recibir la petición.' },
  { route: 'Panel de facturación', pattern: 'Code splitting (next/dynamic)', why: 'Se descarga en un archivo aparte, después de lo visible.' },
  { route: 'Todas', pattern: 'Proxy en el servidor (src/proxy.ts)', why: 'Sin cookie de sesión, /dashboard redirige al login antes de renderizar.' },
];

const noSubscribe = () => () => {};

/**
 * Capacidades del navegador. En el servidor (Node) no hay Worker y la respuesta sería otra:
 * useSyncExternalStore usa false al renderizar en el servidor y el valor real en el navegador,
 * sin error de hidratación.
 */
function useBrowserSupport() {
  const worker = useSyncExternalStore(noSubscribe, workersSupported, () => false);
  const longTasks = useSyncExternalStore(
    noSubscribe,
    () => typeof PerformanceObserver !== 'undefined' && !!PerformanceObserver.supportedEntryTypes?.includes('longtask'),
    () => true,
  );
  return { worker, longTasks };
}

/** Mide el frame más largo mientras corre `work`: es lo que el usuario percibe como congelamiento. */
async function measureLongestFrame<T>(work: () => Promise<T>): Promise<{ result: T; longestFrameMs: number }> {
  let longest = 0;
  let last = performance.now();
  let running = true;
  const loop = () => {
    const now = performance.now();
    longest = Math.max(longest, now - last);
    last = now;
    if (running) requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
  const result = await work();
  // Un frame más para cerrar la medición del último bloqueo.
  await new Promise((resolve) => requestAnimationFrame(resolve));
  running = false;
  return { result, longestFrameMs: Math.round(longest) };
}

function formatValue(name: string, value: number): string {
  if (name === 'CLS') return value.toFixed(3);
  return value >= 1000 ? `${(value / 1000).toFixed(2)} s` : `${Math.round(value)} ms`;
}

function RatingBadge({ rating }: { rating: Rating | null | undefined }) {
  if (!rating) return <Badge tone="gray">sin datos</Badge>;
  const tone = rating === 'good' ? 'green' : rating === 'needs-improvement' ? 'amber' : 'red';
  const label = rating === 'good' ? 'Bueno' : rating === 'needs-improvement' ? 'Mejorable' : 'Malo';
  return <Badge tone={tone}>{label}</Badge>;
}

/**
 * Barra movida con requestAnimationFrame, que corre en el hilo principal: si el hilo se bloquea,
 * la barra se congela. (Una animación CSS no serviría: la ejecuta el compositor en otro hilo.)
 */
function MainThreadTicker() {
  const bar = useRef<HTMLElement>(null);
  useEffect(() => {
    let frame = 0;
    const start = performance.now();
    const loop = (now: number) => {
      const progress = ((now - start) / 1500) % 1;
      if (bar.current) bar.current.style.transform = `translateX(${progress * 460}%)`;
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(frame);
  }, []);
  return (
    <div className="perf-ticker" aria-hidden="true">
      <i ref={bar} />
    </div>
  );
}

export default function PerformancePage() {
  const perf = useSyncExternalStore(perfStore.subscribe, perfStore.getSnapshot, perfStore.getServerSnapshot);
  const { data: summary, error } = useAsync(() => api.performanceSummary(), [], 15000);

  const support = useBrowserSupport();
  const demoData = useRef<EnergySeries | null>(null);
  const [running, setRunning] = useState<ComputeThread | null>(null);
  const [demo, setDemo] = useState<{
    thread: ComputeThread;
    computeMs: number;
    totalMs: number;
    longestFrameMs: number;
    longTasks: number;
  } | null>(null);
  const [clicks, setClicks] = useState(0);

  const runDemo = async (thread: ComputeThread) => {
    setRunning(thread);
    setDemo(null);
    // Se deja pintar el estado "calculando…" antes de empezar.
    await new Promise((resolve) => requestAnimationFrame(() => setTimeout(resolve, 0)));
    demoData.current ??= syntheticSeries(DEMO_POINTS);
    // El worker se queda con la memoria transferida: cada ejecución recibe una copia (memcpy de ~3 MB).
    const series = { starts: demoData.current.starts.slice(), watts: demoData.current.watts.slice() };
    const longTasksBefore = perfStore.getSnapshot().longTasks.count;
    const start = performance.now();
    const { result, longestFrameMs } = await measureLongestFrame(() =>
      computeStats(series, 'America/Bogota', thread, 'Demostración'));
    const totalMs = performance.now() - start;
    // Las long tasks se reportan un instante después de terminar.
    setTimeout(() => {
      setDemo({
        thread: result.thread,
        computeMs: Math.round(result.ms),
        totalMs: Math.round(totalMs),
        longestFrameMs,
        longTasks: perfStore.getSnapshot().longTasks.count - longTasksBefore,
      });
      setRunning(null);
    }, 150);
  };

  const backend = new Map((summary?.metrics ?? []).map((m) => [m.name, m]));
  const apiLatency = backend.get('API_LATENCY');

  return (
    <div className="page-content">
      <SectionTitle
        title="Rendimiento"
        subtitle="Métricas reales de esta pestaña y de todos los navegadores de la casa. Ver docs/RENDIMIENTO.md"
      />
      {error && <Card style={{ color: 'var(--red)', marginBottom: 16 }}>{error}</Card>}

      <Card style={{ marginBottom: 16 }}>
        <SectionTitle
          title="Core Web Vitals"
          subtitle="Tu sesión (último valor) y el p75 de la casa (el percentil que usa Google). Aparecen al navegar e interactuar."
          action={<Gauge size={18} />}
        />
        <div className="kpi-grid">
          {VITAL_NAMES.map((name) => {
            const mine = perf.vitals[name];
            const house = backend.get(name);
            return (
              <Card key={name} className="kpi-card">
                <div className="kpi-title" title={VITAL_INFO[name]}>{name}</div>
                <div className={`kpi-value perf-rating-${mine?.rating ?? 'none'}`} style={{ fontSize: 22 }}>
                  {mine ? formatValue(name, mine.value) : '—'}
                </div>
                <div className="kpi-note">
                  Casa p75: {house && house.count > 0 ? `${formatValue(name, house.p75)} (${house.count})` : '—'}{' '}
                  <RatingBadge rating={house?.rating} />
                </div>
                <div className="kpi-note">Bueno ≤ {formatValue(name, THRESHOLDS[name][0])} · Malo &gt; {formatValue(name, THRESHOLDS[name][1])}</div>
              </Card>
            );
          })}
        </div>
      </Card>

      <div className="dashboard-grid">
        <Card>
          <SectionTitle
            title="Latencia de la API"
            subtitle={`Medida con performance.now() en cada llamada · casa p75: ${apiLatency && apiLatency.count > 0 ? formatValue('API', apiLatency.p75) : '—'}`}
            action={<Timer size={18} />}
          />
          {perf.api.length === 0 && <div className="muted">Aún no hay llamadas medidas.</div>}
          {perf.api.slice(0, 12).map((e) => (
            <div key={e.endpoint} className="cost-row">
              <span style={{ fontFamily: 'var(--font-geist-mono)', fontSize: 12 }}>{e.endpoint}</span>
              <strong className={`perf-rating-${rate('API_LATENCY', e.p95Ms)}`}>
                {e.count}× · prom {e.avgMs} ms · p95 {e.p95Ms} ms{e.errors ? ` · ${e.errors} error(es)` : ''}
              </strong>
            </div>
          ))}
        </Card>

        <Card>
          <SectionTitle title="Event loop" subtitle="Cuánto se atrasa el hilo principal y cuántas tareas largas (> 50 ms) hubo" action={<Layers size={18} />} />
          <div className="cost-row"><span>Retraso actual del event loop</span><strong className={perf.loopLag.currentMs > 50 ? 'perf-rating-poor' : 'perf-rating-good'}>{perf.loopLag.currentMs} ms</strong></div>
          <div className="cost-row"><span>Retraso máximo en esta sesión</span><strong>{perf.loopLag.maxMs} ms</strong></div>
          <div className="cost-row"><span>Long tasks detectadas</span><strong>{perf.longTasks.count}</strong></div>
          <div className="cost-row"><span>Long task más larga</span><strong>{Math.round(perf.longTasks.maxMs)} ms</strong></div>
          <div className="cost-row"><span>Tiempo total bloqueado</span><strong>{Math.round(perf.longTasks.totalMs)} ms</strong></div>
          {!support.longTasks && <div className="muted">Este navegador no expone long tasks (solo Chrome y Edge).</div>}
        </Card>
      </div>

      <Card style={{ marginTop: 16 }}>
        <SectionTitle
          title="Demostración: Web Worker frente al hilo principal"
          subtitle={`El mismo cálculo de estadísticas sobre ${DEMO_POINTS.toLocaleString('es-CO')} lecturas sintéticas. Mira la barra y prueba el contador mientras calcula.`}
          action={<Cpu size={18} />}
        />
        <MainThreadTicker />
        <div className="quick-actions" style={{ marginTop: 14, flexWrap: 'wrap' }}>
          <Button variant="danger" disabled={running !== null} onClick={() => runDemo('main')}>
            {running === 'main' ? 'Calculando…' : 'Hilo principal (bloquea)'}
          </Button>
          <Button variant="outline" disabled={running !== null} onClick={() => runDemo('main-chunked')}>
            {running === 'main-chunked' ? 'Calculando…' : 'Hilo principal por bloques'}
          </Button>
          <Button variant="primary" disabled={running !== null || !support.worker} onClick={() => runDemo('worker')}>
            {running === 'worker' ? 'Calculando…' : 'Web Worker'}
          </Button>
          <Button variant="ghost" onClick={() => setClicks((c) => c + 1)}>Contador: {clicks}</Button>
        </div>
        {demo && (
          <div style={{ marginTop: 14 }}>
            <div className="cost-row"><span>Dónde corrió</span><strong>{THREAD_LABEL[demo.thread]}</strong></div>
            <div className="cost-row"><span>Tiempo de cálculo</span><strong>{demo.computeMs} ms</strong></div>
            <div className="cost-row"><span>Duración de punta a punta</span><strong>{demo.totalMs} ms</strong></div>
            <div className="cost-row">
              <span>Frame más largo (la interfaz congelada)</span>
              <strong className={demo.longestFrameMs > 100 ? 'perf-rating-poor' : 'perf-rating-good'}>{demo.longestFrameMs} ms</strong>
            </div>
            <div className="cost-row">
              <span>Long tasks durante el cálculo</span>
              <strong className={demo.longTasks > 0 ? 'perf-rating-poor' : 'perf-rating-good'}>{demo.longTasks}</strong>
            </div>
          </div>
        )}
        {perf.computations.length > 0 && (
          <>
            <div className="section-heading" style={{ marginTop: 14 }}>Últimos cálculos</div>
            {perf.computations.map((c) => (
              <div key={c.at + c.label + c.thread} className="cost-row">
                <span>{c.label} · {THREAD_LABEL[c.thread]}</span>
                <strong>{c.ms} ms</strong>
              </div>
            ))}
          </>
        )}
      </Card>

      <Card style={{ marginTop: 16 }}>
        <SectionTitle title="Patrones de rendering" subtitle="Qué estrategia usa cada parte de la app y por qué" />
        {RENDERING.map((r) => (
          <div key={r.route} className="protected-row" style={{ alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <strong>{r.route}</strong>
              <span>{r.why}</span>
            </div>
            <Badge tone="blue">{r.pattern}</Badge>
          </div>
        ))}
      </Card>
    </div>
  );
}
