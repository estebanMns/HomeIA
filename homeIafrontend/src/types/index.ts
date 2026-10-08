// Autenticación
export interface LoginRequest {
  email: string;
  password: string;
  ipAddress?: string;
}

export interface LoginResponse {
  success: boolean;
  message: string;
  accessToken?: string;
  refreshToken?: string;
  userId?: string;
  email?: string;
  expiresIn?: number;
  timestamp?: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface RefreshTokenResponse {
  success: boolean;
  message: string;
  accessToken?: string;
  expiresIn?: number;
  timestamp?: string;
}

// Usuario
export interface User {
  id: string;
  email: string;
  name?: string;
  avatar?: string;
}

// Dispositivos
export interface Device {
  id: string;
  name: string;
  type: 'LIGHT' | 'SWITCH' | 'THERMOSTAT' | 'SENSOR' | 'PLUG';
  status: 'ON' | 'OFF' | 'ONLINE' | 'OFFLINE';
  roomId: string;
  roomName: string;
  temperature?: number;
  humidity?: number;
  power?: number;
  lastUpdate?: string;
}

export interface DeviceCommand {
  success: boolean;
  message: string;
  deviceId?: string;
  command?: string;
  timestamp?: string;
}

// Alertas
export interface Alert {
  id: string;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  title: string;
  message: string;
  deviceName?: string;
  deviceId?: string;
  timestamp: string;
  whatsappStatus?: string;
  read?: boolean;
}

// Automatización
export interface Automation {
  id: string;
  name: string;
  enabled: boolean;
  trigger: string;
  action: string;
  description?: string;
  lastRun?: string;
}

// MQTT
export interface MqttStatus {
  connected: boolean;
  message: string;
  timestamp?: string;
}

// WebSocket
export interface WebSocketMessage {
  type: string;
  deviceId?: string;
  status?: string;
  details?: Record<string, any>;
  timestamp?: string;
}

// Métricas
export interface Metrics {
  activeDevices: number;
  activeMqttConnections: number;
  activeWebSocketConnections: number;
  timestamp?: string;
}
