# Rendimiento en HomeIA: Web Workers, rendering, event loop y métricas

Este documento explica cuatro técnicas de rendimiento web y dónde está cada una en el código de HomeIA. Todas se pueden ver funcionando en la app, en **Administración → Rendimiento** (`/dashboard/performance`).

## Contenido

1. [Event loop](#1-event-loop)
2. [Web Workers](#2-web-workers)
3. [Patrones de rendering](#3-patrones-de-rendering)
4. [Métricas de rendimiento](#4-métricas-de-rendimiento)
5. [Resultados medidos](#5-resultados-medidos)

---

## 1. Event loop

### Qué es

El navegador ejecuta JavaScript, pinta la pantalla y atiende clics y teclas en **un solo hilo**, el hilo principal. Lo organiza el *event loop* con un ciclo que se repite:

1. **Tarea:** toma una de la cola (un evento, un `setTimeout`, la respuesta de un `fetch`) y la ejecuta completa.
2. **Microtareas:** ejecuta todas las pendientes, como las continuaciones de una `Promise` o de un `await`.
3. **Render:** si hace falta, corre los `requestAnimationFrame`, calcula estilos y diseño, y pinta.

Mientras una tarea se ejecuta, **nada más ocurre**: la página no pinta ni responde. Una tarea de más de **50 ms** es una *long task*, y el usuario la nota como un congelamiento.

### Dónde está en HomeIA

| Técnica | Archivo | Qué hace |
|---|---|---|
| `yieldToMain()` | [lib/scheduling.ts](../homeIafrontend/src/lib/scheduling.ts) | Cede el hilo para que el navegador pinte y atienda eventos, y luego continúa. Usa `scheduler.yield()` si existe; si no, un `MessageChannel`, que encola una tarea nueva sin el retraso mínimo de ~4 ms de `setTimeout(0)`. |
| `processInChunks()` | [lib/scheduling.ts](../homeIafrontend/src/lib/scheduling.ts) | Procesa listas grandes en bloques y llama a `yieldToMain()` entre bloques. |
| `whenIdle()` | [lib/scheduling.ts](../homeIafrontend/src/lib/scheduling.ts) | Ejecuta trabajo no urgente con `requestIdleCallback`, cuando el navegador está libre. Lo usa el envío de métricas. |
| Refresco encadenado | [hooks/useAsync.ts](../homeIafrontend/src/hooks/useAsync.ts) | En vez de `setInterval`, la siguiente consulta se programa con `setTimeout` **cuando termina** la anterior. Con un backend lento (Gemini tarda decenas de segundos), las peticiones no se acumulan. |
| Pausa con la pestaña oculta | [hooks/useAsync.ts](../homeIafrontend/src/hooks/useAsync.ts) | Con `document.hidden` no se consulta; con el evento `visibilitychange` se recarga al volver. |
| Respuestas fuera de orden | [hooks/useAsync.ts](../homeIafrontend/src/hooks/useAsync.ts) | Cada petición lleva un número y solo la más reciente actualiza la pantalla. |
| `useDeferredValue` | [dashboard/devices/page.tsx](../homeIafrontend/src/app/dashboard/devices/page.tsx) | Al buscar dispositivos, React atiende primero cada tecla (urgente) y filtra la tabla después. |

### Cómo verlo

En **Rendimiento → Demostración**, el botón "Hilo principal (bloquea)" ejecuta un cálculo pesado en una sola tarea: la barra verde se congela, el contador no responde y aparece una long task. "Hilo principal por bloques" usa `processInChunks`: la barra avanza a saltos. La tarjeta **Event loop** muestra el retraso actual y las long tasks de la sesión.

**Límite de los bloques:** solo se puede partir el trabajo que es divisible. Ordenar 200.000 elementos es una sola operación, y por eso el modo por bloques todavía deja un bloqueo de unos 200 ms. Para eso existen los Web Workers.

---

## 2. Web Workers

### Qué es

Un **Web Worker** ejecuta JavaScript en **otro hilo**, con su propio event loop. No tiene acceso al DOM y se comunica con la página solo por mensajes (`postMessage`). El trabajo pesado que se hace ahí no bloquea el hilo principal.

Los datos que se envían se **copian** (*structured clone*), y esa copia también ocurre en el hilo principal. Para series grandes, HomeIA envía `Float64Array` como **transferibles**: el bloque de memoria cambia de dueño sin copiarse. Después de enviarlo, la página ya no puede usarlo.

### Dónde está en HomeIA

| Archivo | Qué hace |
|---|---|
| [lib/energyStats.ts](../homeIafrontend/src/lib/energyStats.ts) | Funciones puras de estadísticas de consumo: perfil horario en la zona horaria de la casa, horas pico, anomalías (puntaje z > 2), media móvil de 24 h y tendencia. |
| [workers/energyStats.worker.ts](../homeIafrontend/src/workers/energyStats.worker.ts) | El worker: recibe `{ id, buckets \| series, timeZone }`, calcula y responde `{ id, stats, ms }`. |
| [lib/energyStatsRunner.ts](../homeIafrontend/src/lib/energyStatsRunner.ts) | Crea **un solo worker** para toda la app con `new Worker(new URL('../workers/energyStats.worker.ts', import.meta.url))`; Turbopack lo empaqueta como un archivo aparte. Relaciona cada respuesta con su petición por `id`, transfiere las series grandes y, si el navegador no tiene workers, calcula por bloques. |
| [hooks/useEnergyStats.ts](../homeIafrontend/src/hooks/useEnergyStats.ts) | Hook que pide el cálculo al worker cuando cambian los datos. |
| [dashboard/analytics/page.tsx](../homeIafrontend/src/app/dashboard/analytics/page.tsx) | **Consumo y ahorro**: las tarjetas "Perfil horario" y "Horas pico y anomalías" se calculan en el worker sobre la serie por hora del período. |

### Cómo verlo

- **En la app:** en Consumo y ahorro, el subtítulo del perfil horario dice "calculado en Web Worker en N ms". En Rendimiento, el botón "Web Worker" corre el cálculo de 200.000 puntos y la barra no se detiene.
- **En DevTools:** la pestaña Sources muestra el hilo del worker, y la pestaña Performance lo dibuja en una fila aparte del *Main*.

---

## 3. Patrones de rendering

### Qué son

Cada patrón decide **dónde y cuándo** se genera el HTML:

| Patrón | Dónde se genera | Cuándo conviene |
|---|---|---|
| **SSG** (estático) | En el servidor, durante el build | Contenido igual para todos. |
| **SSR** (en el servidor) | En el servidor, en cada petición | Datos que cambian y se pueden pedir desde el servidor. |
| **CSR** (en el cliente) | En el navegador, con JavaScript | Datos privados que dependen del navegador, como el token de sesión. |
| **Server Components** | En el servidor; no envían su JavaScript | Partes sin estado ni eventos. |
| **Streaming con Suspense** | El servidor envía primero un esqueleto y luego el resto | Que la página nunca se vea en blanco. |
| **Partial Prerender (PPR)** | El shell es estático y los huecos dinámicos se completan por petición | Rutas mixtas. |
| **Code splitting** | Código en archivos aparte que se descargan al necesitarse | Componentes pesados o que no se ven de inmediato. |

### Dónde está en HomeIA

Esta es la salida de `next build` (○ estática, ◐ Partial Prerender, ƒ en el servidor):

| Ruta | Patrón | Por qué |
|---|---|---|
| `/auth/login`, `/auth/register` (○) | **SSG + Server Component + isla de cliente** | [page.tsx](../homeIafrontend/src/app/auth/login/page.tsx) es un Server Component con su `metadata`. El panel de marca [LoginBrand](../homeIafrontend/src/components/auth/LoginBrand.tsx) se genera en el build y no envía JavaScript. Solo el formulario ([LoginForm](../homeIafrontend/src/components/auth/LoginForm.tsx), [RegisterForm](../homeIafrontend/src/components/auth/RegisterForm.tsx)) es de cliente. |
| `/auth/forgot`, `/auth/reset`, `/setup` (○) | **SSG + CSR** | El HTML se genera en el build y el formulario se activa en el navegador. |
| `/dashboard/*` (○) | **Shell estático + CSR + streaming** | Los datos son privados y el token vive en el navegador, así que se piden desde el cliente. [loading.tsx](../homeIafrontend/src/app/dashboard/loading.tsx) es el límite de Suspense: al navegar se ve el esqueleto al instante. |
| `/dashboard/[section]` (◐) | **Partial Prerender** | Es una ruta dinámica: el esqueleto es estático y el resto se completa por petición. El [layout](../homeIafrontend/src/app/dashboard/layout.tsx) envuelve el shell en `Suspense` porque lee la ruta actual (`usePathname`). |
| Panel de facturación | **Code splitting** (`next/dynamic`) | En [analytics/page.tsx](../homeIafrontend/src/app/dashboard/analytics/page.tsx) se descarga en un archivo aparte, después de lo visible, con un esqueleto mientras llega. |
| Todas (ƒ Proxy) | **Proxy en el servidor** | [src/proxy.ts](../homeIafrontend/src/proxy.ts): sin cookie de sesión, `/dashboard` redirige al login **antes** de renderizar (respuesta 307). |

**Sin cascada (waterfall).** Antes, el layout del dashboard esperaba a confirmar la sesión (`GET /users/me`) para recién entonces mostrar la página, que a su vez pedía sus datos: dos viajes al backend uno detrás del otro. Ahora la página se renderiza de inmediato y las dos cosas ocurren **en paralelo**. Esto además resolvió los avisos de "instant navigation" de `next dev`.

---

## 4. Métricas de rendimiento

### Qué se mide

| Métrica | Qué significa | Bueno | Malo |
|---|---|---|---|
| **LCP** (Largest Contentful Paint) | Cuándo se pinta el elemento principal | ≤ 2,5 s | > 4 s |
| **INP** (Interaction to Next Paint) | Cuánto tarda en responder a un clic o tecla | ≤ 200 ms | > 500 ms |
| **CLS** (Cumulative Layout Shift) | Cuánto se mueve el contenido mientras carga | ≤ 0,1 | > 0,25 |
| **FCP** (First Contentful Paint) | Cuándo aparece el primer contenido | ≤ 1,8 s | > 3 s |
| **TTFB** (Time to First Byte) | Cuánto tarda el servidor en empezar a responder | ≤ 0,8 s | > 1,8 s |
| **Latencia de la API** (propia) | Duración de cada llamada al backend | ≤ 300 ms | > 1 s |
| **Long tasks** | Tareas de más de 50 ms en el hilo principal | 0 | — |
| **Retraso del event loop** | Cuánto se atrasa un temporizador de 500 ms | < 50 ms | — |

LCP, INP y CLS son las **Core Web Vitals**. Google las evalúa con el **p75**: el valor que cumple el 75 % de las visitas.

### Cómo se recolectan y se agregan

```
Navegador                                                     Backend (Spring)
───────────────────────────────────────────────────────────   ─────────────────────────────────
useReportWebVitals (next/web-vitals) ─┐
PerformanceObserver('longtask') ──────┤
sonda de retraso del event loop ──────┼─► perfStore ─► PerformanceReporter ─► POST /api/performance/metrics
api.ts: performance.now() y           │   (memoria,    cada 15 s con whenIdle    └► PerformanceMetricsService
        performance.measure() ────────┘   useSync…)    y al ocultar la pestaña       últimas 500 muestras por casa
                                                       (fetch con keepalive)         y métrica, p75 y clasificación
Página Rendimiento ◄──────────────────────────────────────────────────────────── GET /api/performance/summary
```

| Pieza | Archivo |
|---|---|
| Almacén de métricas de la pestaña | [lib/perfStore.ts](../homeIafrontend/src/lib/perfStore.ts) |
| Recolección y envío | [components/perf/PerformanceReporter.tsx](../homeIafrontend/src/components/perf/PerformanceReporter.tsx), montado en [app/layout.tsx](../homeIafrontend/src/app/layout.tsx) |
| Latencia de la API, con marcas `performance.measure` visibles en DevTools | [services/api.ts](../homeIafrontend/src/services/api.ts) |
| Agregación (p75 y umbrales) | [PerformanceMetricsService.java](../src/main/java/com/homeia/service/PerformanceMetricsService.java) y su [test](../src/test/java/com/homeia/service/PerformanceMetricsServiceTest.java) |
| Endpoints | [PerformanceController.java](../src/main/java/com/homeia/web/PerformanceController.java): `POST /api/performance/metrics` acepta lotes de hasta 50 con nombres de una lista cerrada; `GET /api/performance/summary` |

**`keepalive`.** El envío usa `fetch(..., { keepalive: true })`, así la petición termina aunque se cierre la pestaña. No pasa por `request()`, para no medirse a sí mismo. Las métricas del backend viven en memoria y se reinician con él: son de diagnóstico, no datos de negocio.

---

## 5. Resultados medidos

Medido en Chrome con la página Rendimiento, sobre 200.000 lecturas sintéticas:

| Modo | Tiempo de cálculo | Frame más largo | Long tasks |
|---|---|---|---|
| **Web Worker** | ~600 ms | **24 ms** (la interfaz sigue fluida) | **0** |
| Hilo principal, de una vez | ~520 ms | **524 ms** (congelada todo el cálculo) | 1 |
| Hilo principal, por bloques | ~580 ms | 204 ms (queda el ordenamiento, que no se puede partir) | 1 |

- **Event loop:** con la pestaña oculta durante 35 s hubo **0** llamadas a la API, y al volver se recargó de inmediato.
- **Web Vitals** de una sesión local: LCP ≈ 260 ms, FCP ≈ 260 ms, TTFB ≈ 120 ms, todas en "bueno".

El worker no es más rápido: tarda lo mismo o un poco más, porque además convierte los datos. Lo que cambia es que **la interfaz nunca se congela**. Esa es la razón para usar un Web Worker: no acelera el cálculo, lo saca del hilo que pinta y responde al usuario.
