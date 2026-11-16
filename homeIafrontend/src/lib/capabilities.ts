import type { Device, DeviceCapability, DeviceProvider } from '@/types';

/** Debe coincidir con DeviceCapabilityPolicy.java: el backend vuelve a validar cada orden. */
const DIMMABLE_BY_DEFAULT: DeviceProvider[] = ['MQTT', 'TUYA', 'TASMOTA', 'WLED'];

export function effectiveCapabilities(device: Device): Set<DeviceCapability> {
  if (device.effectiveCapabilities?.length) return new Set(device.effectiveCapabilities);
  let announced: DeviceCapability[] = [];
  try {
    const parsed = JSON.parse(device.capabilitiesJson || '[]');
    if (Array.isArray(parsed)) announced = parsed.filter((c): c is DeviceCapability => typeof c === 'string');
  } catch {
    announced = [];
  }
  if (announced.length) return new Set(announced);
  const inferred = new Set<DeviceCapability>();
  if (device.type === 'LIGHT') {
    inferred.add('POWER');
    if (DIMMABLE_BY_DEFAULT.includes(device.provider)) inferred.add('BRIGHTNESS');
  } else if (device.type === 'SMART_OUTLET') {
    inferred.add('POWER');
    inferred.add('ENERGY');
  } else {
    inferred.add('PRESENCE');
  }
  return inferred;
}

export const canSwitch = (d: Device) => effectiveCapabilities(d).has('POWER');
export const canDim = (d: Device) => effectiveCapabilities(d).has('BRIGHTNESS');

export const providerLabel: Record<DeviceProvider, string> = {
  MQTT: 'MQTT',
  TUYA: 'WiFi · Tuya',
  ZIGBEE: 'Zigbee',
  SHELLY: 'WiFi · Shelly',
  TASMOTA: 'WiFi · Tasmota',
  ESPHOME: 'WiFi · ESPHome',
  WLED: 'WiFi · WLED',
};

/** Proveedores LAN que admiten credenciales locales cifradas. */
export const usesIpAddress = (p: DeviceProvider) => ['SHELLY', 'TASMOTA', 'ESPHOME', 'WLED'].includes(p);

const telemetryLabels: Record<string, (v: unknown) => string> = {
  temperatureC: (v) => `${Number(v).toFixed(1)} °C`,
  humidityPercent: (v) => `${Math.round(Number(v))} % HR`,
  batteryPercent: (v) => `Batería ${Math.round(Number(v))} %`,
  contactOpen: (v) => (v ? 'Abierto' : 'Cerrado'),
  illuminanceLux: (v) => `${Math.round(Number(v))} lx`,
  energyKwh: (v) => `${Number(v).toFixed(2)} kWh`,
  voltage: (v) => `${Number(v).toFixed(0)} V`,
  currentA: (v) => `${Number(v).toFixed(2)} A`,
  occupancy: (v) => (v ? 'Movimiento' : 'Sin movimiento'),
  batteryLow: (v) => (v ? 'Batería baja' : ''),
};

/** Telemetría legible para mostrar junto al dispositivo (sensores Zigbee, medidores, etc.). */
export function telemetrySummary(device: Device): string[] {
  try {
    const data = JSON.parse(device.telemetryJson || '{}') as Record<string, unknown>;
    return Object.entries(data)
      .filter(([k, v]) => telemetryLabels[k] && v !== null && v !== undefined)
      .map(([k, v]) => telemetryLabels[k](v))
      .filter(Boolean);
  } catch {
    return [];
  }
}
