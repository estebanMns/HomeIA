'use client';

import { useCallback, useEffect, useState } from 'react';
import { Activity, Cable, CircleDashed, Cpu, Radar, RefreshCw, Wifi } from 'lucide-react';
import { api, ApiError } from '@/services/api';
import { useAuth } from '@/contexts/AuthContext';
import type { DiscoveryCandidate, DeviceType } from '@/types';
import { Badge, Button, Card, SectionTitle } from '@/components/ui/Primitives';

export default function IntegrationsPage() {
  const { user } = useAuth(); const admin = user?.role === 'ADMIN';
  const [items,setItems] = useState<DiscoveryCandidate[]>([]); const [message,setMessage] = useState('');
  const [busy,setBusy] = useState(false); const [type,setType] = useState<DeviceType>('LIGHT');
  const load = useCallback(async () => { try { setItems(await api.discoveryCandidates()); } catch(e) { setMessage(e instanceof ApiError ? e.message : 'No fue posible consultar Edge'); } },[]);
  useEffect(() => { void load(); const t=setInterval(()=>void load(),5000); return ()=>clearInterval(t); },[load]);
  const scan = async () => { setBusy(true); setMessage(''); try { const r=await api.discoveryScan(); setMessage(r.message); } catch(e) { setMessage(e instanceof ApiError ? e.message : 'Error al iniciar búsqueda'); } finally { setBusy(false); } };
  const permitJoin = async () => { setBusy(true); try { await api.zigbeePermitJoin(60); setMessage('Emparejamiento Zigbee habilitado durante 60 segundos. Zigbee2MQTT lo cerrará automáticamente.'); } catch(e) { setMessage(e instanceof ApiError ? e.message : 'No se pudo habilitar el emparejamiento'); } finally { setBusy(false); } };
  const closePermitJoin = async () => { setBusy(true); try { await api.zigbeeClosePermitJoin(); setMessage('Emparejamiento Zigbee cerrado.'); } catch(e) { setMessage(e instanceof ApiError ? e.message : 'No se pudo cerrar el emparejamiento'); } finally { setBusy(false); } };
  const claim = async (item: DiscoveryCandidate) => { setBusy(true); try { await api.claimDiscoveryCandidate(item.id,{type,critical:false}); setMessage(`${item.name} incorporado a HomeIA`); await load(); } catch(e) { setMessage(e instanceof ApiError ? e.message : 'No se pudo incorporar'); } finally { setBusy(false); } };
  return <div className="page-content">
    <SectionTitle title="Ecosistema multimarca" subtitle="Descubrimiento local y administración de integraciones Wi-Fi, Zigbee y MQTT" />
    {message && <Card style={{marginBottom:16}}>{message}</Card>}
    <div className="stats-grid" style={{marginBottom:16}}>
      {[{n:'Tuya / Smart Life',d:'Nube del fabricante',i:Wifi},{n:'Shelly / Tasmota',d:'HTTP local en LAN',i:Cpu},{n:'Zigbee2MQTT',d:'Coordinador + broker',i:Activity},{n:'ESP32 / MQTT',d:'Protocolo HomeIA',i:Cable}].map(x=><Card key={x.n}><x.i size={22}/><strong>{x.n}</strong><span className="muted">{x.d}</span></Card>)}
    </div>
    <Card style={{marginBottom:16}}>
      <div className="filter-row" style={{justifyContent:'space-between',alignItems:'center'}}>
        <div><h3 style={{margin:0}}>Descubrimiento de red</h3><p className="muted">La búsqueda la ejecuta HomeIA Edge dentro de la red local. Encontrar un equipo no lo empareja automáticamente.</p></div>
        {admin && <div style={{display:'flex',gap:8,flexWrap:'wrap'}}><Button onClick={scan} disabled={busy}><Radar size={16}/> {busy?'Procesando…':'Buscar dispositivos'}</Button><Button variant="outline" onClick={permitJoin} disabled={busy}><Activity size={16}/> Permitir Zigbee 60 s</Button><Button variant="outline" onClick={closePermitJoin} disabled={busy}>Cerrar emparejamiento</Button></div>}
      </div>
    </Card>
    <Card>
      <div className="filter-row" style={{justifyContent:'space-between'}}><h3>Dispositivos detectados</h3><Button variant="outline" onClick={()=>void load()}><RefreshCw size={15}/> Actualizar</Button></div>
      {items.length===0 ? <div className="empty-state"><CircleDashed size={28}/><p>No hay candidatos recientes.</p><span>Comprueba que HomeIA Edge esté activo y conectado al broker.</span></div> : items.map(item=><div key={item.id} className="device-row" style={{display:'flex',gap:12,alignItems:'center',padding:'14px 0',borderBottom:'1px solid var(--border)'}}>
        <div className="device-icon"><Wifi size={20}/></div><div className="grow"><strong>{item.name}</strong><div className="muted">{item.provider} · {item.protocol} · {item.address}</div><small>{item.manufacturer ?? 'Fabricante desconocido'} {item.model ?? ''}</small>{item.capabilities?.length > 0 && <small className="muted">Capacidades: {item.capabilities.join(', ')}</small>}</div><Badge>{item.provider}</Badge>{admin && <Button size="sm" onClick={()=>void claim(item)} disabled={busy || !['SHELLY','TASMOTA','ZIGBEE'].includes(item.provider)}>Incorporar</Button>}
      </div>)}
    </Card>
    <Card style={{marginTop:16}}><h3>Tipo de dispositivo para incorporación</h3><select className="btn btn-outline btn-md" value={type} onChange={e=>setType(e.target.value as DeviceType)}><option value="LIGHT">Luz</option><option value="SMART_OUTLET">Tomacorriente</option><option value="PRESENCE_SENSOR">Sensor de presencia</option></select><p className="muted">La detección muestra capacidades anunciadas por el dispositivo cuando están disponibles. Confirma el tipo antes de registrarlo; la incorporación no activa el emparejamiento ni concede permisos adicionales.</p></Card>
  </div>;
}
