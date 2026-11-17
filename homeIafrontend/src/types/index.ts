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
  /** Último dato de presencia (sensor o marcado a mano); null si nunca se registró. */
  occupied?: boolean | null;
  presenceAt?: string | null;
}

export type DeviceType = 'LIGHT' | 'SMART_OUTLET' | 'PRESENCE_SENSOR';
export type DeviceProvider = 'MQTT' | 'TUYA' | 'ZIGBEE' | 'SHELLY' | 'TASMOTA' | 'ESPHOME' | 'WLED';
export type DeviceProtocol = 'WIFI' | 'ZIGBEE' | 'MQTT' | 'CLOUD';
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
  protocol?: DeviceProtocol;
  manufacturer?: string | null;
  model?: string | null;
  networkAddress?: string | null;
  capabilitiesJson?: string;
  /** Capacidades efectivas (anunciadas o inferidas del tipo y proveedor) que valida el backend. */
  effectiveCapabilities?: DeviceCapability[];
  /** HomeIA Edge que controla el dispositivo en la LAN. */
  edgeId?: string | null;
  /** Última telemetría normalizada (JSON): temperatureC, humidityPercent, batteryPercent, contactOpen… */
  telemetryJson?: string;
  telemetryAt?: string | null;
  /** Umbral propio de consumo excesivo; sin valor se usa el de la casa. */
  consumptionAlertWatts?: number | null;
  /** Potencia de lo conectado: estima el consumo de interruptores sin medidor mientras están encendidos. */
  ratedWatts?: number | null;
}

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertType =
  | 'EXCESSIVE_CONSUMPTION'
  | 'POSSIBLE_ENERGY_LEAK'
  | 'DEVICE_OFFLINE'
  | 'AUTOMATIC_SHUTDOWN'
  | 'MANUAL'
  | 'EDGE_OFFLINE'
  | 'ZIGBEE_PAIRING'
  | 'SECURITY';

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

/** Resumen de una métrica de rendimiento reportada por los navegadores de la casa (p75). */
export interface PerformanceMetricSummary {
  name: 'LCP' | 'INP' | 'CLS' | 'FCP' | 'TTFB' | 'API_LATENCY';
  count: number;
  p75: number;
  rating: 'good' | 'needs-improvement' | 'poor' | null;
  good: number;
  poor: number;
}

export type DeviceCapability =
  | 'POWER' | 'BRIGHTNESS' | 'COLOR' | 'COLOR_TEMPERATURE' | 'TEMPERATURE' | 'HUMIDITY' | 'MOTION'
  | 'PRESENCE' | 'BATTERY' | 'ENERGY' | 'VOLTAGE' | 'CURRENT' | 'CONTACT' | 'ILLUMINANCE';

export interface DiscoveryCandidate {
  id: string;
  edgeId: string;
  edgeName: string;
  name: string;
  provider: DeviceProvider;
  protocol: DeviceProtocol;
  address: string;
  manufacturer?: string | null;
  model?: string | null;
  service?: string | null;
  capabilities: string[];
  discoveredAt: string;
}

export type EdgeStatus = 'PENDING' | 'ONLINE' | 'OFFLINE' | 'REVOKED';

export interface EdgeNode {
  id: string;
  name: string;
  status: EdgeStatus;
  version?: string | null;
  platform?: string | null;
  zigbeeBridgeState?: string | null;
  zigbeePermitJoin: boolean;
  zigbeePermitJoinUntil?: string | null;
  queueDepth: number;
  metrics: Record<string, unknown>;
  lastHeartbeatAt?: string | null;
  lastHelloAt?: string | null;
  rotatedAt?: string | null;
  previousSecretValidUntil?: string | null;
  revokedAt?: string | null;
  createdAt: string;
}

/** El secreto solo se entrega al enrolar o rotar: no se puede volver a consultar. */
export interface EdgeEnrollment {
  edge: EdgeNode;
  secret: string;
  env: Record<string, string>;
}

export interface IntegrationStatus {
  broker: { state: 'DISABLED' | 'CONNECTED' | 'DISCONNECTED'; tls: boolean; mutualTls: boolean };
  tuya: { configured: boolean };
  zigbee: { edgeId: string; edgeName: string; edgeStatus: EdgeStatus; bridgeState: string; permitJoin: boolean; permitJoinUntil?: string | null }[];
  providers: { provider: DeviceProvider; viaEdge: boolean; devices: number; online: number; candidates: number }[];
  pendingCommands: number;
  checkedAt: string;
}

export type CheckStatus = 'OK' | 'PENDING' | 'WARN' | 'FAIL';

export interface DiagnosticCheck {
  id: string;
  label: string;
  status: CheckStatus;
  detail?: string | null;
  hint?: string | null;
}

export interface Diagnostic {
  integration: string;
  overall: CheckStatus;
  checks: DiagnosticCheck[];
}

export type DeviceEventType =
  | 'DISCOVERED' | 'IMPORTED' | 'UPDATED' | 'CREDENTIALS_CHANGED' | 'ONLINE' | 'OFFLINE'
  | 'COMMAND_SENT' | 'COMMAND_CONFIRMED' | 'COMMAND_FAILED' | 'COMMAND_EXPIRED'
  | 'ZIGBEE_JOINED' | 'ZIGBEE_LEFT' | 'ERROR';

export interface DeviceEvent {
  id: string;
  type: DeviceEventType;
  details?: string | null;
  edgeId?: string | null;
  deviceId?: string | null;
  createdAt: string;
}

export interface DeviceCommandRecord {
  id: string;
  command: DeviceCommand;
  brightness?: number | null;
  status: 'PENDING' | 'SENT' | 'CONFIRMED' | 'FAILED' | 'EXPIRED';
  attempts: number;
  error?: string | null;
  requestedBy: string;
  createdAt: string;
  completedAt?: string | null;
}
