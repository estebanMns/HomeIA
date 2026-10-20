// Espejo de los DTOs del backend (com.homeia.web)

export type UserRole = 'ADMIN' | 'MEMBER';

export interface User {
  id: string;
  homeId: string;
  email: string;
  fullName: string;
  role: UserRole;
  active: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Home {
  id: string;
  name: string;
  currency: string;
  electricityPricePerKwh: number;
  timezone: string;
  updatedAt: string;
}

export interface Room {
  id: string;
  name: string;
  location?: string;
  createdAt: string;
}

export type DeviceType = 'LIGHT' | 'SMART_OUTLET' | 'PRESENCE_SENSOR';
export type DeviceProvider = 'MQTT' | 'TUYA';
export type ConnectionStatus = 'ONLINE' | 'OFFLINE';
export type PowerState = 'ON' | 'OFF';
export type DeviceCommand = 'ON' | 'OFF' | 'TOGGLE' | 'BRIGHTNESS' | 'STATUS';

export interface Device {
  id: string;
  name: string;
  roomId?: string;
  type: DeviceType;
  connectionStatus: ConnectionStatus;
  powerState: PowerState;
  brightness?: number;
  consumptionWatts: number;
  critical: boolean;
  presence?: boolean;
  lastPresenceAt?: string;
  lastStatusChangeAt?: string;
  lastSeenAt?: string;
  /** Por dónde se conecta: MQTT (dispositivo propio) o TUYA (Tuya / Smart Life por la nube). */
  provider: DeviceProvider;
  /** Id del dispositivo en Tuya. */
  externalId?: string | null;
  /** Umbral propio de consumo excesivo; sin valor se usa el de la casa. */
  consumptionAlertWatts?: number | null;
}

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertType =
  | 'EXCESSIVE_CONSUMPTION'
  | 'POSSIBLE_ENERGY_LEAK'
  | 'DEVICE_OFFLINE'
  | 'AUTOMATIC_SHUTDOWN'
  | 'MANUAL';

export interface Alert {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  title: string;
  message: string;
  roomId?: string;
  deviceId?: string;
  acknowledged: boolean;
  acknowledgedAt?: string;
  acknowledgedBy?: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AutomationSettings {
  enabled: boolean;
  vacancyMinutes: number;
  returnSensitivityPercent: number;
  consumptionAlertWatts: number;
  leakMinutes: number;
  /** Minutos que el consumo debe mantenerse sobre el umbral para alertar. */
  consumptionSustainedMinutes: number;
  /** Minutos sin repetir la misma alerta del mismo dispositivo. */
  alertCooldownMinutes: number;
  updatedAt: string;
}

export interface AutomationStatus {
  enabled: boolean;
  decisionsLast24h: number;
  actionsTotal: number;
  lastDecisionAt?: string;
  predictionMode: 'GEMINI' | 'HISTORICO';
  geminiConfigured: boolean;
}

export type ActionType =
  | 'SWITCHED_OFF_VACANT_ROOM'
  | 'SWITCHED_ON_OCCUPIED_ROOM'
  | 'MANUAL_SHUTDOWN'
  | 'PROTECTED_CRITICAL_DEVICE'
  | 'KEPT_ON_LIKELY_RETURN';

export interface AutomationAction {
  id: string;
  roomId?: string;
  deviceId?: string;
  actionType: ActionType;
  description: string;
  wattsAvoided: number;
  createdAt: string;
}

export interface EnergySummary {
  totalKwh: number;
  cost: number;
  currency: string;
  pricePerKwh: number;
  peakWatts: number;
  currentWatts: number;
  devicesOn: number;
  devicesTotal: number;
  avoidedWattsEstimate: number;
  avoidedCostPerHour: number;
  from: string;
  to: string;
}

export interface EnergyBucket {
  start: string;
  kwh: number;
  cost: number;
  averageWatts: number;
}

export interface RoomEnergy {
  roomId: string;
  roomName: string;
  kwh: number;
  cost: number;
}

export interface TariffPeriod {
  id: string;
  periodStart: string;
  periodEnd: string;
  unitPricePerKwh: number;
  subsidyKwhLimit: number;
  subsidyPercent: number;
  publicLighting: number;
  adjustment: number;
}

export interface TariffInput {
  periodStart: string;
  periodEnd: string;
  unitPricePerKwh: number;
  subsidyKwhLimit: number;
  subsidyPercent: number;
  publicLighting: number;
  adjustment: number;
}

export interface MeterReading {
  id: string;
  readingKwh: number;
  readAt: string;
}

export interface BillStatus {
  tariff: TariffPeriod;
  consumptionKwh?: number;
  energyGross?: number;
  subsidy?: number;
  energyNet?: number;
  adjustment?: number;
  publicLighting?: number;
  total?: number;
  missing?: string;
}

export type MessageStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface WhatsAppStatus {
  configured: boolean;
  activeRecipients: number;
  pendingMessages: number;
  failedMessages: number;
}

export interface WhatsAppRecipient {
  id: string;
  name: string;
  phone: string;
  active: boolean;
  createdAt: string;
}

export interface WhatsAppMessage {
  id: string;
  alertId?: string;
  recipientPhone: string;
  body: string;
  status: MessageStatus;
  attempts: number;
  lastError?: string;
  providerMessageId?: string;
  createdAt: string;
  sentAt?: string;
}

export interface AuditEntry {
  id: number;
  actor: string;
  action: string;
  entityType: string;
  entityId?: string;
  details?: string;
  createdAt: string;
}

export interface SystemHealth {
  status: string;
  database: string;
  mqtt: string;
  whatsapp: string;
  gemini: string;
  checkedAt: string;
}

export interface SystemMetrics {
  devicesTotal: number;
  devicesOnline: number;
  devicesOn: number;
  roomsTotal: number;
  alertsOpen: number;
  whatsappPending: number;
  mqttConnected: boolean;
  generatedAt: string;
}

export interface WifiStatus {
  tuyaConfigured: boolean;
  tuyaRegion: string;
  tuyaEndpoint: string;
  mqttEnabled: boolean;
  mqttConnected: boolean;
}

/** Dispositivo de la cuenta Smart Life vinculada al proyecto de Tuya. */
export interface TuyaCloudDevice {
  id: string;
  name: string;
  category: string;
  productName: string;
  online: boolean;
  /** Id del dispositivo en HomeIA si ya se agregó a la casa. */
  importedAs?: string | null;
}
