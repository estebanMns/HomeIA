'use client';

import { FormEvent, useState } from 'react';
import {
  Activity, CheckCircle2, CircleDashed, Copy, Cpu, KeyRound, Radar, RefreshCw, Router, ShieldAlert,
  ShieldCheck, Trash2, Wifi, XCircle, AlertTriangle, Clock3,
} from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAsync } from '@/hooks/useAsync';
import { useAuth } from '@/contexts/AuthContext';
import type {
  CheckStatus, DeviceType, DiscoveryCandidate, EdgeEnrollment, EdgeNode, EdgeStatus, IntegrationStatus, Room, DeviceEvent,
} from '@/types';
import { Badge, BadgeTone, Button, Card, SectionTitle } from '@/components/ui/Primitives';
import { formatDateTime } from '@/components/ui/AlertCard';
import { providerLabel } from '@/lib/capabilities';

interface Data {
  status: IntegrationStatus;
  edges: EdgeNode[];
  candidates: DiscoveryCandidate[];
  rooms: Room[];
  events: DeviceEvent[];
}

const edgeTone: Record<EdgeStatus, BadgeTone> = { ONLINE: 'green', OFFLINE: 'red', PENDING: 'amber', REVOKED: 'gray' };
const edgeLabel: Record<EdgeStatus, string> = { ONLINE: 'En línea', OFFLINE: 'Sin conexión', PENDING: 'Esperando conexión', REVOKED: 'Revocado' };
const checkTone: Record<CheckStatus, BadgeTone> = { OK: 'green', PENDING: 'blue', WARN: 'amber', FAIL: 'red' };
const checkLabel: Record<CheckStatus, string> = { OK: 'Correcto', PENDING: 'Pendiente', WARN: 'Revisar', FAIL: 'Falla' };

const integrations: { id: string; name: string; steps: string[] }[] = [
  {
    id: 'EDGE', name: 'HomeIA Edge', steps: [
      'Enrola un Edge abajo y guarda HOMEIA_EDGE_ID y HOMEIA_EDGE_SECRET (el secreto solo se muestra una vez).',
      'En un equipo Linux / Raspberry Pi de la LAN: sudo ./edge/install.sh y completa /etc/homeia-edge.env (o usa compose.edge.yaml).',
      'Conecta el backend al mismo broker: VPN privada o broker en la nube con mTLS (docs/SEGURIDAD_MQTT.md).',
      'Comprueba que el heartbeat aparezca en verde en esta página.',
    ],
  },
  {
    id: 'ZIGBEE', name: 'Zigbee2MQTT', steps: [
      'Conecta el coordinador USB al equipo del Edge y define ZIGBEE_SERIAL_DEVICE (/dev/serial/by-id/...).',
      'Copia data_zigbee_configuration.yaml a data/zigbee2mqtt/configuration.yaml con el usuario zigbee2mqtt.',
      'Levanta compose.edge.yaml y verifica que el coordinador aparezca "online".',
      'Pulsa "Permitir Zigbee" (máx. 120 s), empareja el equipo y luego incorpóralo desde los candidatos.',
    ],
  },
  {
    id: 'SHELLY', name: 'Shelly', steps: [
      'Conecta el Shelly a la Wi-Fi de la casa con su app o su interfaz web.',
      'Si activaste contraseña, cárgala después en Dispositivos → Credenciales (se guarda cifrada).',
      'Pulsa "Buscar dispositivos"; los Gen1 y Gen2/Plus/Pro se detectan por mDNS.',
      'Incorpóralo como luz o tomacorriente; los dimmers aparecen con regulación de brillo.',
    ],
  },
  {
    id: 'TASMOTA', name: 'Tasmota', steps: [
      'Conecta el equipo Tasmota a la Wi-Fi y anota su IP fija (reserva DHCP recomendada).',
      'Si tiene contraseña web, cárgala en Credenciales del dispositivo.',
      'Pulsa "Buscar dispositivos" (SSDP/mDNS) e incorpóralo.',
    ],
  },
  {
    id: 'ESPHOME', name: 'ESPHome (API nativa)', steps: [
      'Incluye "api:" en el YAML del firmware; si usas "encryption: key", tenla a mano.',
      'Pulsa "Buscar dispositivos": se detecta por _esphomelib._tcp.',
      'Incorpóralo y carga la clave de cifrado en Credenciales. El Edge controla la primera luz o interruptor del firmware.',
    ],
  },
  {
    id: 'WLED', name: 'WLED', steps: [
      'Conecta el controlador WLED a la Wi-Fi.',
      'Pulsa "Buscar dispositivos" (mDNS _wled._tcp) e incorpóralo como luz.',
    ],
  },
  {
    id: 'MQTT', name: 'ESP32 / MQTT HomeIA', steps: [
      'Levanta el broker (compose.mqtt.yaml) y crea un usuario por dispositivo con su id de HomeIA (MQTT_DEVICE_USERS="<id>:clave").',
      'Registra el dispositivo en Dispositivos y usa su id en homeia/devices/<id>/command y /state.',
    ],
  },
  {
    id: 'TUYA', name: 'Tuya / Smart Life', steps: [
      'Crea un proyecto Cloud en iot.tuya.com y vincula tu cuenta Smart Life.',
      'Define TUYA_ACCESS_ID, TUYA_ACCESS_SECRET y TUYA_REGION en el backend.',
      'Importa los equipos desde Conexión WiFi.',
    ],
  },
];

function CheckIcon({ status }: { status: CheckStatus }) {
  if (status === 'OK') return <CheckCircle2 size={18} color="var(--green)" />;
  if (status === 'FAIL') return <XCircle size={18} color="var(--red)" />;
  if (status === 'WARN') return <AlertTriangle size={18} color="var(--amber, #d97706)" />;
  return <Clock3 size={18} className="muted" />;
}

interface ClaimDraft { type: DeviceType; roomId: string; name: string }

export default function IntegrationsPage() {
  const { user } = useAuth();
  const admin = user?.role === 'ADMIN';
  const { data, error, reload } = useAsync<Data>(async () => {
    const [status, edges, rooms, events, candidates] = await Promise.all([
      api.integrationStatus(), api.edges(), api.rooms(), api.integrationEvents(30),
      admin ? api.discoveryCandidates() : Promise.resolve([] as DiscoveryCandidate[]),
    ]);
    return { status, edges, candidates, rooms, events };
  }, [admin], 5000);

  const [integration, setIntegration] = useState('EDGE');
  const diagnostic = useAsync(() => api.integrationDiagnostics(integration), [integration], 10000);

  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [edgeName, setEdgeName] = useState('');
  const [enrollment, setEnrollment] = useState<EdgeEnrollment | null>(null);
  const [joinEdge, setJoinEdge] = useState('');
  const [drafts, setDrafts] = useState<Record<string, ClaimDraft>>({});

  const run = async (action: () => Promise<unknown>, ok?: string) => {
    setBusy(true);
    setMessage('');
    try {
      const result = await action();
      if (ok) setMessage(ok);
      else if (result && typeof result === 'object' && 'message' in result) setMessage(String((result as { message: string }).message));
      await reload();
      await diagnostic.reload();
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : 'Error inesperado');
    } finally {
      setBusy(false);
    }
  };

  const enroll = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      setEnrollment(await api.enrollEdge(edgeName));
      setEdgeName('');
    }, 'Edge enrolado. Copia la configuración ahora: el secreto no se volverá a mostrar.');
  };

  const draftFor = (c: DiscoveryCandidate): ClaimDraft =>
    drafts[c.id] ?? { type: c.capabilities.includes('BRIGHTNESS') || c.provider === 'WLED' ? 'LIGHT' : (c.capabilities.some((x) => ['MOTION', 'PRESENCE', 'CONTACT', 'TEMPERATURE'].includes(x)) && !c.capabilities.includes('POWER') ? 'PRESENCE_SENSOR' : 'LIGHT'), roomId: '', name: c.name };

  const envText = enrollment ? Object.entries(enrollment.env).map(([k, v]) => `${k}=${v}`).join('\n') : '';
  const status = data?.status;
  const zigbeeEdges = (data?.edges ?? []).filter((e) => e.status === 'ONLINE' && e.zigbeeBridgeState);

  return (
    <div className="page-content">
      <SectionTitle title="Ecosistema multimarca" subtitle="Estado de cada integración, HomeIA Edge, asistente de configuración y descubrimiento local" />

      {(error || message) && (
        <Card style={{ marginBottom: 16, color: error ? 'var(--red)' : 'var(--text)' }}>{error || message}</Card>
      )}

      {/* Estado por componente: nada se da por bueno solo porque MQTT conectó */}
      <div className="stats-grid" style={{ marginBottom: 16 }}>
        <Card>
          <Router size={22} />
          <strong>Broker MQTT</strong>
          <Badge tone={status?.broker.state === 'CONNECTED' ? 'green' : status?.broker.state === 'DISABLED' ? 'gray' : 'red'}>
            {status?.broker.state === 'CONNECTED' ? 'Conectado' : status?.broker.state === 'DISABLED' ? 'Deshabilitado' : 'Sin conexión'}
          </Badge>
          <span className="muted">{status?.broker.mutualTls ? 'TLS mutuo' : status?.broker.tls ? 'TLS' : 'Sin TLS (solo VPN/LAN)'}</span>
        </Card>
        <Card>
          <Cpu size={22} />
          <strong>HomeIA Edge</strong>
          <span>{(data?.edges ?? []).filter((e) => e.status === 'ONLINE').length} en línea de {(data?.edges ?? []).filter((e) => e.status !== 'REVOKED').length}</span>
          <span className="muted">{status?.pendingCommands ?? 0} órdenes esperando confirmación</span>
        </Card>
        <Card>
          <Activity size={22} />
          <strong>Coordinador Zigbee</strong>
          {status?.zigbee.length ? status.zigbee.map((z) => (
            <span key={z.edgeId}>
              <Badge tone={z.bridgeState === 'online' && z.edgeStatus === 'ONLINE' ? 'green' : 'red'}>{z.edgeName}: {z.bridgeState}</Badge>
              {z.permitJoin && <Badge tone="amber">Emparejando</Badge>}
            </span>
          )) : <span className="muted">No reportado por ningún Edge</span>}
        </Card>
        <Card>
          <Wifi size={22} />
          <strong>Tuya / Smart Life</strong>
          <Badge tone={status?.tuya.configured ? 'green' : 'gray'}>{status?.tuya.configured ? 'Configurado' : 'Sin configurar'}</Badge>
        </Card>
      </div>

      <Card className="table-card" style={{ marginBottom: 16 }}>
        <div className="data-table">
          <div className="table-head" style={{ gridTemplateColumns: '2fr 1fr 1fr 1fr 1fr' }}>
            <span>INTEGRACIÓN</span><span>VÍA</span><span>DISPOSITIVOS</span><span>EN LÍNEA</span><span>DETECTADOS</span>
          </div>
          {(status?.providers ?? []).map((p) => (
            <div className="table-row" key={p.provider} style={{ gridTemplateColumns: '2fr 1fr 1fr 1fr 1fr' }}>
              <strong>{providerLabel[p.provider]}</strong>
              <span>{p.viaEdge ? 'HomeIA Edge' : p.provider === 'TUYA' ? 'Nube' : 'Broker'}</span>
              <span>{p.devices}</span>
              <Badge tone={p.devices === 0 ? 'gray' : p.online === p.devices ? 'green' : p.online === 0 ? 'red' : 'amber'}>{p.online}/{p.devices}</Badge>
              <span>{p.candidates}</span>
            </div>
          ))}
        </div>
      </Card>

      {/* HomeIA Edge */}
      <Card style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>HomeIA Edge</h3>
        <p className="muted">Agente local de cada casa. Se autentica con su propio secreto (firma HMAC en ambos sentidos); revocarlo lo desconecta de inmediato.</p>
        {admin && (
          <form onSubmit={enroll} className="filter-row" style={{ marginBottom: 12 }}>
            <div className="search grow"><input placeholder="Nombre del Edge (p. ej. Raspberry sala)" value={edgeName} onChange={(e) => setEdgeName(e.target.value)} required maxLength={120} /></div>
            <button type="submit" className="btn btn-primary btn-md" disabled={busy}><KeyRound size={16} /> Enrolar Edge</button>
          </form>
        )}
        {(data?.edges ?? []).length === 0 ? (
          <div className="empty-state"><CircleDashed size={28} /><p>No hay Edge enrolados.</p></div>
        ) : (data?.edges ?? []).map((edge) => (
          <div key={edge.id} style={{ display: 'flex', gap: 12, alignItems: 'center', padding: '12px 0', borderBottom: '1px solid var(--border)', flexWrap: 'wrap' }}>
            <div className="device-icon"><Cpu size={20} /></div>
            <div className="grow" style={{ minWidth: 220 }}>
              <strong>{edge.name}</strong> <Badge tone={edgeTone[edge.status]}>{edgeLabel[edge.status]}</Badge>
              <div className="muted" style={{ fontSize: 13 }}>
                {edge.version ? `v${edge.version}` : 'versión desconocida'} · {edge.platform ?? '—'} · último heartbeat {edge.lastHeartbeatAt ? formatDateTime(edge.lastHeartbeatAt) : 'nunca'}
                {edge.queueDepth > 0 && ` · ${edge.queueDepth} en cola`}
                {edge.zigbeeBridgeState && ` · Zigbee ${edge.zigbeeBridgeState}${edge.zigbeePermitJoin ? ' (emparejando)' : ''}`}
              </div>
              {edge.previousSecretValidUntil && new Date(edge.previousSecretValidUntil) > new Date() && (
                <small style={{ color: 'var(--amber, #d97706)' }}>Rotación en curso: el secreto anterior vale hasta {formatDateTime(edge.previousSecretValidUntil)}</small>
              )}
            </div>
            {admin && edge.status !== 'REVOKED' && (
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <Button size="sm" variant="outline" disabled={busy || edge.status !== 'ONLINE'} onClick={() => run(() => api.syncEdge(edge.id), 'Inventario reenviado al Edge')}><RefreshCw size={14} /> Sincronizar</Button>
                <Button size="sm" variant="outline" disabled={busy} onClick={() => {
                  if (window.confirm(`Rotar el secreto de ${edge.name}? El anterior seguirá valiendo 15 minutos.`)) run(async () => setEnrollment(await api.rotateEdge(edge.id)), 'Secreto rotado. Actualiza HOMEIA_EDGE_SECRET en el Edge y reinícialo.');
                }}><KeyRound size={14} /> Rotar secreto</Button>
                <Button size="sm" variant="danger" disabled={busy} onClick={() => {
                  if (window.confirm(`Revocar ${edge.name}? Dejará de poder enviar o recibir mensajes.`)) run(() => api.revokeEdge(edge.id), 'Edge revocado');
                }}><ShieldAlert size={14} /> Revocar</Button>
              </div>
            )}
            {admin && edge.status === 'REVOKED' && (
              <Button size="icon" variant="ghost" disabled={busy} onClick={() => {
                if (window.confirm(`Eliminar ${edge.name}? Sus dispositivos quedarán sin Edge.`)) run(() => api.deleteEdge(edge.id), 'Edge eliminado');
              }}><Trash2 size={16} /></Button>
            )}
          </div>
        ))}
      </Card>

      {/* Asistente */}
      <Card style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>Asistente de configuración</h3>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }}>
          {integrations.map((i) => (
            <Button key={i.id} size="sm" variant={integration === i.id ? 'primary' : 'outline'} onClick={() => setIntegration(i.id)}>{i.name}</Button>
          ))}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
          <div>
            <strong>Pasos</strong>
            <ol style={{ paddingLeft: 18, lineHeight: 1.6 }}>
              {integrations.find((i) => i.id === integration)?.steps.map((s) => <li key={s}>{s}</li>)}
            </ol>
          </div>
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <strong>Diagnóstico</strong>
              <span style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {diagnostic.data && <Badge tone={checkTone[diagnostic.data.overall]}>{checkLabel[diagnostic.data.overall]}</Badge>}
                <Button size="sm" variant="ghost" onClick={() => void diagnostic.reload()}><RefreshCw size={14} /></Button>
              </span>
            </div>
            {diagnostic.error && <p style={{ color: 'var(--red)' }}>{diagnostic.error}</p>}
            {(diagnostic.data?.checks ?? []).map((c) => (
              <div key={c.id} style={{ display: 'flex', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--border)' }}>
                <CheckIcon status={c.status} />
                <div>
                  <div><strong>{c.label}</strong> <span className="muted">· {c.detail}</span></div>
                  {c.hint && <small className="muted">{c.hint}</small>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* Descubrimiento */}
      {admin && (
        <Card style={{ marginBottom: 16 }}>
          <div className="filter-row" style={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
            <div>
              <h3 style={{ margin: 0 }}>Descubrimiento de red</h3>
              <p className="muted">Lo ejecuta HomeIA Edge dentro de la LAN. Encontrar un equipo no lo empareja: tú decides qué incorporar.</p>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
              <Button onClick={() => run(() => api.discoveryScan())} disabled={busy}><Radar size={16} /> Buscar dispositivos</Button>
              {zigbeeEdges.length > 1 && (
                <select className="btn btn-outline btn-md" value={joinEdge} onChange={(e) => setJoinEdge(e.target.value)}>
                  <option value="">Todos los coordinadores</option>
                  {zigbeeEdges.map((e) => <option key={e.id} value={e.id}>{e.name}</option>)}
                </select>
              )}
              <Button variant="outline" disabled={busy || zigbeeEdges.length === 0} onClick={() => run(() => api.zigbeePermitJoin(60, joinEdge || undefined), 'Emparejamiento Zigbee abierto 60 s. Se cerrará y verificará automáticamente.')}><Activity size={16} /> Permitir Zigbee 60 s</Button>
              <Button variant="outline" disabled={busy || zigbeeEdges.length === 0} onClick={() => run(() => api.zigbeeClosePermitJoin(joinEdge || undefined), 'Emparejamiento Zigbee cerrado')}>Cerrar emparejamiento</Button>
            </div>
          </div>
          {(data?.candidates ?? []).length === 0 ? (
            <div className="empty-state"><CircleDashed size={28} /><p>No hay candidatos recientes.</p><span>Comprueba que el Edge esté en línea y pulsa Buscar.</span></div>
          ) : (data?.candidates ?? []).map((c) => {
            const d = draftFor(c);
            const set = (patch: Partial<ClaimDraft>) => setDrafts({ ...drafts, [c.id]: { ...d, ...patch } });
            return (
              <div key={c.id} style={{ display: 'flex', gap: 12, alignItems: 'center', padding: '12px 0', borderBottom: '1px solid var(--border)', flexWrap: 'wrap' }}>
                <div className="device-icon">{c.provider === 'ZIGBEE' ? <Activity size={20} /> : <Wifi size={20} />}</div>
                <div className="grow" style={{ minWidth: 220 }}>
                  <strong>{c.name}</strong> <Badge tone="blue">{providerLabel[c.provider]}</Badge>
                  <div className="muted" style={{ fontSize: 13 }}>{c.address} · {c.manufacturer ?? 'Fabricante desconocido'} {c.model ?? ''} · vía {c.edgeName}</div>
                  {c.capabilities.length > 0 && <small className="muted">Capacidades: {c.capabilities.join(', ')}</small>}
                </div>
                <input className="btn btn-outline btn-md" style={{ width: 160 }} value={d.name} maxLength={120} onChange={(e) => set({ name: e.target.value })} aria-label="Nombre" />
                <select className="btn btn-outline btn-md" value={d.type} onChange={(e) => set({ type: e.target.value as DeviceType })}>
                  <option value="LIGHT">Luz</option>
                  <option value="SMART_OUTLET">Tomacorriente</option>
                  <option value="PRESENCE_SENSOR">Sensor</option>
                </select>
                <select className="btn btn-outline btn-md" value={d.roomId} onChange={(e) => set({ roomId: e.target.value })}>
                  <option value="">Sin habitación</option>
                  {(data?.rooms ?? []).map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
                <Button size="sm" disabled={busy} onClick={() => run(() => api.claimDiscoveryCandidate(c.id, { type: d.type, roomId: d.roomId || undefined, critical: false, name: d.name }), `${d.name} incorporado`)}>
                  <ShieldCheck size={14} /> Incorporar
                </Button>
              </div>
            );
          })}
        </Card>
      )}

      <Card>
        <h3 style={{ marginTop: 0 }}>Actividad reciente</h3>
        {(data?.events ?? []).length === 0 ? <p className="muted">Sin eventos.</p> : (data?.events ?? []).map((e) => (
          <div key={e.id} style={{ display: 'flex', gap: 12, padding: '6px 0', borderBottom: '1px solid var(--border)', fontSize: 14 }}>
            <span className="muted" style={{ minWidth: 110 }}>{formatDateTime(e.createdAt)}</span>
            <Badge tone={e.type.includes('FAILED') || e.type === 'ERROR' || e.type === 'OFFLINE' ? 'red' : e.type.includes('EXPIRED') ? 'amber' : 'gray'}>{e.type}</Badge>
            <span>{e.details}</span>
          </div>
        ))}
      </Card>

      {enrollment && (
        <div className="modal-backdrop" onClick={() => setEnrollment(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-title">Configuración de {enrollment.edge.name}</div>
            <p className="muted">Copia estas variables en <code>/etc/homeia-edge.env</code> o en el <code>.env</code> de compose.edge.yaml. <strong>El secreto no se volverá a mostrar.</strong></p>
            <pre style={{ background: 'var(--surface-2, #0f172a0d)', padding: 12, borderRadius: 8, overflowX: 'auto', fontSize: 13 }}>{envText}</pre>
            <div className="modal-actions">
              <Button variant="outline" onClick={() => void navigator.clipboard?.writeText(envText)}><Copy size={14} /> Copiar</Button>
              <Button onClick={() => setEnrollment(null)}>Ya lo guardé</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
