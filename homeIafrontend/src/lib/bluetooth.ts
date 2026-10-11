'use client';

/**
 * Conexión Bluetooth Low Energy desde el navegador (Web Bluetooth API).
 *
 * Disponible en Chrome y Edge (escritorio y Android) con HTTPS o localhost.
 * No está disponible en Safari (iPhone/Mac) ni en Firefox.
 *
 * Contrato GATT que debe implementar el firmware de cada dispositivo HomeIA:
 *  - Servicio:            HOMEIA_SERVICE_UUID
 *  - Característica de comando (escritura): HOMEIA_COMMAND_UUID
 *      JSON UTF-8: {"command":"ON"|"OFF"|"TOGGLE"|"BRIGHTNESS"|"STATUS","brightness":0-100}
 *  - Característica de estado (lectura + notificaciones): HOMEIA_STATE_UUID
 *      JSON UTF-8: {"powerState":"ON"|"OFF","consumptionWatts":12.5,"brightness":80,"presence":true}
 *  - Nombre anunciado que empieza por "HomeIA" (opcional, ayuda a filtrar en el selector)
 */

export const HOMEIA_SERVICE_UUID = '776661c5-ac9a-4ea3-808f-f13b7fece3a4';
export const HOMEIA_COMMAND_UUID = '5e3cedfc-4c0f-49b8-ae9b-085d645b9db3';
export const HOMEIA_STATE_UUID = 'b86d2ede-fbc8-46b3-926d-90338dcd912f';
export const HOMEIA_NAME_PREFIX = 'HomeIA';

export type BleCommand = 'ON' | 'OFF' | 'TOGGLE' | 'BRIGHTNESS' | 'STATUS';

export interface BleDeviceState {
  powerState?: 'ON' | 'OFF';
  consumptionWatts?: number;
  brightness?: number;
  presence?: boolean;
}

export type BleStatus = 'connecting' | 'connected' | 'disconnected' | 'error';

export interface BleSession {
  /** Id Bluetooth estable que entrega el navegador (BluetoothDevice.id). */
  bluetoothId: string;
  name: string;
  status: BleStatus;
  error?: string;
  state: BleDeviceState;
  lastUpdate?: number;
}

interface Connection {
  device: BluetoothDevice;
  command?: BluetoothRemoteGATTCharacteristic;
  session: BleSession;
}

export function isBluetoothSupported(): boolean {
  return typeof navigator !== 'undefined' && 'bluetooth' in navigator && !!navigator.bluetooth;
}

const connections = new Map<string, Connection>();
const listeners = new Set<() => void>();
let snapshot: BleSession[] = [];

function emit() {
  snapshot = Array.from(connections.values()).map((c) => ({ ...c.session }));
  listeners.forEach((l) => l());
}

/** Suscripción para React (useSyncExternalStore). */
export function subscribeBle(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getBleSessions(): BleSession[] {
  return snapshot;
}

function decode(value: DataView): BleDeviceState {
  const text = new TextDecoder().decode(value);
  try {
    return JSON.parse(text) as BleDeviceState;
  } catch {
    return {};
  }
}

/**
 * Muestra el selector del navegador (requiere un clic del usuario).
 * Filtra por el servicio de HomeIA y por el prefijo de nombre; con acceptAll se muestran todos los equipos BLE.
 */
export async function discoverDevice(acceptAll = false): Promise<BluetoothDevice> {
  if (!isBluetoothSupported()) {
    throw new Error('Este navegador no soporta Bluetooth. Usa Chrome o Edge con HTTPS.');
  }
  if (acceptAll) {
    return navigator.bluetooth.requestDevice({
      acceptAllDevices: true,
      optionalServices: [HOMEIA_SERVICE_UUID],
    });
  }
  return navigator.bluetooth.requestDevice({
    filters: [{ services: [HOMEIA_SERVICE_UUID] }, { namePrefix: HOMEIA_NAME_PREFIX }],
    optionalServices: [HOMEIA_SERVICE_UUID],
  });
}

/** Se conecta por GATT, lee el estado y suscribe las notificaciones. */
export async function connectDevice(device: BluetoothDevice): Promise<BleSession> {
  const id = device.id;
  const existing = connections.get(id);
  if (existing?.session.status === 'connected') {
    return existing.session;
  }

  const session: BleSession = {
    bluetoothId: id,
    name: device.name ?? 'Dispositivo Bluetooth',
    status: 'connecting',
    state: {},
  };
  const connection: Connection = { device, session };
  connections.set(id, connection);
  emit();

  device.addEventListener('gattserverdisconnected', () => {
    update(id, { status: 'disconnected', command: undefined });
  });

  try {
    const server = await device.gatt!.connect();
    const service = await server.getPrimaryService(HOMEIA_SERVICE_UUID);
    const state = await service.getCharacteristic(HOMEIA_STATE_UUID);
    const command = await service.getCharacteristic(HOMEIA_COMMAND_UUID);

    state.addEventListener('characteristicvaluechanged', (event) => {
      const value = (event.target as BluetoothRemoteGATTCharacteristic).value;
      if (value) update(id, { state: { ...connections.get(id)?.session.state, ...decode(value) }, lastUpdate: Date.now() });
    });
    await state.startNotifications();

    const initial = await state.readValue();
    update(id, { status: 'connected', command, state: decode(initial), lastUpdate: Date.now(), error: undefined });
    return connections.get(id)!.session;
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Error de conexión';
    update(id, { status: 'error', error: `No es un dispositivo HomeIA compatible o se perdió la conexión: ${message}` });
    device.gatt?.disconnect();
    throw new Error(message);
  }
}

/** Envía un comando por la característica de comando. */
export async function sendBleCommand(bluetoothId: string, command: BleCommand, brightness?: number): Promise<void> {
  const connection = connections.get(bluetoothId);
  if (!connection?.command) {
    throw new Error('El dispositivo no está conectado');
  }
  const payload = new TextEncoder().encode(JSON.stringify({ command, brightness }));
  await connection.command.writeValueWithResponse(payload);
}

export function disconnectDevice(bluetoothId: string): void {
  const connection = connections.get(bluetoothId);
  if (!connection) return;
  if (connection.device.gatt?.connected) {
    connection.device.gatt.disconnect();
  }
  update(bluetoothId, { status: 'disconnected', command: undefined });
}

export function forgetDevice(bluetoothId: string): void {
  disconnectDevice(bluetoothId);
  connections.delete(bluetoothId);
  emit();
}

function update(id: string, patch: Partial<BleSession> & { command?: BluetoothRemoteGATTCharacteristic | undefined }) {
  const connection = connections.get(id);
  if (!connection) return;
  const { command, ...sessionPatch } = patch;
  if ('command' in patch) connection.command = command;
  connection.session = { ...connection.session, ...sessionPatch };
  emit();
}
